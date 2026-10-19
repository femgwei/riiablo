package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.component.serializer.VitalsSerializer;
import com.riiablo.engine.server.event.DamageAppliedEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.Map;
import com.riiablo.net.packet.d2gs.VitalsP;
import java.util.Random;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Spawn snapshot -> actual swept collision -> settlement/events -> wire HP.
 * Synthetic table rows isolate arithmetic; real quillrat rows are checked below.
 */
class MonsterMissilePhysicalIntegrationTest extends RiiabloTest {
  @ParameterizedTest
  @CsvSource({
      "10,11,128,8,0,0,0,2815", "10,10,128,8,0,0,0,2560",
      "11,10,128,8,0,0,0,2815", "10,11,128,8,75,0,0,4926",
      "10,11,128,8,-100,0,0,282", "1,1,64,8,0,0,0,128",
      "0,0,0,7,0,0,0,128", "10,11,128,8,0,10,50,127",
      "10,11,128,8,0,10,-50,382", "10,11,128,8,0,11,0,0,0",
      "0,0,128,8,0,0,0,0", "0,11,128,8,0,0,0,0"
  })
  void fractionalPacketReachesActualLifeEventsAndWire(int min, int max, int sourceScale,
      int hitShift, int percent, int flat, int resistance, int expectedFixed) {
    try (Fixture f = new Fixture()) {
      f.source.base().put(Stat.mindamage, min);
      f.source.base().put(Stat.maxdamage, max);
      f.source.base().put(Stat.damagepercent, percent);
      f.source.reset();
      f.target.attrs.base().put(Stat.normal_damage_reduction, flat);
      f.target.attrs.base().put(Stat.damageresist, resistance);
      f.target.attrs.reset();
      Missiles.Entry row = row();
      row.SrcDamage = sourceScale;
      row.HitShift = hitShift;
      if (sourceScale == 0) row.MinDamage = row.MaxDamage = 1;
      Missile projectile = f.spawn(row);
      assertNotNull(projectile.nativePhysical);
      f.land();
      float expected = expectedFixed / 256f;
      assertEquals(10000 * 256 - expectedFixed, f.target.attrs.get(Stat.hitpoints).encodedValues());
      if (expectedFixed > 0) {
        assertEquals(expected, f.probe.requested);
        assertEquals(expected, f.probe.physical);
        assertNotNull(f.probe.applied);
        assertEquals(expected, f.probe.applied.physicalDamage);
        assertEquals(expected, f.probe.applied.lifeLost);
      } else {
        assertEquals(0, f.probe.events);
      }
      FlatBufferBuilder buffer = new FlatBufferBuilder(64);
      buffer.finish(new VitalsSerializer().putData(buffer, f.target));
      VitalsP wire = VitalsP.getRootAsVitalsP(buffer.dataBuffer());
      AttributesWrapper client = new AttributesWrapper();
      client.attrs = Attributes.obtainLarge();
      VitalsSerializer.apply(client, wire);
      assertEquals(f.target.attrs.get(Stat.hitpoints).encodedValues(),
          client.attrs.get(Stat.hitpoints).encodedValues());
    }
  }

  @Test void inFlightSourceAndStateChangesDoNotChangePhysicalSnapshot() {
    try (Fixture f = new Fixture()) {
      f.source.base().put(Stat.damagepercent, 25);
      f.source.base().put(Stat.item_mindamage_percent, 10);
      f.source.base().put(Stat.item_maxdamage_percent, 20);
      f.source.reset();
      UnitStates states = f.world.getMapper(UnitStates.class).create(f.owner).init(f.owner);
      states.stateList.addState(StateId.MIGHT, 100).damageModifier = 30;
      Missile projectile = f.spawn(row());
      assertEquals(75, projectile.nativePhysical.damagePercent);
      f.source.base().put(Stat.mindamage, 999);
      f.source.base().put(Stat.maxdamage, 999);
      f.source.base().put(Stat.damagepercent, 999);
      f.source.reset();
      states.stateList.clearAll();
      states.stateList.addState(StateId.MIGHT, 100).damageModifier = 900;
      f.land();
      assertEquals(4926 / 256f, f.probe.applied.lifeLost);
    }
  }

