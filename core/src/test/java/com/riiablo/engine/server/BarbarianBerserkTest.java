package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;
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
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class BarbarianBerserkTest extends RiiabloTest {
  @Test
  void startAppliesNativeDefenseZeroStateAndCalc2Duration() {
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = world(actioneer, factory);
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Berserk");
      int barbarian = player(world, 0, 0, attributes(1000, 10, 10, 100000, 50));
      CharData data = CharData.createRemote("berserk", (byte) Riiablo.BARBARIAN);
      data.setSkillLevel(skill.Id, 1);
      data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(weapon("hax", 10)));
      world.getMapper(Player.class).get(barbarian).data = data;
      int target = monster(world, 1, 0, attributes(1000, 0, 0, 0, 50));
      world.getMapper(Casting.class).create(barbarian)
          .set(skill.Id, target, new Vector2(1, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(
          barbarian, skill.Id, target, new Vector2(1, 0), skill.srvstfunc, skill.cltstfunc));

      UnitState state = world.getMapper(UnitStates.class).get(barbarian)
          .stateList.getState(StateId.BERSERK);
      assertNotNull(state);
      assertEquals(-100, state.defenseModifier);
      assertEquals(68, state.duration);
    } finally {
      world.dispose();
    }
  }

  @Test
  void keyframeConvertsPhysicalPacketToMagicBeforeResistance() {
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = world(actioneer, factory);
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Berserk");
      int barbarian = player(world, 0, 0, attributes(1000, 0, 0, 100000, 0));
      CharData data = CharData.createRemote("berserk", (byte) Riiablo.BARBARIAN);
      data.setSkillLevel(skill.Id, 1);
      data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(weapon("hax", 100)));
      world.getMapper(Player.class).get(barbarian).data = data;
      int target = monster(world, 1, 0, attributes(1000, 0, 0, 0, 100));
      world.getMapper(Casting.class).create(barbarian)
          .set(skill.Id, target, new Vector2(1, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(
          barbarian, skill.Id, target, new Vector2(1, 0), skill.srvstfunc, skill.cltstfunc));
      MathUtils.random.setSeed(0xBE25E2L);
      actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(barbarian, Engine.KEYFRAME_ATK));

      assertTrue(hp(world, target) < 1000f,
          "magic damage must bypass the target's physical immunity");
      assertTrue(world.getMapper(UnitStates.class).get(barbarian)
          .stateList.hasState(StateId.BERSERK));
    } finally {
      world.dispose();
    }
  }

  private static World world(Actioneer actioneer, DummyFactory factory) {
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
  }

  private static int player(World world, float x, float y, Attributes attrs) {
    int id = world.create();
    world.getMapper(Player.class).create(id);
    world.getMapper(Class.class).create(id).type = Class.Type.PLR;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
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

  private static Attributes attributes(float hp, int min, int max, int toHit, int physicalResist) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mindamage, min);
    attrs.base().put(Stat.maxdamage, max);
    attrs.base().put(Stat.tohit, toHit);
    attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.armorclass, 0);
    attrs.base().put(Stat.damageresist, physicalResist);
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
