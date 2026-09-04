package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class DruidShapeShiftTest extends RiiabloTest {
  @Test
  void werewolfBuildsNativeStateWithoutFrameCompounding() {
    Skills.Entry wolf = Riiablo.files.skills.get(SkillId.WEREWOLF);
    StateList states = new StateList(7);
    UnitState state = apply(states, wolf, 2, 1).appliedState;
    assertNotNull(state);
    assertEquals(StateId.WOLF, state.stateId);
    assertEquals(2000, state.duration);
    assertEquals(25, state.maxStaminaModifier);
    assertEquals(29, state.animationRateModifier);
    assertEquals(wolf.ToHit + wolf.LevToHit, state.attackModifier);
    assertEquals(45, state.maxLifeModifier);

    int attack = states.getTotalAttackModifier();
    int animation = states.getTotalAnimationRateModifier();
    states.update();
    assertEquals(attack, states.getTotalAttackModifier());
    assertEquals(animation, states.getTotalAnimationRateModifier());
  }

  @Test
  void werebearUsesNativeDamageDefenseAndLycanthropyLife() {
    Skills.Entry bear = Riiablo.files.skills.get(SkillId.WEREBEAR);
    UnitState state = apply(new StateList(9), bear, 3, 2).appliedState;
    assertNotNull(state);
    assertEquals(StateId.BEAR, state.stateId);
    assertEquals(2500, state.duration);
    assertEquals(71, state.damageModifier);
    assertEquals(37, state.defenseModifier);
    assertEquals(100, state.maxLifeModifier);
  }

  @Test
  void activeTransformIsRemovedBeforeAnotherCanBeApplied() {
    StateList states = new StateList(11);
    Skills.Entry wolf = Riiablo.files.skills.get(SkillId.WEREWOLF);
    Skills.Entry bear = Riiablo.files.skills.get(SkillId.WEREBEAR);
    assertNotNull(apply(states, wolf, 1, 0).appliedState);

    DruidSkills.ShapeShiftResult toggledOff = apply(states, bear, 1, 0);
    assertEquals(StateId.WOLF, toggledOff.removedStateId);
    assertNull(toggledOff.appliedState);
    assertFalse(states.hasState(StateId.WOLF));
    assertFalse(states.hasState(StateId.BEAR));

    assertEquals(StateId.BEAR, apply(states, bear, 1, 0).appliedState.stateId);
    assertTrue(states.isTransformed());
  }

  @Test
  void actioneerDispatchesSrvDo116AtAnimationKeyframe() {
    Actioneer actioneer = new Actioneer();
    DummyFactory factory = new DummyFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      Skills.Entry wolf = Riiablo.files.skills.get(SkillId.WEREWOLF);
      CharData data = CharData.createRemote("shape", (byte) Riiablo.DRUID);
      data.setSkillLevel(SkillId.WEREWOLF, 2);
      data.setSkillLevel(SkillId.LYCANTHROPY, 1);
      int player = world.create();
      world.getMapper(Player.class).create(player).data = data;
      world.getMapper(UnitStates.class).create(player).init(player);
      world.getMapper(Casting.class).create(player)
          .set(wolf.Id, Engine.INVALID_ENTITY, Vector2.Zero);

      actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(player, Engine.KEYFRAME_SKL));

      UnitState state = world.getMapper(UnitStates.class).get(player)
          .stateList.getState(StateId.WOLF);
      assertNotNull(state);
      assertEquals(2, state.level);
      assertEquals(2000, state.duration);
    } finally {
      world.dispose();
    }
  }

  private static DruidSkills.ShapeShiftResult apply(
      StateList states, Skills.Entry skill, int level, int lycanthropyLevel) {
    return DruidSkills.applyShapeShiftState(states, skill, level, 1,
        name -> "Shape Shifting".equals(name) ? lycanthropyLevel : 0,
        name -> Riiablo.files.skills.get(name));
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
