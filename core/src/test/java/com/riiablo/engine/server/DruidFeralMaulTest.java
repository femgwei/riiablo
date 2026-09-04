package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
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
import com.riiablo.engine.server.component.serializer.StateSerializer;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class DruidFeralMaulTest extends RiiabloTest {
  @Test
  void shapeRestrictionsFollowRestrictAndStateColumns() {
    Skills.Entry feral = Riiablo.files.skills.get(SkillId.FERAL_RAGE);
    Skills.Entry maul = Riiablo.files.skills.get(SkillId.MAUL);
    StateList states = new StateList(1);
    assertFalse(DruidSkills.isSkillAllowedInCurrentShape(feral, states));
    assertFalse(DruidSkills.isSkillAllowedInCurrentShape(maul, states));
    states.addState(StateId.WOLF, 100, 1, 1);
    assertTrue(DruidSkills.isSkillAllowedInCurrentShape(feral, states));
    assertFalse(DruidSkills.isSkillAllowedInCurrentShape(maul, states));
    states.removeState(StateId.WOLF);
    states.addState(StateId.BEAR, 100, 1, 1);
    assertFalse(DruidSkills.isSkillAllowedInCurrentShape(feral, states));
    assertTrue(DruidSkills.isSkillAllowedInCurrentShape(maul, states));
  }

  @Test
  void successfulHitsBuildRefreshAndCapNativeFeralStacks() {
    Skills.Entry feral = Riiablo.files.skills.get(SkillId.FERAL_RAGE);
    StateList states = new StateList(2);
    states.addState(StateId.WOLF, 2000, 1, 2);
    UnitState state = null;
    for (int i = 0; i < 8; i++) {
      state = DruidSkills.applyFeralMaulState(states, feral, 5, 2);
    }
    assertNotNull(state);
    assertEquals(5, state.runtimeValue);
    assertEquals(500, state.duration);
    assertEquals(DruidSkills.getFeralMaulAuraStat(feral, 5, "velocitypercent"),
        state.velocityModifier);
    assertEquals(20, state.lifeLeechModifier);
    state.duration = 1;
    DruidSkills.applyFeralMaulState(states, feral, 5, 2);
    assertEquals(500, state.duration);
    assertEquals(5, state.runtimeValue);
  }

  @Test
  void maulRebuildsDamageAndStunFromStackCountWithoutCompounding() {
    Skills.Entry maul = Riiablo.files.skills.get(SkillId.MAUL);
    StateList states = new StateList(3);
    states.addState(StateId.BEAR, 2000, 1, 3);
    UnitState one = DruidSkills.applyFeralMaulState(states, maul, 1, 3);
    assertEquals(1, one.runtimeValue);
    assertEquals(20, one.damageModifier);
    assertEquals(DruidSkills.getFeralMaulAuraStat(maul, 1, "stunlength"), one.stunLength);
    UnitState two = DruidSkills.applyFeralMaulState(states, maul, 1, 3);
    assertEquals(2, two.runtimeValue);
    assertEquals(40, two.damageModifier);
    assertEquals(DruidSkills.getFeralMaulAuraStat(maul, 2, "stunlength"), two.stunLength);
    assertEquals(40, states.getTotalDamageModifier());
  }

  @Test
  void losingShapeRemovesOnlyItsDependentChargeState() {
    StateList states = new StateList(4);
    states.addState(StateId.WOLF, 100, 1, 4);
    states.addState(StateId.FERALRAGE, 100, 1, 4);
    states.addState(StateId.MAUL, 100, 1, 4);
    assertEquals(1, DruidSkills.removeInvalidFeralMaulStates(states));
    assertTrue(states.hasState(StateId.FERALRAGE));
    assertFalse(states.hasState(StateId.MAUL));
    states.removeState(StateId.WOLF);
    assertEquals(1, DruidSkills.removeInvalidFeralMaulStates(states));
    assertFalse(states.hasState(StateId.FERALRAGE));
  }

  @Test
  void multiplayerSnapshotCarriesFeralStateDurationLevelStacksAndVelocity() {
    Skills.Entry feral = Riiablo.files.skills.get(SkillId.FERAL_RAGE);
    UnitStates source = new UnitStates().init(42);
    source.stateList.addState(StateId.WOLF, 2000, 2, 42);
    UnitState applied = DruidSkills.applyFeralMaulState(source.stateList, feral, 5, 42);
    StateSerializer serializer = new StateSerializer();
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    int stateOffset = serializer.putData(builder, source);
    int typeOffset = EntitySync.createComponentTypeVector(
        builder, new byte[] {ComponentP.StateP});
    int componentOffset = EntitySync.createComponentVector(builder, new int[] {stateOffset});
    int root = EntitySync.createEntitySync(builder, 42, 0, 0, typeOffset, componentOffset);
    builder.finish(root);

    UnitStates remote = new UnitStates().init(42);
    serializer.getData(EntitySync.getRootAsEntitySync(builder.dataBuffer()), 0, remote);
    UnitState snapshot = remote.stateList.getState(StateId.FERALRAGE);
    assertNotNull(snapshot);
    assertEquals(applied.duration, snapshot.duration);
    assertEquals(applied.level, snapshot.level);
    assertEquals(applied.runtimeValue, snapshot.runtimeValue);
    assertEquals(applied.velocityModifier, snapshot.velocityModifier);
  }

  @Test
  void srvSt56ResultIsConsumedOnceAndMissDoesNotBuildAStack() {
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = world(actioneer, factory);
    try {
      Skills.Entry feral = Riiablo.files.skills.get(SkillId.FERAL_RAGE);
      int druid = player(world, 0, 0, attributes(1000, 100, 100, 100000));
      CharData data = CharData.createRemote("feral", (byte) Riiablo.DRUID);
      data.setSkillLevel(feral.Id, 5);
      data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(weapon("hax", 100)));
      world.getMapper(Player.class).get(druid).data = data;
      world.getMapper(UnitStates.class).get(druid).stateList
          .addState(StateId.WOLF, 2000, 1, druid);
      int target = monster(world, 1, 0, attributes(10000, 0, 0, 0));
      Casting casting = world.getMapper(Casting.class).create(druid)
          .set(feral.Id, target, new Vector2(1, 0));
      MathUtils.random.setSeed(0xFE2A1L);
      actioneer.onSkillStart(SkillStartEvent.obtain(
          druid, feral.Id, target, new Vector2(1, 0), feral.srvstfunc, feral.cltstfunc));
      assertTrue(casting.feralMaulPrepared);
      casting.feralMaulCombat.hit = false;
      actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(druid, Engine.KEYFRAME_ATK));
      assertNull(world.getMapper(UnitStates.class).get(druid)
          .stateList.getState(StateId.FERALRAGE));
      assertFalse(casting.feralMaulPrepared);

      world.getMapper(Casting.class).remove(druid);
      casting = world.getMapper(Casting.class).create(druid)
          .set(feral.Id, target, new Vector2(1, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(
          druid, feral.Id, target, new Vector2(1, 0), feral.srvstfunc, feral.cltstfunc));
      casting.feralMaulCombat.hit = true;
      casting.feralMaulCombat.blocked = false;
      float before = hp(world, target);
      actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(druid, Engine.KEYFRAME_ATK));
      assertTrue(hp(world, target) < before);
      assertEquals(1, world.getMapper(UnitStates.class).get(druid)
          .stateList.getState(StateId.FERALRAGE).runtimeValue);
    } finally {
      world.dispose();
    }
  }

  @Test
  void wrongShapeAndOutOfRangeNeverPrepareCombatRecord() {
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = world(actioneer, factory);
    try {
      Skills.Entry feral = Riiablo.files.skills.get(SkillId.FERAL_RAGE);
      int druid = player(world, 0, 0, attributes(1000, 10, 10, 1000));
      CharData data = CharData.createRemote("restricted", (byte) Riiablo.DRUID);
      data.setSkillLevel(feral.Id, 1);
      world.getMapper(Player.class).get(druid).data = data;
      int target = monster(world, 50, 0, attributes(1000, 0, 0, 0));
      Casting casting = world.getMapper(Casting.class).create(druid)
          .set(feral.Id, target, new Vector2(50, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(
          druid, feral.Id, target, new Vector2(50, 0), feral.srvstfunc, feral.cltstfunc));
      assertFalse(casting.feralMaulPrepared);
      world.getMapper(UnitStates.class).get(druid).stateList
          .addState(StateId.WOLF, 100, 1, druid);
      actioneer.onSkillStart(SkillStartEvent.obtain(
          druid, feral.Id, target, new Vector2(50, 0), feral.srvstfunc, feral.cltstfunc));
      assertFalse(casting.feralMaulPrepared);
    } finally {
      world.dispose();
    }
  }

  @Test
  void rabiesConsumesPreparedHitAndMarksTargetInfected() {
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = world(actioneer, factory);
    try {
      Skills.Entry rabies = Riiablo.files.skills.get(SkillId.RABIES);
      int druid = player(world, 0, 0, attributes(1000, 20, 20, 100000));
      CharData data = CharData.createRemote("rabies", (byte) Riiablo.DRUID);
      data.setSkillLevel(rabies.Id, 1);
      world.getMapper(Player.class).get(druid).data = data;
      world.getMapper(UnitStates.class).get(druid).stateList
          .addState(StateId.WOLF, 2000, 1, druid);
      int target = monster(world, 1, 0, attributes(1000, 0, 0, 0));
      Casting casting = world.getMapper(Casting.class).create(druid)
          .set(rabies.Id, target, new Vector2(1, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(
          druid, rabies.Id, target, new Vector2(1, 0), rabies.srvstfunc, rabies.cltstfunc));
      assertNotNull(casting.rabiesCombat);
      casting.rabiesCombat.hit = true;
      casting.rabiesCombat.blocked = false;
      casting.rabiesPrepared = true;
      actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(druid, Engine.KEYFRAME_ATK));
      assertTrue(world.getMapper(UnitStates.class).get(target)
          .stateList.hasState(StateId.RABIES));
      assertFalse(casting.rabiesPrepared);
    } finally {
      world.dispose();
    }
  }

  @Test
  void fireClawsConsumesSrvSt58RecordWithoutGenericSecondHit() {
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = world(actioneer, factory);
    try {
      Skills.Entry claws = Riiablo.files.skills.get(SkillId.FIRE_CLAWS);
      int druid = player(world, 0, 0, attributes(1000, 10, 10, 100000));
      CharData data = CharData.createRemote("claws", (byte) Riiablo.DRUID);
      data.setSkillLevel(claws.Id, 1);
      world.getMapper(Player.class).get(druid).data = data;
      world.getMapper(UnitStates.class).get(druid).stateList
          .addState(StateId.BEAR, 2000, 1, druid);
      int target = monster(world, 1, 0, attributes(1000, 0, 0, 0));
      Casting casting = world.getMapper(Casting.class).create(druid)
          .set(claws.Id, target, new Vector2(1, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(
          druid, claws.Id, target, new Vector2(1, 0), claws.srvstfunc, claws.cltstfunc));
      assertNotNull(casting.fireClawsCombat);
      casting.fireClawsCombat.hit = true;
      casting.fireClawsCombat.blocked = false;
      casting.fireClawsPrepared = true;
      float expected = casting.fireClawsCombat.totalDamage;
      actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(druid, Engine.KEYFRAME_ATK));
      assertEquals(1000f - expected, hp(world, target));
      assertFalse(casting.fireClawsPrepared);
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
