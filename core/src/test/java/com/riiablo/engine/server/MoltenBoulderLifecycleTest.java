package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.missile.NativeMoltenBoulder;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.combat.NativeCombatRolls;
import com.riiablo.engine.server.skill.SkillId;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class MoltenBoulderLifecycleTest extends RiiabloTest {
  @Test void nativeSizePolicySeparatesRollingDamageLargeBurstAndKnockback() {
    Missiles.Entry row = Riiablo.files.Missiles.get("moltenboulder");
    assertEquals(6, row.pSrvDoFunc);
    assertEquals(47, row.pSrvHitFunc);
    assertEquals(14, row.pSrvDmgFunc);
    MonStats2.Entry small = new MonStats2.Entry(); small.small = true;
    MonStats2.Entry medium = new MonStats2.Entry();
    MonStats2.Entry large = new MonStats2.Entry(); large.large = true;
    assertEquals(100, NativeMoltenBoulder.knockbackChance(row, false, small));
    assertEquals(50, NativeMoltenBoulder.knockbackChance(row, false, medium));
    assertEquals(0, NativeMoltenBoulder.knockbackChance(row, false, large));
    assertFalse(NativeMoltenBoulder.burstsOnTarget(false, small));
    assertTrue(NativeMoltenBoulder.burstsOnTarget(false, large));
    assertEquals(18, NativeMoltenBoulder.FIRE_X.length);
  }

  @Test void emergeCreatesSingleAuthoritativeRollThenCellCrossingsCreateRateBasedFire() {
    EntityFactory factory = mock(EntityFactory.class);
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem(), factory).build()
        .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      world.setDelta(1f / 25f);
      int owner = world.create();
      world.getMapper(Player.class).create(owner);
      world.getMapper(Position.class).create(owner);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().clear();
      attrs.base().put(Stat.hitpoints, 100f);
      attrs.base().put(Stat.maxhp, 100f);
      attrs.base().put(Stat.level, 20);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attrs;
      ArrayList<Integer> created = new ArrayList<>();
      when(factory.createMissile(any(Missiles.Entry.class), any(Vector2.class),
          any(Vector2.class), eq(owner), anyInt())).thenAnswer(call -> {
            int id = create(world, call.getArgument(0), call.getArgument(2),
                call.getArgument(1), owner);
            created.add(id);
            return id;
          });
      int emergeId = create(world, Riiablo.files.Missiles.get("moltenboulderemerge"),
          new Vector2(10, 10), Vector2.X, owner);
      for (int i = 0; i < 5; i++) world.process();
      assertEquals(1, created.size());
      Missile rolling = world.getMapper(Missile.class).get(created.get(0));
      assertEquals("moltenboulder", rolling.missile.Missile);
      assertEquals(100, rolling.nativeLifetimeFrames);
      assertEquals(SkillId.MOLTEN_BOULDER, rolling.skillId);
      assertTrue(rolling.authoritative);
      assertTrue(rolling.damageSnapshot);
      for (int i = 0; i < 15; i++) world.process();
      // Artemis may recycle the deleted emerge id for a fire-path child.
      assertTrue(!world.getEntityManager().isActive(emergeId)
          || !"moltenboulderemerge".equals(world.getMapper(Missile.class)
              .get(emergeId).missile.Missile));
      int fireCount = 0;
      for (int id : created) {
        Missile fire = world.getMapper(Missile.class).get(id);
        if (!"moltenboulderfirepath".equals(fire.missile.Missile)) continue;
        fireCount++;
        assertTrue(fire.persistent);
        assertTrue(fire.fixedElementalRate);
        assertEquals(40, fire.elementalMinRateFixed);
        assertEquals(56, fire.elementalMaxRateFixed);
        assertEquals(41, fire.elementalDamageRate);
      }
      assertTrue(fireCount > 0);
      assertTrue(fireCount < 15, "trails are emitted on cell crossings, not every callback");
    } finally { world.dispose(); }
  }

  @Test void largeCollisionCreatesExactlyOneAreaPacketAndEighteenFiniteFireChildren() {
    EntityFactory factory = mock(EntityFactory.class);
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem(), factory).build()
        .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      world.setDelta(1f / 25f);
      int owner = world.create();
      world.getMapper(Player.class).create(owner);
      world.getMapper(Position.class).create(owner).position.set(8, 10);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(1000);
      ArrayList<Integer> children = new ArrayList<>();
      when(factory.createMissile(any(Missiles.Entry.class), any(Vector2.class),
          any(Vector2.class), eq(owner), anyInt())).thenAnswer(call -> {
            int id = create(world, call.getArgument(0), call.getArgument(2),
                call.getArgument(1), owner);
            children.add(id);
            return id;
          });
      int large = monster(world, 10.3f, 10, true);
      int secondary = monster(world, 12, 10, false);
      int fireImmune = monster(world, 12, 12, false);
      Attributes immuneAttrs = world.getMapper(AttributesWrapper.class).get(fireImmune).attrs;
      immuneAttrs.base().put(Stat.fireresist, 100);
      immuneAttrs.reset();
      int missileId = create(world, Riiablo.files.Missiles.get("moltenboulder"),
          new Vector2(10, 10), Vector2.X, owner);
      Missile rolling = world.getMapper(Missile.class).get(missileId);
      MissileDamageResolver.initializeSkill(Riiablo.files, rolling,
          Riiablo.files.skills.get(SkillId.MOLTEN_BOULDER),
          world.getMapper(AttributesWrapper.class).get(owner).attrs, 1, n -> 0);
      rolling.rngState = 12345;
      NativeUnitRandom rng = NativeUnitRandom.missile(rolling, 0, missileId);
      NativeUnitRandom.Snapshot seed = rng.snapshot();
      NativeCombatRolls expected = new NativeCombatRolls(rng);
      // Native level-one Molten Boulder has independent 6..12 HP physical
      // and fire ranges, drawn in 8.8 with an exclusive upper endpoint.
      int physical = expected.physicalFixed(6 * 256, 12 * 256);
      int rawFire = expected.physicalFixed(6 * 256, 12 * 256);
      rng.restore(seed);
      world.process();
      assertEquals(18, children.size(), "a large collision must fan out once without recursion");
      for (int target : new int[] {large, secondary}) {
        float life = world.getMapper(AttributesWrapper.class).get(target).attrs.get(Stat.hitpoints).asFixed();
        assertTrue(life >= 976f && life <= 988f,
            "each target gets exactly one 12..24 physical+fire area packet, hp=" + life);
        assertEquals(1000f - (physical + rawFire) / 256f, life,
            "all targets share one raw packet, not one random roll per target");
      }
      assertEquals(1000f - physical / 256f, immuneAttrs.get(Stat.hitpoints).asFixed(),
          "fire immunity settles independently after copying the shared packet");
      for (int id : children) {
        Missile fire = world.getMapper(Missile.class).get(id);
        assertEquals("moltenboulderfirepath", fire.missile.Missile);
        assertEquals(7, fire.remainingFrames, "SrvHit47 passes explosion radius as child range");
      }
      for (int i = 0; i < 8; i++) world.process();
      for (int id : children) assertFalse(world.getEntityManager().isActive(id));
    } finally { world.dispose(); }
  }

  private static int monster(World world, float x, float y, boolean large) {
    int id = world.create();
    MonStats.Entry stats = new MonStats.Entry();
    MonStats2.Entry stats2 = new MonStats2.Entry();
    stats2.large = large;
    world.getMapper(Monster.class).create(id).set(stats, stats2);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1000);
    return id;
  }

  private static Attributes attributes(float life) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 20);
    attrs.base().put(Stat.hitpoints, life);
    attrs.base().put(Stat.maxhp, life);
    attrs.reset();
    return attrs;
  }

  private static int create(World world, Missiles.Entry row, Vector2 origin,
      Vector2 facing, int owner) {
    int id = world.create();
    Missile missile = world.getMapper(Missile.class).create(id)
        .set(row, origin, row.Range).setOwner(owner);
    missile.skillId = SkillId.MOLTEN_BOULDER;
    missile.damageLevel = 1;
    world.getMapper(Position.class).create(id).position.set(origin);
    world.getMapper(Velocity.class).create(id).velocity.set(facing).nor().scl(row.Vel);
    return id;
  }
}
