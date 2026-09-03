package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.AssassinSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native SrvDo034/SrvDo035 regression coverage. */
class AssassinMartialArtsTest extends RiiabloTest {
  @Test
  void fistsOfFireExposesNativeProgressiveColumns() {
    Skills.Entry fists = Riiablo.files.skills.get("Fists of Fire");
    assertNotNull(fists);
    assertTrue(fists.prgstack);
    assertNotNull(fists.srvprgfunc);
    assertEquals(3, fists.srvprgfunc.length);
    assertEquals(143, fists.srvprgfunc[0]);
    assertEquals(38, fists.srvprgfunc[1]);
    assertEquals(39, fists.srvprgfunc[2]);
    assertNotNull(fists.prgcalc);
    assertEquals(3, fists.prgcalc.length);
    assertEquals(4, AssassinSkills.progressiveRange(fists, 5, 2));
    assertEquals(4, AssassinSkills.progressiveRange(fists, 5, 3));
    assertEquals(128, fists.SrcDam);
    assertNotNull(AssassinSkills.progressiveMissile(fists, 3));
    Missiles.Entry field = Riiablo.files.Missiles.get(
        AssassinSkills.progressiveMissile(fists, 3));
    assertNotNull(field);
    assertEquals("fistsoffirefirewall", field.Missile);
    assertEquals(0, field.Vel);
    assertTrue(field.Range > 0);
  }

  @Test
  void progressiveStateUsesAuraStateCapsAtThreeAndDoesNotChangeMovementSpeed() {
    Skills.Entry tiger = new Skills.Entry();
    tiger.Id = 254;
    tiger.skill = "Tiger Strike";
    tiger.aurastate = "progressive_damage";
    tiger.auralencalc = "100+lvl";
    StateList states = new StateList(7);

    UnitState charge = null;
    for (int i = 0; i < 5; i++) {
      charge = AssassinSkills.addProgressiveCharge(states, tiger, 4, 7);
      assertNotNull(charge);
    }

    assertEquals(StateId.PROGRESSIVE_DAMAGE, charge.stateId);
    assertEquals(3, AssassinSkills.progressiveCharges(charge));
    assertEquals(104, charge.duration);
    assertEquals(254, charge.skillId);
    assertEquals(0, states.getTotalVelocityModifier(),
        "the StateP scalar carrying charge count must not affect movement");
  }

  @Test
  void tigerAndCobraUseNativeChargeFormulas() {
    Skills.Entry tiger = new Skills.Entry();
    tiger.calc1 = "par1+(lvl-1)*par2";
    tiger.Param = new int[] {100, 20, 0, 0, 0, 0, 0, 0};
    assertEquals(280, AssassinSkills.calculateTigerStrikeDamageBonus(tiger, 3, 2));

    Skills.Entry cobra = new Skills.Entry();
    cobra.Param = new int[] {40, 5, 0, 0, 0, 0, 0, 0};
    assertEquals(50, AssassinSkills.calculateCobraStrikeSteal(cobra, 3, 1)[0]);
    assertEquals(0, AssassinSkills.calculateCobraStrikeSteal(cobra, 3, 1)[1]);
    assertEquals(50, AssassinSkills.calculateCobraStrikeSteal(cobra, 3, 2)[1]);
    assertEquals(100, AssassinSkills.calculateCobraStrikeSteal(cobra, 3, 3)[0]);
    assertEquals(100, AssassinSkills.calculateCobraStrikeSteal(cobra, 3, 3)[1]);
  }

