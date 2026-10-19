package com.riiablo.map;

import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutdoorWallOpacityTest {
  @Test
  void townRoofCutsAwayInItsOwnLogicalRegion() {
    TileGrid grid = new TileGrid(2, 2);
    assertTrue(RenderSystem.roofContextOccludes(grid, 7, 7, -1, 0, 0));
    assertFalse(RenderSystem.roofContextOccludes(grid, 7, 8, -1, 0, 0));
    assertFalse(RenderSystem.roofContextOccludes(grid, 0, 0, -1, 0, 0));
    assertFalse(RenderSystem.roofContextOccludes(grid, -1, -1, -1, 0, 0));
  }

  @Test
  void explicitPopCutsAwayRoofWithoutLogicalRegion() {
    TileGrid grid = new TileGrid(8, 8);
    grid.nativePops.add(new TileGrid.NativePop(2, 2, 2, 2, 0, 1, 4));
    assertTrue(RenderSystem.roofContextOccludes(grid, -1, -1, 0, 2, 2));
    assertFalse(RenderSystem.roofContextOccludes(grid, -1, -1, -1, 2, 2));
    assertFalse(RenderSystem.roofContextOccludes(grid, -1, -1, 0, 7, 7));
  }

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
