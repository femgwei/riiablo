package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.riiablo.engine.Engine;
import com.riiablo.map.Material;
import org.junit.jupiter.api.Test;

class FootstepEmitterTest {
  @Test
  void walkModesUseWalkSoundGroup() {
    assertEquals("light_walk_dirt_1",
        FootstepEmitter.sound(Engine.Player.MODE_WL, Material.DIRT));
    assertEquals("light_walk_wood_1",
        FootstepEmitter.sound(Engine.Player.MODE_TW, Material.WOOD));
  }

  @Test
  void runModeUsesIndependentRunSoundGroup() {
    assertEquals("light_run_snow_1",
        FootstepEmitter.sound(Engine.Player.MODE_RN, Material.SNOW));
  }

  @Test
  void nonMovementModesDoNotEmitFootsteps() {
    assertNull(FootstepEmitter.sound(Engine.Player.MODE_NU, Material.DIRT));
    assertNull(FootstepEmitter.sound(Engine.Player.MODE_A1, Material.DIRT));
  }
}
