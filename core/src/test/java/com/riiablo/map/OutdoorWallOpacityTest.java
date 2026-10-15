package com.riiablo.map;

import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutdoorWallOpacityTest {
  @Test
  void outdoorLogicalGroupDoesNotFadeTreesAndBuildingExteriors() {
    TileGrid grid = new TileGrid(2, 2);
    assertFalse(RenderSystem.wallContextOccludes(grid, false, 7, 7, -1, 0, 0));
  }

  @Test
  void indoorGroupCutawayIsPreserved() {
    TileGrid grid = new TileGrid(2, 2);
    assertTrue(RenderSystem.wallContextOccludes(grid, true, 7, 7, -1, 0, 0));
    assertFalse(RenderSystem.wallContextOccludes(grid, true, 7, 8, -1, 0, 0));
  }
}
