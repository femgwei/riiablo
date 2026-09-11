package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AutomapLayerTest {
  @Test void roomRectangleRevealUsesWorldSubtiles() {
    AutomapLayer layer = new AutomapLayer(2);
    layer.revealRect(10, 20, 3, 2);
    assertTrue(layer.isExplored(10, 20));
    assertTrue(layer.isExplored(12, 21));
    assertFalse(layer.isExplored(13, 21));
    assertEquals(6, layer.getExploredCount());
  }

  @Test void invalidRoomDimensionsStillRevealOneCell() {
    AutomapLayer layer = new AutomapLayer(2);
    layer.revealRect(-4, -6, 0, -3);
    assertTrue(layer.isExplored(-4, -6));
    assertEquals(1, layer.getExploredCount());
  }
}
