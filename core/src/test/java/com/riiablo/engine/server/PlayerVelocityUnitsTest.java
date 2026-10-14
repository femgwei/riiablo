package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.riiablo.engine.server.component.Velocity;
import org.junit.jupiter.api.Test;

class PlayerVelocityUnitsTest {
  @Test
  void playerSpeedMatchesNativeAxisStepAtTwentyFiveFramesPerSecond() {
    Velocity player = new Velocity().setPlayer(6, 9);
    assertEquals(9.375f, player.walkSpeed);
    assertEquals(14.0625f, player.runSpeed);
    // PATH_ComputeVelocityAndDirectionVectorsToNextPoint: (4096 * (6<<8)) >> 8.
    int nativeWalkStep = (4096 * (6 << 8)) >> 8;
    assertEquals(nativeWalkStep / 65536f, player.speed(false) / 25f);
    int nativeRunStep = (4096 * (9 << 8)) >> 8;
    assertEquals(nativeRunStep / 65536f, player.speed(true) / 25f);
  }

  @Test
  void respawnInitializationDoesNotAccumulateTheConversion() {
    Velocity player = new Velocity().setPlayer(6, 9);
    player.setPlayer(6, 9);
    assertEquals(9.375f, player.walkSpeed);
    assertEquals(14.0625f, player.runSpeed);
  }

  @Test
  void monsterAndExplicitWorldSpeedContractsAreUnchanged() {
    Velocity velocity = new Velocity().set(6, 9);
    assertEquals(6f, velocity.speed(false));
    velocity.setMonster(6);
    assertEquals(4.5f, velocity.speed(false));
  }

  @Test
  void monsterInitializationClearsPlayerRunningFormula() {
    Velocity velocity = new Velocity().setPlayer(6, 9);
    velocity.movementRunning = true;
    velocity.nativePlayerVelocityBonus = 50;
    velocity.setMonster(6);
    assertFalse(velocity.nativePlayerSpeed);
    assertFalse(velocity.movementRunning);
    assertEquals(0f, velocity.nativePlayerVelocityBonus);
    assertEquals(4.5f, velocity.speed(true) * velocity.movementMultiplier());
  }
}
