package com.riiablo.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.Client;
import org.junit.jupiter.api.Test;

class DisplayOptionsTest {
  @Test
  void fpsModeCyclesThroughSupportedPositions() {
    byte mode = Client.FPS_NONE;
    assertEquals("OFF", DisplayOptions.fpsModeLabel(mode));
    mode = DisplayOptions.nextFpsMode(mode);
    assertEquals(Client.FPS_TOPLEFT, mode);
    mode = DisplayOptions.nextFpsMode(Client.FPS_BOTTOMRIGHT);
    assertEquals(Client.FPS_NONE, mode);
  }

  @Test
  void invalidFpsModeFallsBackToOff() {
    assertEquals(Client.FPS_NONE, DisplayOptions.normalizeFpsMode((byte) -1));
    assertEquals("OFF", DisplayOptions.fpsModeLabel((byte) 99));
  }
}