  @Test void attackTwoProfileReplacesFixedEndpointsWithoutLosingCapturedBonus() {
    try (Fixture f = new Fixture()) {
      f.source.base().put(Stat.damagepercent, 50);
      f.source.reset();
      Missile projectile = f.spawn(row());
      MissileDamageResolver.applySourceAttackProfile(projectile, 1, 1, 321);
      assertEquals(256, projectile.nativePhysical.minimum);
      assertEquals(256, projectile.nativePhysical.maximum);
      assertEquals(50, projectile.nativePhysical.damagePercent);
      assertEquals(321, projectile.attackRating);
      f.land();
      assertEquals(1.5f, f.probe.applied.lifeLost);
    }
  }

  @Test void missedRatingCheckDoesNotApplyPhysicalDamage() {
    try (Fixture f = new Fixture()) {
      Missiles.Entry row = row();
      row.ToHit = true;
      f.spawn(row);
      MathUtils.random = new Random(0) {
        @Override public int nextInt(int bound) { return bound - 1; }
      };
      f.land();
      assertEquals(10000f, f.target.attrs.get(Stat.hitpoints).asFixed());
      assertEquals(0, f.probe.events);
      assertNull(f.probe.applied);
    }
  }

  @Test void shieldBlocksSubHpPhysicalEvenWhenLegacySummaryIsZero() {
    try (Fixture f = new Fixture()) {
      f.source.base().put(Stat.mindamage, 1);
      f.source.base().put(Stat.maxdamage, 1);
      f.source.reset();
      f.target.attrs.base().put(Stat.toblock, 75);
      f.target.attrs.base().put(Stat.dexterity, 1000);
      f.target.attrs.reset();
      Missiles.Entry row = row();
      row.SrcDamage = 64;
      f.spawn(row);
      f.land();
      assertEquals(10000f, f.target.attrs.get(Stat.hitpoints).asFixed());
      assertEquals(0, f.probe.events);
    }
  }

  @Test void realQuillRatRowsCaptureWholeSourceBeforeSourceScaleDivision() {
    try (Fixture f = new Fixture()) {
      String[] names = {"quillrat1", "quillrat2", "quillrat5"};
      for (String name : names) {
        com.riiablo.codec.excel.MonStats.Entry monster = Riiablo.files.monstats.get(name);
        Missiles.Entry row = Riiablo.files.Missiles.get(monster.MissA2);
        assertNotNull(row, name);
        Missile projectile = new Missile().set(row, new Vector2(), row.Range);
        assertTrue(MissileDamageResolver.initialize(Riiablo.files, projectile, f.source,
            f.monster, 0, 1, 0));
        assertNotNull(projectile.nativePhysical, row.Missile);
        // Level 1 table + 10..11 HP owner, derived directly from D2Common source.
        assertEquals((row.MinDamage << row.HitShift) + 10 * 256 * row.SrcDamage / 128,
            projectile.nativePhysical.minimum);
        assertEquals((row.MaxDamage << row.HitShift) + 11 * 256 * row.SrcDamage / 128,
            projectile.nativePhysical.maximum);
      }
    }
  }

  @Test void skillAndFormulaReinitializationDoNotReuseStaleNativePacket() {
    try (Fixture f = new Fixture()) {
      Missile projectile = f.spawn(row());
      Missiles.Entry formula = row();
      formula.DmgSymPerCalc = "ln12";
      projectile.missile = formula;
      projectile.damageSnapshot = false;
      assertTrue(MissileDamageResolver.initialize(Riiablo.files, projectile, f.source, f.monster, 0, 1, 0));
      assertNull(projectile.nativePhysical);
      projectile.missile = row();
      projectile.damageSnapshot = false;
      assertTrue(MissileDamageResolver.initialize(Riiablo.files, projectile, f.source, f.monster, 0, 1, 0));
      assertNotNull(projectile.nativePhysical);
      MissileDamageResolver.initializeSkill(projectile, Riiablo.files.skills.get("Fire Bolt"), f.source, 1);
      assertNull(projectile.nativePhysical);
    }
  }