  @Test
  void actioneerAddsChargesOnlyFromSuccessfulMeleeRecords() {
    DummyFactory factory = new DummyFactory();
    Actioneer actioneer = new Actioneer();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int assassin = createPlayer(world, 0, 0, attributes(10000, 1, 1, 100000));
      int target = createMonster(world, 1, 0, attributes(10000, 0, 0, 0));
      Skills.Entry tiger = Riiablo.files.skills.get("Tiger Strike");
      Skills.Entry cobra = Riiablo.files.skills.get("Cobra Strike");
      Skills.Entry fists = Riiablo.files.skills.get("Fists of Fire");
      assertNotNull(tiger);
      assertNotNull(cobra);
      assertNotNull(fists);
      assertEquals(34, tiger.srvdofunc);
      assertEquals(34, cobra.srvdofunc);
      assertEquals(35, fists.srvdofunc);

      MathUtils.random.setSeed(0xA551551L);
      buildToThreeCharges(world, assassin, target, tiger);
      buildToThreeCharges(world, assassin, target, cobra);
      buildToThreeCharges(world, assassin, target, fists);

      StateList states = world.getMapper(UnitStates.class).get(assassin).stateList;
      assertEquals(3, AssassinSkills.progressiveCharges(states, StateId.PROGRESSIVE_DAMAGE));
      assertEquals(3, AssassinSkills.progressiveCharges(states, StateId.PROGRESSIVE_STEAL));
      assertEquals(3, AssassinSkills.progressiveCharges(states, StateId.PROGRESSIVE_FIRE));

      int distant = createMonster(world, 50, 50, attributes(10000, 0, 0, 0));
      states.removeState(StateId.PROGRESSIVE_DAMAGE);
      world.getMapper(Casting.class).get(assassin)
          .set(tiger.Id, distant, world.getMapper(Position.class).get(distant).position);
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(assassin, Engine.KEYFRAME_ATK));
      assertNull(states.getState(StateId.PROGRESSIVE_DAMAGE));
    } finally {
      world.dispose();
    }
  }

  @Test
  void serverSkillSystemDoesNotSpawnReleaseMissilesWhileBuildingCharge() {
    DummyFactory factory = new DummyFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int assassin = createPlayer(world, 0, 0, attributes(100, 1, 1, 100));
      Skills.Entry fists = Riiablo.files.skills.get("Fists of Fire");
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          assassin, fists.Id, Engine.INVALID_ENTITY, new Vector2(1, 0),
          fists.srvdofunc, fists.cltdofunc));
      assertEquals(0, factory.missilesCreated);
    } finally {
      world.dispose();
    }
  }

  @Test
  void tigerCobraAndFistsResolveNativeReleaseData() {
    Skills.Entry tiger = Riiablo.files.skills.get("Tiger Strike");
    Skills.Entry cobra = Riiablo.files.skills.get("Cobra Strike");
    Skills.Entry fists = Riiablo.files.skills.get("Fists of Fire");
    StateList states = new StateList(12);
    for (int i = 0; i < 3; i++) {
      AssassinSkills.addProgressiveCharge(states, tiger, 5, 12);
      AssassinSkills.addProgressiveCharge(states, cobra, 5, 12);
      AssassinSkills.addProgressiveCharge(states, fists, 5, 12);
    }

    AssassinSkills.ProgressiveRelease release = AssassinSkills.resolveProgressiveRelease(
        states, id -> Riiablo.files.skills.get(id), id -> 5);
    assertEquals(9, release.totalCharges);
    assertTrue(release.tigerDamagePercent > 0);
    assertTrue(release.lifeLeechPercent > 0);
    assertTrue(release.manaLeechPercent > 0);
    assertTrue(release.fireMaxDamage >= release.fireMinDamage);
    assertTrue(release.fireMaxDamage > 0);
    assertTrue(release.fireConversionPercent >= 0 && release.fireConversionPercent <= 100);
  }

  @Test
  void successfulDragonClawReleasesAndConsumesTigerCobraCharges() {
    DummyFactory factory = new DummyFactory();
    Actioneer actioneer = new Actioneer();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      Attributes assassinAttrs = attributes(100, 10, 10, 100000);
      assassinAttrs.base().get(Stat.hitpoints).set(20f);
      assassinAttrs.base().get(Stat.mana).set(0f);
      assassinAttrs.reset();
      int assassin = createPlayer(world, 0, 0, assassinAttrs);
      int target = createMonster(world, 1, 0, attributes(10000, 0, 0, 0));
      StateList states = world.getMapper(UnitStates.class).get(assassin).stateList;
      Skills.Entry tiger = Riiablo.files.skills.get("Tiger Strike");
      Skills.Entry cobra = Riiablo.files.skills.get("Cobra Strike");
      Skills.Entry finisher = Riiablo.files.skills.get("Dragon Claw");
      assertNotNull(finisher);
      assertEquals(46, finisher.srvdofunc);
      for (int i = 0; i < 3; i++) {
        AssassinSkills.addProgressiveCharge(states, tiger, 5, assassin);
        AssassinSkills.addProgressiveCharge(states, cobra, 5, assassin);
      }
      world.getMapper(Casting.class).get(assassin)
          .set(finisher.Id, target, world.getMapper(Position.class).get(target).position);

      MathUtils.random.setSeed(0xF1A15EEL);
      for (int i = 0; i < 20 && states.hasState(StateId.PROGRESSIVE_DAMAGE); i++) {
        world.getSystem(EventSystem.class).dispatch(
            AnimDataKeyframeEvent.obtain(assassin, Engine.KEYFRAME_ATK));
      }

      assertNull(states.getState(StateId.PROGRESSIVE_DAMAGE));
      assertNull(states.getState(StateId.PROGRESSIVE_STEAL));
      assertTrue(assassinAttrs.get(Stat.hitpoints).asFixed() > 20f);
      assertTrue(assassinAttrs.get(Stat.mana).asFixed() > 0f);
      assertTrue(world.getMapper(AttributesWrapper.class).get(target).attrs
          .get(Stat.hitpoints).asFixed() < 10000f);
    } finally {
      world.dispose();
    }
  }

  @Test
  void failedFinisherDoesNotConsumeCharges() {
    DummyFactory factory = new DummyFactory();
    Actioneer actioneer = new Actioneer();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int assassin = createPlayer(world, 0, 0, attributes(100, 10, 10, 100000));
      int target = createMonster(world, 30, 30, attributes(100, 0, 0, 0));
      StateList states = world.getMapper(UnitStates.class).get(assassin).stateList;
      Skills.Entry tiger = Riiablo.files.skills.get("Tiger Strike");
      Skills.Entry finisher = Riiablo.files.skills.get("Dragon Claw");
      AssassinSkills.addProgressiveCharge(states, tiger, 3, assassin);
      world.getMapper(Casting.class).get(assassin)
          .set(finisher.Id, target, world.getMapper(Position.class).get(target).position);

      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(assassin, Engine.KEYFRAME_ATK));

      assertEquals(1, AssassinSkills.progressiveCharges(
          states, StateId.PROGRESSIVE_DAMAGE));
    } finally {
      world.dispose();
    }
  }

  @Test
  void fistsOfFireReleaseDamagesPhysicalImmuneTargetWithSkillFire() {
    DummyFactory factory = new DummyFactory();
    Actioneer actioneer = new Actioneer();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int assassin = createPlayer(world, 0, 0, attributes(100, 10, 10, 100000));
      Attributes targetAttrs = attributes(1000, 0, 0, 0);
      targetAttrs.base().put(Stat.damageresist, 100);
      targetAttrs.base().put(Stat.fireresist, 0);
      targetAttrs.reset();
      int target = createMonster(world, 1, 0, targetAttrs);
      StateList states = world.getMapper(UnitStates.class).get(assassin).stateList;
      Skills.Entry fists = Riiablo.files.skills.get("Fists of Fire");
      Skills.Entry finisher = Riiablo.files.skills.get("Dragon Claw");
      for (int i = 0; i < 3; i++) {
        AssassinSkills.addProgressiveCharge(states, fists, 5, assassin);
      }
      world.getMapper(Casting.class).get(assassin)
          .set(finisher.Id, target, world.getMapper(Position.class).get(target).position);

      MathUtils.random.setSeed(0xF1575F1EL);
      for (int i = 0; i < 20 && states.hasState(StateId.PROGRESSIVE_FIRE); i++) {
        world.getSystem(EventSystem.class).dispatch(
            AnimDataKeyframeEvent.obtain(assassin, Engine.KEYFRAME_ATK));
      }

      assertNull(states.getState(StateId.PROGRESSIVE_FIRE));
      assertTrue(targetAttrs.get(Stat.hitpoints).asFixed() < 1000f,
          "Fists skill fire must survive a target's physical immunity");
    } finally {
      world.dispose();
    }
  }

  @Test
  void fistsOfFireSecondChargeDamagesNearbyEnemiesOnly() {
    DummyFactory factory = new DummyFactory();
    Actioneer actioneer = new Actioneer();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      Attributes assassinAttrs = attributes(1000, 10, 10, 100000);
      int assassin = createPlayer(world, 0, 0, assassinAttrs);
      Attributes primaryAttrs = attributes(10000, 0, 0, 0);
      Attributes nearbyAttrs = attributes(10000, 0, 0, 0);
      Attributes distantAttrs = attributes(10000, 0, 0, 0);
      int primary = createMonster(world, 1, 0, primaryAttrs);
      createMonster(world, 2, 0, nearbyAttrs);
      createMonster(world, 30, 30, distantAttrs);
      StateList states = world.getMapper(UnitStates.class).get(assassin).stateList;
      Skills.Entry fists = Riiablo.files.skills.get("Fists of Fire");
      Skills.Entry finisher = Riiablo.files.skills.get("Dragon Claw");
      AssassinSkills.addProgressiveCharge(states, fists, 5, assassin);
      AssassinSkills.addProgressiveCharge(states, fists, 5, assassin);
      world.getMapper(Casting.class).get(assassin)
          .set(finisher.Id, primary, world.getMapper(Position.class).get(primary).position);

      MathUtils.random.setSeed(0xF157200L);
      dispatchUntilConsumed(world, assassin, states, StateId.PROGRESSIVE_FIRE);

      assertTrue(primaryAttrs.get(Stat.hitpoints).asFixed() < 10000f);
      assertTrue(nearbyAttrs.get(Stat.hitpoints).asFixed() < 10000f,
          "charge two must apply the SrvDo038 area record");
      assertEquals(10000f, distantAttrs.get(Stat.hitpoints).asFixed());
      assertEquals(1000f, assassinAttrs.get(Stat.hitpoints).asFixed());
      assertEquals(0, factory.missilesCreated,
          "charge two must not create the charge-three field");
    } finally {
      world.dispose();
    }
  }

  @Test
  void fistsOfFireThirdChargeCreatesOwnedDamageSnapshotField() {
    DummyFactory factory = new DummyFactory();
    Actioneer actioneer = new Actioneer();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int assassin = createPlayer(world, 0, 0, attributes(1000, 10, 10, 100000));
      int primary = createMonster(world, 1, 0, attributes(10000, 0, 0, 0));
      StateList states = world.getMapper(UnitStates.class).get(assassin).stateList;
      Skills.Entry fists = Riiablo.files.skills.get("Fists of Fire");
      Skills.Entry finisher = Riiablo.files.skills.get("Dragon Claw");
      for (int i = 0; i < 3; i++) {
        AssassinSkills.addProgressiveCharge(states, fists, 5, assassin);
      }
      world.getMapper(Casting.class).get(assassin)
          .set(finisher.Id, primary, world.getMapper(Position.class).get(primary).position);

      MathUtils.random.setSeed(0xF157300L);
      dispatchUntilConsumed(world, assassin, states, StateId.PROGRESSIVE_FIRE);

      assertTrue(factory.missilesCreated > 0,
          "charge three must create at least one valid SrvDo039 field missile");
      int range = AssassinSkills.progressiveRange(fists, 5, 3);
      Vector2 origin = world.getMapper(Position.class).get(primary).position;
      for (int i = 0; i < factory.missileEntityIds.size(); i++) {
        int missileId = factory.missileEntityIds.get(i);
        Missile missile = world.getMapper(Missile.class).get(missileId);
        Vector2 position = world.getMapper(Position.class).get(missileId).position;
        assertEquals(assassin, missile.ownerId);
        assertEquals(fists.Id, missile.skillId);
        assertTrue(missile.damageSnapshot);
        assertTrue(missile.persistent);
        assertEquals(missile.missile.Range, missile.remainingFrames);
        assertTrue(missile.tickInterval > missile.remainingFrames);
        assertTrue(position.dst2(origin) <= range * range);
      }
    } finally {
      world.dispose();
    }
  }

  private static void buildToThreeCharges(
      World world, int assassin, int target, Skills.Entry skill) {
    world.getMapper(Casting.class).get(assassin)
        .set(skill.Id, target, world.getMapper(Position.class).get(target).position);
    int stateId = AssassinSkills.progressiveStateId(skill);
    for (int i = 0; i < 20; i++) {
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(assassin, Engine.KEYFRAME_ATK));
      StateList states = world.getMapper(UnitStates.class).get(assassin).stateList;
      if (AssassinSkills.progressiveCharges(states, stateId) == 3) return;
    }
    assertEquals(3, AssassinSkills.progressiveCharges(
        world.getMapper(UnitStates.class).get(assassin).stateList, stateId));
  }

  private static void dispatchUntilConsumed(
      World world, int assassin, StateList states, int stateId) {
    for (int i = 0; i < 20 && states.hasState(stateId); i++) {
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(assassin, Engine.KEYFRAME_ATK));
    }
    assertNull(states.getState(stateId));
  }

  private static int createPlayer(World world, float x, float y, Attributes attrs) {
    int id = world.create();
    world.getMapper(Player.class).create(id);
    world.getMapper(Class.class).create(id).type = Class.Type.PLR;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(Casting.class).create(id);
    return id;
  }

  private static int createMonster(World world, float x, float y, Attributes attrs) {
    int id = world.create();
    world.getMapper(Monster.class).create(id);
    world.getMapper(Class.class).create(id).type = Class.Type.MON;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Attributes attributes(float hp, int min, int max, int toHit) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mana, 100);
    attrs.base().put(Stat.maxmana, 100);
    attrs.base().put(Stat.mindamage, min);
    attrs.base().put(Stat.maxdamage, max);
    attrs.base().put(Stat.tohit, toHit);
    attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.armorclass, 0);
    attrs.reset();
    return attrs;
  }

  private static final class DummyFactory extends EntityFactory {
    int missilesCreated;
    final java.util.ArrayList<Integer> missileEntityIds = new java.util.ArrayList<>();

    @Override public int createPlayer(CharData charData, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) {
      return createMissile(missile, angle, position, Engine.INVALID_ENTITY);
    }

    @Override public int createMissile(
        int missileId, Vector2 angle, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(missileId);
      if (row == null) return Engine.INVALID_ENTITY;
      int entityId = world.create();
      world.getMapper(Missile.class).create(entityId)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entityId).position.set(position);
      world.getMapper(Velocity.class).create(entityId).velocity.set(angle).setLength(row.Vel);
      missilesCreated++;
      missileEntityIds.add(entityId);
      return entityId;
    }
  }
}
