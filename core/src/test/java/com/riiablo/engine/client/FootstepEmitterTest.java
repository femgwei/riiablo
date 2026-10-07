package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.engine.Engine;
import com.riiablo.map.Material;
import org.junit.jupiter.api.Test;

class FootstepEmitterTest {
  @Test
  void walkModesUseWalkSoundGroup() {
    assertEquals("light_walk_dirt_1",
        FootstepEmitter.sound(false, Material.DIRT));
    assertEquals("light_walk_wood_1",
        FootstepEmitter.sound(false, Material.WOOD));
  }

  @Test
  void runModeUsesIndependentRunSoundGroup() {
    assertEquals("light_run_snow_1",
        FootstepEmitter.sound(true, Material.SNOW));
  }

  @Test
  void actualSpeedCorrectsLaggingNetworkWalkMode() {
    assertTrue(FootstepEmitter.isRunning(Engine.Player.MODE_RN, 6f, 6f, 9f));
    assertTrue(FootstepEmitter.isRunning(Engine.Player.MODE_WL, 9f, 6f, 9f));
    assertFalse(FootstepEmitter.isRunning(Engine.Player.MODE_WL, 6f, 6f, 9f));
    assertFalse(FootstepEmitter.isRunning(Engine.Player.MODE_TW, 6f, 6f, 9f));
  }

  @Test
  void detectsFootPlantAcrossSkippedAndWrappedFrames() {
    assertTrue(FootstepEmitter.crossedFootstep(3, 5, 8));
    assertTrue(FootstepEmitter.crossedFootstep(7, 1, 8));
    assertFalse(FootstepEmitter.crossedFootstep(1, 3, 8));
    assertFalse(FootstepEmitter.crossedFootstep(4, 4, 8));
  }

  @Test
  void rejectsNonMovementModes() {
    assertFalse(FootstepEmitter.isMovementMode(Engine.Player.MODE_NU));
    assertFalse(FootstepEmitter.isMovementMode(Engine.Player.MODE_A1));
  }
}
