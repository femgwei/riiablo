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

  @Test void duplicateNativeCellsAreCollapsedPerLayer() {
    AutomapLayer layer = new AutomapLayer(2);
    layer.addFloor(11, -5, 7);
    layer.addFloor(11, -5, 7);
    layer.addWall(12, -5, 7);
    layer.addWall(12, -5, 7);
    layer.addObject(13, -5, 7);
    layer.addExtra(14, -5, 7);
    assertEquals(1, layer.floors.size);
    assertEquals(1, layer.walls.size);
    assertEquals(1, layer.objects.size);
    assertEquals(1, layer.extras.size);
  }
}
