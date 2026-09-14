package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import com.riiablo.map.RenderSystem;
import org.junit.jupiter.api.Test;

class AutomapViewportTest {
  @Test
  void fullScreenUsesEntireFrameExceptMargin() {
    Rectangle viewport = AutomapViewport.calculate(
        RenderSystem.AUTOMAP_MODE_CENTER, 800, 600, new Rectangle());
    assertEquals(12f, viewport.x);
    assertEquals(12f, viewport.y);
    assertEquals(776f, viewport.width);
    assertEquals(576f, viewport.height);
  }

  @Test
  void miniMapsUseHalfSizeAndRequestedTopCorner() {
    Rectangle left = AutomapViewport.calculate(
        RenderSystem.AUTOMAP_MODE_TOP_LEFT, 800, 600, new Rectangle());
    assertEquals(12f, left.x);
    assertEquals(288f, left.y);
    assertEquals(400f, left.width);
    assertEquals(300f, left.height);

    Rectangle right = AutomapViewport.calculate(
        RenderSystem.AUTOMAP_MODE_TOP_RIGHT, 800, 600, new Rectangle());
    assertEquals(388f, right.x);
    assertEquals(288f, right.y);
    assertEquals(400f, right.width);
    assertEquals(300f, right.height);
  }

  @Test
  void tinyOffscreenSurfaceStillHasDrawableArea() {
    Rectangle viewport = AutomapViewport.calculate(
        RenderSystem.AUTOMAP_MODE_CENTER, 1, 1, new Rectangle());
    assertTrue(viewport.width > 0f);
    assertTrue(viewport.height > 0f);
    assertTrue(viewport.x >= 0f && viewport.y >= 0f);
  }
}
