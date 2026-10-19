package com.riiablo.map;

import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    assertTrue(RenderSystem.nativePopOccludes(grid, 0, 2, 2, 4, 0));
    assertFalse(RenderSystem.nativePopOccludes(grid, -1, 2, 2, 4, 0));
    assertFalse(RenderSystem.nativePopOccludes(grid, 0, 7, 7, 4, 0));
    assertFalse(RenderSystem.nativePopOccludes(grid, 0, 2, 2, 5, 0));
    assertFalse(RenderSystem.nativePopOccludes(grid, 0, 2, 2, 4, 0x100));
    assertFalse(RenderSystem.nativePopOccludes(grid, 0, 2, 2, 4, 0x200));
    assertFalse(RenderSystem.roofContextOccludes(grid, 7, 7, -1, 2, 2));
  }

  @Test
  void allRectanglesOfSelectedIndexSwitchTogether() {
    TileGrid grid = new TileGrid(12, 12);
    grid.nativePops.add(new TileGrid.NativePop(2, 2, 2, 2, 0, 1, 4));
    grid.nativePops.add(new TileGrid.NativePop(7, 7, 2, 2, 0, 1, 5));
    grid.nativePops.add(new TileGrid.NativePop(0, 7, 2, 2, 0, 2, 6));
    assertTrue(RenderSystem.nativePopOccludes(grid, 0, 7, 7, 5, 0));
    assertTrue(RenderSystem.nativePopOccludes(grid, 1, 2, 2, 4, 0));
    assertFalse(RenderSystem.nativePopOccludes(grid, 0, 0, 7, 6, 0));
    assertFalse(RenderSystem.nativePopOccludes(grid, 2, 7, 7, 5, 0));
  }

  @Test
  void foregroundWallFadeDoesNotLeaveOpaqueRoofTrimOrFadeExteriorTiles() {
    TileGrid grid = new TileGrid(12, 12);
    grid.nativePops.add(new TileGrid.NativePop(3, 1, 5, 8, 0, 1, 2));
    assertEquals(0f, RenderSystem.nativePopAlpha(grid, 0, 4, 3, 2, 0x8001, false));
    assertEquals(RenderSystem.WALL_OCCLUDED_ALPHA,
        RenderSystem.nativePopAlpha(grid, 0, 8, 3, 4, 0x4005, false));
    assertEquals(RenderSystem.WALL_OCCLUDED_ALPHA,
        RenderSystem.nativePopAlpha(grid, 0, 8, 3, 4, 0x4001, false));
    assertEquals(1f, RenderSystem.nativePopAlpha(grid, -1, 8, 3, 4, 0x4005, false));
    assertEquals(1f, RenderSystem.nativePopAlpha(grid, 0, 10, 3, 4, 0x4005, false));
    assertEquals(1f, RenderSystem.nativePopAlpha(grid, 0, 8, 3, 4, 0x8005, false));
    assertEquals(1f, RenderSystem.nativePopAlpha(grid, 0, 8, 3, 4, 0x4105, false));
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
