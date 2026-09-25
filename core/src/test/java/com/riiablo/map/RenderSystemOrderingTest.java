package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RenderSystemOrderingTest {
  @Test
  void groundItemsStayBelowWallsAndUnits() {
    assertEquals(1, RenderSystem.entityOrderFlag(false, 0, true));
    assertEquals(0, RenderSystem.entityOrderFlag(false, 2, false));
  }

  @Test
  void nativeObjectOrderFlagIsPreserved() {
    assertEquals(0, RenderSystem.entityOrderFlag(true, 0, false));
    assertEquals(1, RenderSystem.entityOrderFlag(true, 1, false));
    assertEquals(2, RenderSystem.entityOrderFlag(true, 2, false));
  }
}
