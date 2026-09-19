package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.codec.Animation;

class GameScreenDeltaTest {
  @Test
  void preservesNormalFrameDelta() {
    assertEquals(1f / 60f, GameScreen.sanitizeSimulationDelta(1f / 60f));
    assertEquals(0.1f, GameScreen.sanitizeSimulationDelta(0.1f));
    assertEquals(GameScreen.MAX_SIMULATION_DELTA,
        GameScreen.sanitizeSimulationDelta(GameScreen.MAX_SIMULATION_DELTA));
  }

  @Test
  void boundsVisibleCatchUpToAccumulatorCapacity() {
    assertEquals(GameScreen.MAX_SIMULATION_DELTA,
        GameScreen.sanitizeSimulationDelta(GameScreen.MAX_SIMULATION_DELTA + 0.001f));
    assertEquals(GameScreen.MAX_SIMULATION_DELTA,
        GameScreen.sanitizeSimulationDelta(GameScreen.BACKGROUND_DELTA_THRESHOLD));
  }

  @Test
  void discardsBackgroundAndInvalidDelta() {
    assertEquals(Animation.FRAME_DURATION,
        GameScreen.sanitizeSimulationDelta(GameScreen.BACKGROUND_DELTA_THRESHOLD + 0.001f));
    assertEquals(Animation.FRAME_DURATION,
        GameScreen.sanitizeSimulationDelta(5f));
    assertEquals(Animation.FRAME_DURATION,
        GameScreen.sanitizeSimulationDelta(Float.NaN));
    assertEquals(Animation.FRAME_DURATION,
        GameScreen.sanitizeSimulationDelta(-1f));
  }

  @Test
  void resumeDoesNotInventOrRetainElapsedTime() {
    assertEquals(1f / 60f,
        GameScreen.sanitizeResumedSimulationDelta(1f / 60f));
    assertEquals(Animation.FRAME_DURATION,
        GameScreen.sanitizeResumedSimulationDelta(0.1f));
    assertEquals(Animation.FRAME_DURATION,
        GameScreen.sanitizeResumedSimulationDelta(5f));
  }

  @Test
  void movementClosesOnlyPairedStashOrVendorPanels() {
    assertTrue(GameScreen.shouldCloseTradePanelsForMovement(true, true, false));
    assertTrue(GameScreen.shouldCloseTradePanelsForMovement(true, false, true));

    assertFalse(GameScreen.shouldCloseTradePanelsForMovement(true, false, false));
    assertFalse(GameScreen.shouldCloseTradePanelsForMovement(false, true, false));
    assertFalse(GameScreen.shouldCloseTradePanelsForMovement(false, false, true));
  }
}
