package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.riiablo.codec.Animation;

class GameScreenDeltaTest {
  @Test
  void preservesNormalFrameDelta() {
    assertEquals(1f / 60f, GameScreen.sanitizeSimulationDelta(1f / 60f));
    assertEquals(GameScreen.BACKGROUND_DELTA_THRESHOLD,
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
}
