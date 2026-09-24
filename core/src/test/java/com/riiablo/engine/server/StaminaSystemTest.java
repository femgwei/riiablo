package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.TemporaryRunning;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import org.junit.jupiter.api.Test;

class StaminaSystemTest extends RiiabloTest {
  @Test
  void usesNativeFixedPointRunDrain() {
    assertEquals(40f / 256f, StaminaSystem.drainPerTick(20f, 0, 0));
    assertEquals(80f / 256f, StaminaSystem.drainPerTick(20f, 10, 0));
    assertEquals(30f / 256f, StaminaSystem.drainPerTick(20f, 0, 25));
  }

  @Test
  void runningInTownDoesNotDrainOrRecoverNormally() {
    assertEquals(false, StaminaSystem.shouldDrain(true, true, 20f));
    assertEquals(true, StaminaSystem.shouldDrain(true, false, 20f));
    assertEquals(-1, StaminaSystem.recoveryShift(true, true, true, 20f, 0));
  }

  @Test
  void exhaustedWildernessWalkingDoesNotRecoverUntilPlayerStops() {
    assertEquals(-1, StaminaSystem.recoveryShift(true, false, false, 0f, 0));
    assertEquals(8, StaminaSystem.recoveryShift(false, false, false, 0f, 0));
  }

  @Test
  void townWalkingAndSpecialBonusesAllowRecovery() {
    assertEquals(9, StaminaSystem.recoveryShift(true, false, true, 0f, 0));
    assertEquals(8, StaminaSystem.recoveryShift(true, true, false, 0f, 1000));
  }

  @Test
  void usesNativeMaximumBasedRecoveryRates() {
    assertEquals(84f / 256f, StaminaSystem.recoveryPerTick(84f, 0, 8));
    assertEquals(42f / 256f, StaminaSystem.recoveryPerTick(84f, 0, 9));
    assertEquals(168f / 256f, StaminaSystem.recoveryPerTick(84f, 100, 8));
  }

  @Test
  void exhaustedPlayerKeepsRunPreferenceButUsesWalkMovement() {
    AttributesWrapper wrapper = new AttributesWrapper();
    wrapper.attrs = Attributes.obtainStandard();
    wrapper.attrs.base().put(Stat.maxstamina, 84f);
    wrapper.attrs.base().put(Stat.stamina, 0f);
    wrapper.attrs.reset();

    assertEquals(false, StaminaSystem.hasRunStamina(wrapper));
    wrapper.attrs.base().put(Stat.stamina, 1f);
    wrapper.attrs.reset();
    assertEquals(true, StaminaSystem.hasRunStamina(wrapper));
  }

  @Test
  void exhaustionDoesNotToggleThePersistentRunButtonState() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new StaminaSystem())
        .build());
    try {
      int player = world.create();
      world.getMapper(Player.class).create(player);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.maxstamina, 84f);
      attrs.base().put(Stat.stamina, 0.01f);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(player).attrs = attrs;
      Velocity velocity = world.getMapper(Velocity.class).create(player).set(6f, 9f);
      velocity.velocity.set(9f, 0f);
      world.getMapper(Running.class).create(player);

      world.setDelta(1f / 25f);
      world.process();

      assertTrue(world.getMapper(Running.class).has(player),
          "stamina exhaustion must not change the player's run/walk preference");
      assertEquals(0f, attrs.aggregate().getValue(Stat.stamina, -1f));
      assertEquals(6f, velocity.velocity.len(), 0.0001f);
    } finally {
      world.dispose();
    }
  }

  @Test
  void temporaryRunDrainsStaminaWithoutChangingPersistentPreference() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new StaminaSystem())
        .build());
    try {
      int player = world.create();
      world.getMapper(Player.class).create(player);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.maxstamina, 84f);
      attrs.base().put(Stat.stamina, 10f);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(player).attrs = attrs;
      Velocity velocity = world.getMapper(Velocity.class).create(player).set(6f, 9f);
      velocity.velocity.set(9f, 0f);
      world.getMapper(TemporaryRunning.class).create(player);

      world.setDelta(1f / 25f);
      world.process();

      assertTrue(attrs.aggregate().getValue(Stat.stamina, 10f) < 10f);
      assertTrue(world.getMapper(TemporaryRunning.class).has(player));
      assertEquals(false, world.getMapper(Running.class).has(player),
          "hold-to-run must not change the persistent run button state");
    } finally {
      world.dispose();
    }
  }

  @Test
  void staminaShrinePreventsDrainAndRecoversWhileRunning() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new StaminaSystem())
        .build());
    try {
      int player = world.create();
      world.getMapper(Player.class).create(player);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.maxstamina, 84f);
      attrs.base().put(Stat.stamina, 40f);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(player).attrs = attrs;
      Velocity velocity = world.getMapper(Velocity.class).create(player).set(6f, 9f);
      velocity.velocity.set(9f, 0f);
      world.getMapper(Running.class).create(player);
      UnitStates states = world.getMapper(UnitStates.class).create(player).init(player);
      UnitState shrine = states.stateList.addState(
          StateId.SHRINE_STAMINA, 100, 1, 99);
      shrine.setNativeModifier(Stat.staminarecoverybonus, 1000);

      world.setDelta(1f / 25f);
      world.process();

      assertTrue(attrs.aggregate().getValue(Stat.stamina, 0f) > 40f,
          "stamina shrine must recover, not drain, while running");
    } finally {
      world.dispose();
    }
  }
}
