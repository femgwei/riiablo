package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.WellInteractionEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;

import net.mostlyoriginal.api.event.common.EventSystem;

class NativeShrineEffectSystemTest extends RiiabloTest {
  @Test
  void mapsNativeTimedShrinesToRuntimeModifiersAndExpiresThem() {
    StateList states = new StateList(1);

    UnitState combat = NativeShrineEffectSystem.applyTimedEffect(
        states, 10, 7, 200, 100, 2);
    assertNotNull(combat);
    assertEquals(StateId.SHRINE_COMBAT, combat.stateId);
    assertEquals(200, states.getTotalAttackModifier());
    assertEquals(100, states.getTotalDamageModifier());

    UnitState skill = NativeShrineEffectSystem.applyTimedEffect(
        states, 11, 12, 99, 99, 2);
    assertNotNull(skill);
    assertEquals(2, states.getTotalSkillModifier());

    UnitState experience = NativeShrineEffectSystem.applyTimedEffect(
        states, 12, 15, 50, 0, 2);
    assertNotNull(experience);
    assertEquals(50, states.getTotalExperienceModifier());

    states.update();
    assertEquals(2, states.getTotalSkillModifier());
    states.update();
    assertEquals(0, states.getTotalSkillModifier());
    assertEquals(0, states.getTotalExperienceModifier());
  }

  @Test
  void wellConsumesChargeForPlayerCleanseAtFullVitals() {
    World world = effectWorld();
    try {
      int player = createUnit(world, 100, 100);
      world.getMapper(UnitStates.class).get(player).stateList.addState(
          StateId.POISON, 100, 1, 99);
      world.process();

      WellInteractionEvent event = WellInteractionEvent.obtain(player, 7, 84, 64, 0);
      world.getSystem(EventSystem.class).dispatch(event);

      assertTrue(event.appliedByConsumer);
      assertFalse(world.getMapper(UnitStates.class).get(player).stateList
          .hasState(StateId.POISON));
    } finally {
      world.dispose();
    }
  }

  @Test
  void wellHealsAndCleansOwnedMercenaryButNotAnotherPlayersMercenary() {
    World world = effectWorld();
    try {
      int player = createUnit(world, 100, 100);
      int owned = createMercenary(world, player, 25, 100);
      int foreign = createMercenary(world, player + 100, 25, 100);
      world.getMapper(UnitStates.class).get(owned).stateList.addState(
          StateId.FREEZE, 100, 1, 99);
      world.getMapper(UnitStates.class).get(foreign).stateList.addState(
          StateId.POISON, 100, 1, 99);
      world.process();

      WellInteractionEvent event = WellInteractionEvent.obtain(player, 7, 84, 64, 0);
      world.getSystem(EventSystem.class).dispatch(event);

      assertTrue(event.appliedByConsumer);
      assertEquals(100f, value(world, owned, Stat.hitpoints));
      assertFalse(world.getMapper(UnitStates.class).get(owned).stateList
          .hasState(StateId.FREEZE));
      assertEquals(25f, value(world, foreign, Stat.hitpoints));
      assertTrue(world.getMapper(UnitStates.class).get(foreign).stateList
          .hasState(StateId.POISON));
    } finally {
      world.dispose();
    }
  }

  @Test
  void petCleanseAloneConsumesChargeWhenStateActuallyChanges() {
    World world = effectWorld();
    try {
      int player = createUnit(world, 100, 100);
      int owned = createMercenary(world, player, 100, 100);
      world.getMapper(UnitStates.class).get(owned).stateList.addState(
          StateId.POISON, 100, 1, 99);
      world.process();

      WellInteractionEvent event = WellInteractionEvent.obtain(player, 7, 84, 64, 0);
      world.getSystem(EventSystem.class).dispatch(event);

      assertTrue(event.appliedByConsumer);
      assertFalse(world.getMapper(UnitStates.class).get(owned).stateList
          .hasState(StateId.POISON));
    } finally {
      world.dispose();
    }
  }

  private static World effectWorld() {
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new NativeShrineEffectSystem())
        .build());
  }

  private static int createMercenary(World world, int owner, float hp, float maxHp) {
    int id = createUnit(world, hp, maxHp);
    world.getMapper(Mercenary.class).create(id).set(owner, 0, 1, 1, 1);
    return id;
  }

  private static int createUnit(World world, float hp, float maxHp) {
    int id = world.create();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(hp, maxHp);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Attributes attributes(float hp, float maxHp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, maxHp);
    attrs.base().put(Stat.stamina, 20f);
    attrs.base().put(Stat.maxstamina, 100f);
    attrs.reset();
    return attrs;
  }

  private static float value(World world, int entityId, short stat) {
    return world.getMapper(AttributesWrapper.class).get(entityId).attrs
        .aggregate().getValue(stat, 0f);
  }
}
