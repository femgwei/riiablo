package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.engine.server.component.Velocity;
import org.junit.jupiter.api.Test;

class PlayerMovementPercentageTest {
  @Test
  void equipmentDiminishingReturnsAndStateBonusesAdd() {
    assertEquals(1.3f, StateUpdater.playerMovementMultiplier(0, 50 - 20), 0.0001f);
    assertEquals(1.5f, StateUpdater.playerMovementMultiplier(75, 0), 0.0001f);
    assertEquals(1.13f, StateUpdater.playerMovementMultiplier(0, 13), 0.0001f);
    assertEquals(1.28f, StateUpdater.playerMovementMultiplier(50, -9), 0.0001f);
    assertEquals(0.25f, StateUpdater.playerMovementMultiplier(0, -200), 0.0001f);
  }

  @Test
  void runningAddsVelocityBonusRelativeToWalkBase() {
    Velocity velocity = new Velocity().setPlayer(6, 9);
    velocity.stateSpeedMultiplier = StateUpdater.playerMovementMultiplier(75, 0);
    velocity.nativePlayerVelocityBonus = StateUpdater.playerMovementBonus(75, 0);
    assertEquals(14.0625f, velocity.speed(false) * velocity.movementMultiplier(), 0.0001f);
    velocity.movementRunning = true;
    assertEquals(18.75f, velocity.speed(true) * velocity.movementMultiplier(), 0.0001f);
    velocity.nativePlayerVelocityBonus = -200;
    velocity.stateSpeedMultiplier = 0.25f;
    assertEquals(2.34375f, velocity.speed(true) * velocity.movementMultiplier(), 0.0001f);
  }

  @Test
  void armorWeightAddsAfterFrwDiminishingReturnsForWalkAndRun() {
    Velocity velocity = new Velocity().setPlayer(6, 9);
    velocity.nativePlayerVelocityBonus = StateUpdater.playerMovementBonus(75, 20 - 15);
    velocity.stateSpeedMultiplier = StateUpdater.playerMovementMultiplier(75, 20 - 15);
    assertEquals(55f, velocity.nativePlayerVelocityBonus);
    assertEquals(14.53125f, velocity.speed(false) * velocity.movementMultiplier(), 0.0001f);
    velocity.movementRunning = true;
    assertEquals(19.21875f, velocity.speed(true) * velocity.movementMultiplier(), 0.0001f);
  }
}
