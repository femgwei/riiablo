package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.WhirlwindRuntime;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.engine.server.skill.BarbarianSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class BarbarianWhirlwindTest extends RiiabloTest {
  @Test
  void expansionWeaponSpeedBreakpointsMatchSrvDo076() {
    assertEquals(4, BarbarianSkills.getWhirlwindAttackInterval(11));
    assertEquals(6, BarbarianSkills.getWhirlwindAttackInterval(12));
    assertEquals(8, BarbarianSkills.getWhirlwindAttackInterval(15));
    assertEquals(10, BarbarianSkills.getWhirlwindAttackInterval(18));
    assertEquals(12, BarbarianSkills.getWhirlwindAttackInterval(20));
    assertEquals(14, BarbarianSkills.getWhirlwindAttackInterval(23));
    assertEquals(16, BarbarianSkills.getWhirlwindAttackInterval(26));
  }

  @Test
  void straightSkillMoveCreatesStateAndDualWieldPulseUsesBothHands() {
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = world(actioneer, factory);
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
      int barbarian = player(world, 0, 0, attributes(1000, 1, 1, 100000));
      CharData data = CharData.createRemote("whirlwind", (byte) Riiablo.BARBARIAN);
      data.setSkillLevel(skill.Id, 20);
      data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(weapon("hax", 40)));
      data.getItems().equipItem(BodyLoc.LARM, data.getItems().add(weapon("hax", 100)));
      world.getMapper(Player.class).get(barbarian).data = data;
      int first = monster(world, 1, 0, attributes(100000, 0, 0, 0));
      int second = monster(world, 2, 0, attributes(100000, 0, 0, 0));
      world.getMapper(Casting.class).create(barbarian)
          .set(skill.Id, Engine.INVALID_ENTITY, new Vector2(10, 0));

      actioneer.onSkillStart(SkillStartEvent.obtain(
          barbarian, skill.Id, Engine.INVALID_ENTITY, new Vector2(10, 0),
          skill.srvstfunc, skill.cltstfunc));
      WhirlwindRuntime runtime = world.getMapper(WhirlwindRuntime.class).get(barbarian);
      assertNotNull(runtime);
      assertEquals(10f, runtime.destination.x, 0.001f);
      assertTrue(world.getMapper(UnitStates.class).get(barbarian)
          .stateList.hasState(StateId.WHIRLWIND));

      float firstBefore = hp(world, first);
      float secondBefore = hp(world, second);
      MathUtils.random.setSeed(0x7711L);
      world.setDelta(4f / WhirlwindSystem.GAME_FRAMES_PER_SECOND);
      world.process();

      assertEquals(2, runtime.strikeIndex);
      assertTrue(hp(world, first) < firstBefore);
      assertTrue(hp(world, second) < secondBefore);
      assertTrue(secondBefore - hp(world, second) > firstBefore - hp(world, first),
          "the second native attack must use the stronger left-hand weapon");
      assertFalse(world.getMapper(Velocity.class).get(barbarian).velocity.isZero());

      world.getMapper(Position.class).get(barbarian).position.set(runtime.destination);
      world.process();
      assertFalse(world.getMapper(WhirlwindRuntime.class).has(barbarian));
      assertFalse(world.getMapper(Casting.class).has(barbarian));
      assertFalse(world.getMapper(UnitStates.class).get(barbarian)
          .stateList.hasState(StateId.WHIRLWIND));
      assertTrue(world.getMapper(Velocity.class).get(barbarian).velocity.isZero());
    } finally {
      world.dispose();
    }
  }

  @Test
  void meleeTargetIsRejectedAndDeathClearsSkillMoveState() {
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = world(actioneer, factory);
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
      int barbarian = player(world, 0, 0, attributes(1000, 10, 10, 100000));
      int target = monster(world, 0.5f, 0, attributes(1000, 0, 0, 0));
      world.getMapper(Casting.class).create(barbarian)
          .set(skill.Id, target, new Vector2(0.5f, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(
          barbarian, skill.Id, target, new Vector2(0.5f, 0),
          skill.srvstfunc, skill.cltstfunc));
      assertFalse(world.getMapper(WhirlwindRuntime.class).has(barbarian));
      assertFalse(world.getMapper(Casting.class).has(barbarian));

      world.getMapper(Casting.class).create(barbarian)
          .set(skill.Id, Engine.INVALID_ENTITY, new Vector2(8, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(
          barbarian, skill.Id, Engine.INVALID_ENTITY, new Vector2(8, 0),
          skill.srvstfunc, skill.cltstfunc));
      assertTrue(world.getMapper(WhirlwindRuntime.class).has(barbarian));
      actioneer.onDeath(DeathEvent.obtain(target, barbarian));
      assertFalse(world.getMapper(WhirlwindRuntime.class).has(barbarian));
      assertFalse(world.getMapper(UnitStates.class).get(barbarian)
          .stateList.hasState(StateId.WHIRLWIND));
      assertTrue(world.getMapper(Velocity.class).get(barbarian).velocity.isZero());
    } finally {
      world.dispose();
    }
  }

  private static World world(Actioneer actioneer, DummyFactory factory) {
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new WhirlwindSystem(), new Pathfinder(), factory)
        .build()
        .register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
  }

  private static int player(World world, float x, float y, Attributes attrs) {
    int id = world.create();
    world.getMapper(Player.class).create(id);
    world.getMapper(Class.class).create(id).type = Class.Type.PLR;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(Velocity.class).create(id).set(6f, 9f);
    return id;
  }

  private static int monster(World world, float x, float y, Attributes attrs) {
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
    attrs.base().put(Stat.mindamage, min);
    attrs.base().put(Stat.maxdamage, max);
    attrs.base().put(Stat.tohit, toHit);
    attrs.base().put(Stat.level, 90);
    attrs.base().put(Stat.armorclass, 0);
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
