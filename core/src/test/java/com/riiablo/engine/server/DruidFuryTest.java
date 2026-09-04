package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native Fury count, shape, damage and GUID target-chain regression coverage. */
class DruidFuryTest extends RiiabloTest {
  @Test
  void furyIsWolfOnlyAndUsesCalc2ForWeaponDamage() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.FURY);
    StateList states = new StateList(1);
    assertFalse(DruidSkills.isSkillAllowedInCurrentShape(skill, states));
    states.addState(StateId.BEAR, 100, 1, 1);
    assertFalse(DruidSkills.isSkillAllowedInCurrentShape(skill, states));
    states.removeState(StateId.BEAR);
    states.addState(StateId.WOLF, 100, 1, 1);
    assertTrue(DruidSkills.isSkillAllowedInCurrentShape(skill, states));

    Attributes attacker = attributes(1000, 0, 0, 100000);
    Item weapon = weapon("hax", 100);
    int[] level1 = DruidSkills.calculateFuryWeaponDamage(
        skill, 1, attacker, weapon, states);
    int[] level2 = DruidSkills.calculateFuryWeaponDamage(
        skill, 2, attacker, weapon, states);
    assertEquals(200, level1[0]);
    assertEquals(200, level1[1]);
    assertEquals(217, level2[0]);
    assertEquals(217, level2[1]);
  }

  @Test
  void srvSt37RejectsBearAndInitializesNativeWolfStrikeCount() {
    Actioneer actioneer = new Actioneer();
    World world = world(actioneer);
    try {
      Skills.Entry fury = Riiablo.files.skills.get(SkillId.FURY);
      int druid = player(world, fury, 4, StateId.BEAR);
      int target = monster(world, 1, 0, 10000);
      Casting casting = world.getMapper(Casting.class).create(druid)
          .set(fury.Id, target, new Vector2(1, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(
          druid, fury.Id, target, new Vector2(1, 0), fury.srvstfunc, fury.cltstfunc));
      assertFalse(casting.furyInitialized);

      world.getMapper(Casting.class).create(druid).set(fury.Id, target, new Vector2(1, 0));
      StateList states = world.getMapper(UnitStates.class).get(druid).stateList;
      states.removeState(StateId.BEAR);
      states.addState(StateId.WOLF, 1000, 1, druid);
      actioneer.onSkillStart(SkillStartEvent.obtain(
          druid, fury.Id, target, new Vector2(1, 0), fury.srvstfunc, fury.cltstfunc));
      casting = world.getMapper(Casting.class).get(druid);
      assertTrue(casting.furyInitialized);
      assertEquals(5, casting.furyRemainingStrikes);
      assertEquals(target, casting.furyCurrentTargetId);
    } finally {
      world.dispose();
    }
  }

  @Test
  void srvDo013AlternatesNearbyGuidsAndCompletesAllStrikes() {
    Actioneer actioneer = new Actioneer();
    World world = world(actioneer);
    try {
      Skills.Entry fury = Riiablo.files.skills.get(SkillId.FURY);
      int druid = player(world, fury, 4, StateId.WOLF);
      int first = monster(world, 1, 0, 10000);
      int second = monster(world, 0, 1, 10000);
      Casting casting = world.getMapper(Casting.class).create(druid)
          .set(fury.Id, first, new Vector2(1, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(
          druid, fury.Id, first, new Vector2(1, 0), fury.srvstfunc, fury.cltstfunc));

      MathUtils.random.setSeed(0xF013L);
      actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(druid, Engine.KEYFRAME_ATK));
      assertEquals(4, casting.furyRemainingStrikes);
      assertEquals(second, casting.furyCurrentTargetId);
      actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(druid, Engine.KEYFRAME_ATK));
      assertEquals(3, casting.furyRemainingStrikes);
      assertEquals(first, casting.furyCurrentTargetId);
      for (int i = 0; i < 3; i++) {
        actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(druid, Engine.KEYFRAME_ATK));
      }
      assertEquals(0, casting.furyRemainingStrikes);
      assertEquals(Engine.INVALID_ENTITY, casting.furyCurrentTargetId);
      assertTrue(hp(world, first) < 10000 || hp(world, second) < 10000);
    } finally {
      world.dispose();
    }
  }

  private static World world(Actioneer actioneer) {
    DummyFactory factory = new DummyFactory();
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
  }

  private static int player(World world, Skills.Entry fury, int level, int shape) {
    int id = world.create();
    CharData data = CharData.createRemote("fury", (byte) Riiablo.DRUID);
    data.setSkillLevel(fury.Id, level);
    data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(weapon("hax", 10)));
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Class.class).create(id).type = Class.Type.PLR;
    world.getMapper(Position.class).create(id).position.set(0, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1000, 10, 10, 100000);
    world.getMapper(UnitStates.class).create(id).init(id)
        .stateList.addState(shape, 1000, 1, id);
    return id;
  }

  private static int monster(World world, float x, float y, float hp) {
    int id = world.create();
    world.getMapper(Monster.class).create(id);
    world.getMapper(Class.class).create(id).type = Class.Type.MON;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(hp, 0, 0, 0);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Attributes attributes(float hp, int min, int max, int toHit) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mindamage, min);
    attrs.base().put(Stat.maxdamage, max);
    attrs.base().put(Stat.tohit, toHit);
    attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.armorclass, 0);
    attrs.base().put(Stat.strength, 0);
    attrs.reset();
    return attrs;
  }

  private static Item weapon(String code, int damage) {
    Item item = new Item();
    item.reset();
    item.setBase(Riiablo.files.weapons.get(code));
    item.attrs.base().get(Stat.mindamage).set(damage);
    item.attrs.base().get(Stat.maxdamage).set(damage);
    item.attrs.reset();
    return item;
  }

  private static float hp(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId)
        .attrs.get(Stat.hitpoints).asFixed();
  }

  private static final class DummyFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) { return -1; }
  }
}
