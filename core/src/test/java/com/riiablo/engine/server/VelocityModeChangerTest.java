package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VelocityModeChangerTest {
  @Test
  void usesNativePlayerWalkAndRunAnimationRatesAtBaseVelocity() {
    assertEquals(213, VelocityModeChanger.scaleAnimationSpeed(
        VelocityModeChanger.PLAYER_WALK_ANIM_SPEED, 6f, 6f));
    assertEquals(101, VelocityModeChanger.scaleAnimationSpeed(
        VelocityModeChanger.PLAYER_RUN_ANIM_SPEED, 9f, 9f));
  }

  @Test
  void scalesAnimationRateWithActualMovementSpeed() {
    assertEquals(107, VelocityModeChanger.scaleAnimationSpeed(213, 3f, 6f));
    assertEquals(202, VelocityModeChanger.scaleAnimationSpeed(101, 18f, 9f));
  }

  @Test
  void safelyHandlesStoppedOrInvalidVelocity() {
    assertEquals(0, VelocityModeChanger.scaleAnimationSpeed(213, 0f, 6f));
    assertEquals(0, VelocityModeChanger.scaleAnimationSpeed(213, 6f, 0f));
    assertEquals(0, VelocityModeChanger.scaleAnimationSpeed(0, 6f, 6f));
  }
}
