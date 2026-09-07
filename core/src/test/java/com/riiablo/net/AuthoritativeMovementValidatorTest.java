package com.riiablo.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AuthoritativeMovementValidatorTest {
  @Test
  void acceptsPacketLossSizedMovement() {
    assertEquals(AuthoritativeMovementValidator.Result.ACCEPTED,
        AuthoritativeMovementValidator.validate(
            0f, 0f, 0.8f, 0f, 5f, 0f, 5f, 4L, null));
  }

  @Test
  void rejectsInvalidSpeedDistanceAndCollision() {
    assertEquals(AuthoritativeMovementValidator.Result.INVALID_NUMBER,
        AuthoritativeMovementValidator.validate(
            0f, 0f, Float.NaN, 0f, 0f, 0f, 5f, 1L, null));
    assertEquals(AuthoritativeMovementValidator.Result.EXCESSIVE_SPEED,
        AuthoritativeMovementValidator.validate(
            0f, 0f, 0f, 0f, 20f, 0f, 5f, 1L, null));
    assertEquals(AuthoritativeMovementValidator.Result.EXCESSIVE_DISPLACEMENT,
        AuthoritativeMovementValidator.validate(
            0f, 0f, 5f, 0f, 5f, 0f, 5f, 1L, null));
    assertEquals(AuthoritativeMovementValidator.Result.COLLISION,
        AuthoritativeMovementValidator.validate(
            0f, 0f, 0.2f, 0f, 5f, 0f, 5f, 1L,
            (fromX, fromY, toX, toY) -> true));
  }
}
