package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real factory and collision systems: no combat resolver or RNG replacement. */
class MonsterMissileProductionRandomIntegrationTest extends RiiabloTest {
  @ParameterizedTest
  @ValueSource(ints = {1, 1234, -98765})
  void factoryImpactReplaysAndIgnoresUnrelatedUnitDraws(int seed) {
    int[] baseline = run(seed, false);
    assertArrayEquals(baseline, run(seed, false), "same seed and entity order must replay");
    assertArrayEquals(baseline, run(seed, true), "unrelated unit draws must stay isolated");
  }

  private int[] run(int seed, boolean noise) {
    ServerEntityFactory factory = new ServerEntityFactory();
    CofManager cofs = new CofManager();
    MonsterMissilePhysicalIntegrationTest.Probe probe = new MonsterMissilePhysicalIntegrationTest.Probe();
    SeedProbe seedProbe = new SeedProbe();
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), probe, seedProbe,
        new ItemManager(), new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(),
        cofs, factory, new StateUpdater(), new MissileCollisionSystem()).build()
        .register("map", mock(Map.class)).register("factory", factory)
        .register("serverContext", new ServerGameplayContext(Riiablo.files, seed))
        .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()));
    try {
      int owner = world.create(), victim = world.create(), unrelated = world.create();
      Monster monster = world.getMapper(Monster.class).create(owner).set(new MonStats.Entry(), null);
      world.getMapper(Monster.class).create(unrelated).set(new MonStats.Entry(), null);
      world.getMapper(CofReference.class).create(owner).mode = Engine.Monster.MODE_NU;
      Attributes source = attributes();
      source.base().put(Stat.mindamage, 10); source.base().put(Stat.maxdamage, 11);
      source.base().put(Stat.firemindam, 2); source.base().put(Stat.firemaxdam, 3);
      source.base().put(Stat.poisonmindam, 256); source.base().put(Stat.poisonmaxdam, 512);
      source.base().put(Stat.poisonlength, 4); source.base().put(Stat.poison_count, 1);
      source.reset();
      world.getMapper(AttributesWrapper.class).create(owner).attrs = source;
      world.getMapper(Position.class).create(owner).position.set(-10, 0);
      cofs.setMode(owner, Engine.Monster.MODE_A1, true);
      world.getMapper(Player.class).create(victim);
      world.getMapper(Position.class).create(victim).position.set(0, 0);
      Attributes target = attributes();
      world.getMapper(AttributesWrapper.class).create(victim).attrs = target;
      UnitStates states = world.getMapper(UnitStates.class).create(victim).init(victim);
      if (noise) drawNoise(world, seed, unrelated);
      int id = factory.createMissile(Riiablo.files.Missiles.get("arrow"),
          new Vector2(1, 0), new Vector2(), owner, 1);
      Missile missile = world.getMapper(Missile.class).get(id);
      assertTrue(missile.nativePhysical.criticalCaptured);
      assertFalse(missile.nativePhysical.creationCritical);
      assertNotNull(missile.nativeElemental);
      assertEquals(NativeRng.forUnit(seed, id).state(), missile.rngState);
      NativeSeedReference expected = new NativeSeedReference(missile.rngState);
      // Arrow source scaling is 128; native fill order is physical, fire, ... poison.
      assertEquals(2560, missile.nativePhysical.minimum);
      assertEquals(2816, missile.nativePhysical.maximum);
      assertEquals(512, missile.nativeElemental.minimum(1));
      assertEquals(768, missile.nativeElemental.maximum(1));
      assertEquals(256, missile.nativeElemental.minimum(4));
      assertEquals(512, missile.nativeElemental.maximum(4));
      int physical = 2560 + expected.nextInt(256);
      int fire = 512 + expected.nextInt(256);
      int poison = 256 + expected.nextInt(256);
      NativeUnitRandom ownerRandom = NativeUnitRandom.monster(monster, seed, owner);
      NativeUnitRandom.Snapshot ownerBefore = ownerRandom.snapshot();
      NativeSeedReference expectedOwner = new NativeSeedReference(ownerBefore.lowSeed, ownerBefore.highSeed);
      // The real arrow row requests AR; this draw belongs to its owner, not the missile.
      if (missile.missile.ToHit || missile.usesAttackRating) expectedOwner.nextInt(100);
      if (noise) drawNoise(world, seed, unrelated);
      world.setDelta(com.riiablo.codec.Animation.FRAME_DURATION);
      world.process();
      assertNotNull(probe.applied, "factory arrow must hit the player");
      assertEquals(physical / 256f, probe.applied.physicalDamage);
      assertEquals(fire / 256f, probe.request.fireDamage);
      assertEquals(10000 * 256 - physical - fire, target.get(Stat.hitpoints).encodedValues());
      assertEquals(expected.state(), probe.impactSeeds.get(id).intValue());
      assertEquals(expected.high(), seedProbe.high);
      assertEquals(poison / 256f, states.stateList.getState(StateId.POISON).exactDamagePerFrame);
      assertEquals(4, states.stateList.getState(StateId.POISON).duration);
      // No MonStats Crit or creation critical: only the table's AR gate draws here.
      assertEquals(expectedOwner.state(), ownerRandom.state());
      assertEquals(expectedOwner.high(), ownerRandom.high());
      for (int tick = 1; tick <= 4; tick++) {
        if (noise) drawNoise(world, seed, unrelated);
        world.process();
        assertEquals(10000 * 256 - physical - fire - poison * tick,
            target.get(Stat.hitpoints).encodedValues());
      }
      assertNull(states.stateList.getState(StateId.POISON));
      return new int[] {physical, fire, poison, seedProbe.low, seedProbe.high,
          ownerRandom.state(), ownerRandom.high(), target.get(Stat.hitpoints).encodedValues()};
    } finally {
      world.dispose();
    }
  }

  static final class SeedProbe extends net.mostlyoriginal.api.system.core.PassiveSystem {
    int low, high;
    @net.mostlyoriginal.api.event.common.Subscribe
    public void damage(com.riiablo.engine.server.event.DamageEvent event) {
      if (event.missileId < 0) return;
      Missile missile = world.getMapper(Missile.class).get(event.missileId);
      assertNotNull(missile.nativeSeed);
      low = missile.nativeSeed.getNLowSeed();
      high = missile.nativeSeed.getNHighSeed();
    }
  }

  private static void drawNoise(World world, int seed, int unrelated) {
    NativeUnitRandom random = NativeUnitRandom.unit(world, seed, unrelated);
    for (int i = 0; i < 37; i++) random.nextInt(1000);
  }

  private static Attributes attributes() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, 20);
    attrs.base().put(Stat.hitpoints, 10000f); attrs.base().put(Stat.maxhp, 10000f);
    attrs.reset();
    return attrs;
  }
}
