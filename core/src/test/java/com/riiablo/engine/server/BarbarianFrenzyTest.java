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
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.FrenzyRuntime;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.skill.BarbarianSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class BarbarianFrenzyTest extends RiiabloTest {
  @Test
  void previousHitBuildsDataDrivenStateAndSkillLevelCapsStacks() {
    Skills.Entry frenzy = Riiablo.files.skills.get("Frenzy");
    StateList levelOne = new StateList(1);
    for (int i = 0; i < 8; i++) {
      BarbarianSkills.applyFrenzyState(levelOne, frenzy, 1, 1);
    }
    UnitState one = levelOne.getState(StateId.FRENZY);
    assertEquals(1, one.runtimeValue);
    assertEquals(48, one.velocityModifier);
    assertEquals(7, one.animationRateModifier);
    assertEquals(150, one.duration);

    StateList levelFive = new StateList(5);
    for (int i = 0; i < 9; i++) {
      BarbarianSkills.applyFrenzyState(levelFive, frenzy, 5, 5);
    }
    UnitState five = levelFive.getState(StateId.FRENZY);
    assertEquals(5, five.runtimeValue);
    assertEquals(110, five.velocityModifier);
    assertEquals(25, five.animationRateModifier);
  }

  @Test
  void twoSequenceEventsUseBothWeaponsRetargetAndDelayTheFirstStack() {
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      Skills.Entry frenzy = Riiablo.files.skills.get("Frenzy");
      Attributes barbarianAttrs = attributes(1000, 1, 1, 100000);
      int barbarian = player(world, 0, 0, barbarianAttrs);
      CharData data = CharData.createRemote("frenzy", (byte) Riiablo.BARBARIAN);
      data.setSkillLevel(frenzy.Id, 5);
      data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(weapon("hax", 10)));
      data.getItems().equipItem(BodyLoc.LARM, data.getItems().add(weapon("hax", 40)));
      world.getMapper(Player.class).get(barbarian).data = data;
      int primary = monster(world, 1, 0, attributes(100000, 0, 0, 0));
      int secondary = monster(world, 2, 0, attributes(100000, 0, 0, 0));
      Casting casting = world.getMapper(Casting.class).create(barbarian)
          .set(frenzy.Id, primary, world.getMapper(Position.class).get(primary).position);
      MathUtils.random.setSeed(0xF2E2A7L);

      float primaryBefore = hp(world, primary);
      actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(
          barbarian, Engine.KEYFRAME_ATK));
      float primaryAfter = hp(world, primary);
      assertTrue(primaryAfter < primaryBefore);
      assertNull(world.getMapper(UnitStates.class).get(barbarian)
          .stateList.getState(StateId.FRENZY));
      assertTrue(world.getMapper(FrenzyRuntime.class).get(barbarian).previousStrikeHit);

      float secondaryBefore = hp(world, secondary);
      actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(
          barbarian, Engine.KEYFRAME_ATK));
      float secondaryAfter = hp(world, secondary);
      assertTrue(secondaryAfter < secondaryBefore,
          "the odd sequence event must prefer the next nearby GUID");
      assertTrue(secondaryBefore - secondaryAfter > primaryBefore - primaryAfter,
          "the second event must use the stronger left-hand weapon");
      UnitState state = world.getMapper(UnitStates.class).get(barbarian)
          .stateList.getState(StateId.FRENZY);
      assertNotNull(state);
      assertEquals(1, state.runtimeValue,
          "the first successful hit is applied only when the second event starts");
      assertEquals(2, casting.frenzyStrikeIndex);

      state.duration = 1;
      world.getMapper(Casting.class).remove(barbarian);
      world.getMapper(Casting.class).create(barbarian)
          .set(frenzy.Id, primary, world.getMapper(Position.class).get(primary).position);
      actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(
          barbarian, Engine.KEYFRAME_ATK));
      assertEquals(2, state.runtimeValue,
          "the second event's result must survive until the next cast starts");
      assertEquals(150, state.duration,
          "a successful previous strike refreshes the native state lifetime");
    } finally {
      world.dispose();
    }
  }

  @Test
  void nativeMagicConversionRunsBeforePhysicalAndMagicResistance() {
    CombatSystem.AttackerData attacker = new CombatSystem.AttackerData();
    attacker.alwaysHit = true;
    attacker.level = 1;
    attacker.attackRating = 100;
    attacker.minDamage = 100;
    attacker.maxDamage = 100;
    attacker.physicalConversionPercent = 50;
    attacker.physicalConversionType = CombatSystem.DAMAGE_MAGIC;
    CombatSystem.DefenderData defender = new CombatSystem.DefenderData();
    defender.level = 1;
    defender.resistances[CombatSystem.DAMAGE_PHYSICAL] = 100;
    defender.immunePhysical = true;
    CombatSystem.CombatResult result = CombatSystem.INSTANCE.calculateAttack(attacker, defender);
    assertEquals(0, result.physicalDamage);
    assertEquals(50, result.elementalDamage[CombatSystem.DAMAGE_MAGIC]);
    assertEquals(50, result.totalDamage);
  }

  @Test
  void blockedStrikesDoNotArmTheNextFrenzyStack() {
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    try {
      Skills.Entry frenzy = Riiablo.files.skills.get("Frenzy");
      int barbarian = player(world, 0, 0, attributes(1000, 1, 1, 100000));
      CharData data = CharData.createRemote("blocked", (byte) Riiablo.BARBARIAN);
      data.setSkillLevel(frenzy.Id, 5);
      data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(weapon("hax", 10)));
      data.getItems().equipItem(BodyLoc.LARM, data.getItems().add(weapon("hax", 10)));
      world.getMapper(Player.class).get(barbarian).data = data;
      Attributes defender = attributes(1000, 0, 0, 0);
      defender.base().put(Stat.passive_dodge, 100);
      defender.reset();
      int target = monster(world, 1, 0, defender);
      world.getMapper(Casting.class).create(barbarian)
          .set(frenzy.Id, target, world.getMapper(Position.class).get(target).position);

      actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(
          barbarian, Engine.KEYFRAME_ATK));
      actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(
          barbarian, Engine.KEYFRAME_ATK));

      assertTrue(!world.getMapper(FrenzyRuntime.class).get(barbarian).previousStrikeHit);
      assertNull(world.getMapper(UnitStates.class).get(barbarian)
          .stateList.getState(StateId.FRENZY));
      assertEquals(1000f, hp(world, target), 0.001f);
    } finally {
      world.dispose();
    }
  }

  @Test
  void animationRateUsesResolvedAuraPercent() {
    assertEquals(273, AnimStepper.scaleStateAnimationSpeed(256, 7));
    assertEquals(320, AnimStepper.scaleStateAnimationSpeed(256, 25));
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