  @Test void playerAndExplicitHirelingOptOutRetainLegacyCreationPolicy() {
    try (Fixture f = new Fixture()) {
      Missile projectile = new Missile().set(row(), new Vector2(), 100);
      assertTrue(MissileDamageResolver.initialize(Riiablo.files, projectile, f.source, null, 0, 1, 0));
      assertNull(projectile.nativePhysical);
      projectile = new Missile().set(row(), new Vector2(), 100);
      assertTrue(MissileDamageResolver.initialize(Riiablo.files, projectile, f.source,
          f.monster, 0, 1, 0, null, false));
      assertNull(projectile.nativePhysical);
      Missiles.Entry empty = row();
      empty.SrcDamage = 0;
      projectile = new Missile().set(empty, new Vector2(), 100);
      assertFalse(MissileDamageResolver.initialize(Riiablo.files, projectile, f.source,
          f.monster, 0, 1, 0, null, false));
      assertFalse(projectile.damageSnapshot);
    }
  }

  private static Missiles.Entry row() {
    Missiles.Entry row = new Missiles.Entry();
    row.Missile = "physical-audit-fixture";
    row.HitShift = 8;
    row.SrcDamage = 128;
    row.Range = 100;
    row.Collision = true;
    row.CollideType = 3;
    row.CollideKill = true;
    return row;
  }

  static final class Probe extends PassiveSystem {
    int events;
    float requested, physical;
    DamageAppliedEvent applied;
    DamageEvent request;
    @Subscribe public void request(DamageEvent event) {
      request = event;
      events++; requested = event.damage; physical = event.physicalDamage;
    }
    @Subscribe public void applied(DamageAppliedEvent event) { applied = event; }
  }

  static final class Fixture implements AutoCloseable {
    final Random previousRandom = MathUtils.random;
    final Probe probe = new Probe();
    final World world;
    final int owner, victim;
    final Attributes source = attributes();
    final AttributesWrapper target;
    final Monster monster;
    Fixture() { this(false); }
    Fixture(boolean withStates) {
      WorldConfigurationBuilder builder = new WorldConfigurationBuilder().with(new EventSystem(), probe);
      com.riiablo.engine.EntityFactory factory = mock(com.riiablo.engine.EntityFactory.class);
      if (withStates) builder.with(factory, new StateUpdater());
      world = new World(builder.with(new MissileCollisionSystem()).build()
          .register("map", mock(Map.class)).register("factory", factory));
      owner = world.create(); victim = world.create();
      monster = world.getMapper(Monster.class).create(owner).set(Riiablo.files.monstats.get("quillrat1"),
          Riiablo.files.monstats2.get(Riiablo.files.monstats.get("quillrat1").MonStatsEx));
      source.base().put(Stat.mindamage, 10);
      source.base().put(Stat.maxdamage, 11);
      source.reset();
      world.getMapper(AttributesWrapper.class).create(owner).attrs = source;
      world.getMapper(Position.class).create(owner).position.set(-10, 0);
      world.getMapper(Player.class).create(victim);
      world.getMapper(Position.class).create(victim).position.set(0, 0);
      target = world.getMapper(AttributesWrapper.class).create(victim);
      target.attrs = attributes();
      MathUtils.random = new Random(0) {
        @Override public int nextInt(int bound) { return bound == 100 ? 0 : bound - 1; }
      };
    }
    Missile spawn(Missiles.Entry row) {
      int id = world.create();
      Missile projectile = world.getMapper(Missile.class).create(id).set(row, new Vector2(), row.Range).setOwner(owner);
      world.getMapper(Position.class).create(id).position.set(0, 0);
      world.getMapper(Velocity.class).create(id).velocity.set(0.1f, 0);
      UnitStates states = world.getMapper(UnitStates.class).get(owner);
      assertTrue(MissileDamageResolver.initialize(Riiablo.files, projectile, source, monster, 0, 1, 0,
          states != null ? states.stateList : null));
      return projectile;
    }
    void land() { world.setDelta(com.riiablo.codec.Animation.FRAME_DURATION); world.process(); }
    @Override public void close() { world.dispose(); MathUtils.random = previousRandom; }
  }

  private static Attributes attributes() {
    Attributes a = Attributes.obtainStandard();
    a.base().put(Stat.level, 20);
    a.base().put(Stat.hitpoints, 10000f);
    a.base().put(Stat.maxhp, 10000f);
    a.reset();
    return a;
  }
}
