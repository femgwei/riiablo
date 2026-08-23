package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.DrlgExport;
import com.d2moo.common.drlg.DrlgTileExporter;
import com.riiablo.drlg.TileGrid;
import com.riiablo.map.DT1;
import com.riiablo.map.Orientation;

class D2MooTileApplierTest {
  private static final int LEVEL_ID = 2;

  @Test
  void storesFloorWallsAndShadowInRiiabloLayerLayout() {
    TileGrid grid = new TileGrid(3, 2);
    D2MooTileApplier applier = new D2MooTileApplier();
    applier.putGrid(LEVEL_ID, grid);

    applier.onTile(LEVEL_ID, DrlgExport.LAYER_FLOOR, 1, 0,
        pack(Orientation.FLOOR, 5, 7));
    applier.onTile(LEVEL_ID, DrlgExport.LAYER_WALL, 1, 0,
        pack(Orientation.RIGHT_NORTH_CORNER_WALL, 8, 9));
    applier.onTile(LEVEL_ID, DrlgExport.LAYER_WALL, 1, 0,
        pack(Orientation.RIGHT_NORTH_CORNER_WALL, 8, 9));
    applier.onTile(LEVEL_ID, DrlgExport.LAYER_WALL, 1, 0,
        pack(Orientation.LEFT_NORTH_CORNER_WALL, 8, 9));
    applier.onTile(LEVEL_ID, DrlgExport.LAYER_SHADOW, 1, 0,
        pack(Orientation.SHADOW, 3, 4));

    assertEquals(index(Orientation.FLOOR, 5, 7), grid.floorIds[0][1]);
    assertEquals(true, grid.exportedFloorCells[0][1]);
    assertEquals(false, grid.exportedFloorCells[0][0]);
    assertEquals(index(Orientation.RIGHT_NORTH_CORNER_WALL, 8, 9), grid.wallIds[0][0][1]);
    assertEquals(index(Orientation.LEFT_NORTH_CORNER_WALL, 8, 9), grid.wallIds[1][0][1]);
    assertEquals(-1, grid.wallIds[2][0][1]);
    assertEquals(index(Orientation.SHADOW, 3, 4), grid.shadowIds[0][1]);
    assertEquals(1, applier.getLastExportedFloorCount());
    assertEquals(2, applier.getExportedWallCount());
    assertEquals(1, applier.getDuplicateWallCount());
    assertEquals(1, applier.getExportedShadowCount());
    assertEquals(0, applier.getIgnoredLayerCount());
    assertEquals(0, applier.getNonFloorOrientationCount());
    assertEquals(0, applier.getNonWallOrientationCount());
    assertEquals(0, applier.getNonShadowOrientationCount());
  }

  @Test
  void retainsHiddenWarpMarkersForInteractionWithoutLosingVisibilityState() {
    TileGrid grid = new TileGrid(1, 1);
    D2MooTileApplier applier = new D2MooTileApplier();
    applier.putGrid(LEVEL_ID, grid);

    applier.onTile(LEVEL_ID, DrlgExport.LAYER_WALL, 0, 0,
        pack(Orientation.SPECIAL_10, 6, 24), DrlgTileExporter.FLAG_HIDDEN);

    assertEquals(index(Orientation.SPECIAL_10, 6, 24), grid.wallIds[0][0][0]);
    assertEquals(true, grid.hiddenWallCells[0][0][0]);
  }

  @Test
  void reportsOverflowDuplicatesInvalidOrientationAndCoordinates() {
    TileGrid grid = new TileGrid(1, 1);
    D2MooTileApplier applier = new D2MooTileApplier();
    applier.putGrid(LEVEL_ID, grid);

    for (int i = 0; i < TileGrid.MAX_WALL_LAYERS + 1; i++) {
      applier.onTile(LEVEL_ID, DrlgExport.LAYER_WALL, 0, 0,
          pack(Orientation.LEFT_WALL, 1, i));
    }
    applier.onTile(LEVEL_ID, DrlgExport.LAYER_SHADOW, 0, 0,
        pack(Orientation.SHADOW, 2, 1));
    applier.onTile(LEVEL_ID, DrlgExport.LAYER_SHADOW, 0, 0,
        pack(Orientation.FLOOR, 2, 2));
    applier.onTile(LEVEL_ID, DrlgExport.LAYER_FLOOR, 2, 0,
        pack(Orientation.FLOOR, 1, 1));
    applier.onTile(LEVEL_ID, 99, 0, 0, pack(Orientation.FLOOR, 1, 1));

    assertEquals(TileGrid.MAX_WALL_LAYERS, applier.getExportedWallCount());
    assertEquals(1, applier.getWallLayerOverflowCount());
    assertEquals(2, applier.getExportedShadowCount());
    assertEquals(1, applier.getDuplicateShadowCount());
    assertEquals(1, applier.getNonShadowOrientationCount());
    assertEquals(1, applier.getOutOfBoundsCount());
    assertEquals(1, applier.getIgnoredLayerCount());
  }

  @Test
  void clipsNativeSharedRoomBoundaryWithoutHidingTrueOutOfBoundsTiles() {
    TileGrid grid = new TileGrid(3, 2);
    D2MooTileApplier applier = new D2MooTileApplier();
    applier.putGrid(LEVEL_ID, grid);

    applier.onTile(LEVEL_ID, DrlgExport.LAYER_FLOOR, 1, 2,
        pack(Orientation.FLOOR, 1, 1));
    applier.onTile(LEVEL_ID, DrlgExport.LAYER_WALL, 3, 1,
        pack(Orientation.LEFT_WALL, 1, 1));
    applier.onTile(LEVEL_ID, DrlgExport.LAYER_FLOOR, 1, 3,
        pack(Orientation.FLOOR, 1, 1));
    applier.onTile(LEVEL_ID, DrlgExport.LAYER_FLOOR, -1, 0,
        pack(Orientation.FLOOR, 1, 1));

    assertEquals(2, applier.getClippedBoundaryCount());
    assertEquals(1, applier.getClippedBoundaryFloorCount());
    assertEquals(2, applier.getOutOfBoundsCount());
    assertEquals(0, applier.getLastExportedFloorCount());
    assertEquals(0, applier.getExportedWallCount());
  }

  @Test
  void clearExportedTileIdsPreservesDirtPathFlags() {
    TileGrid grid = new TileGrid(1, 1);
    grid.floorIds[0][0] = 1;
    grid.exportedFloorCells[0][0] = true;
    grid.wallIds[0][0][0] = 2;
    grid.hiddenWallCells[0][0][0] = true;
    grid.shadowIds[0][0] = 3;
    grid.dirtPathFlags[0][0] = true;

    grid.clearExportedTileIds();

    assertEquals(-1, grid.floorIds[0][0]);
    assertEquals(false, grid.exportedFloorCells[0][0]);
    assertEquals(-1, grid.wallIds[0][0][0]);
    assertEquals(false, grid.hiddenWallCells[0][0][0]);
    assertEquals(-1, grid.shadowIds[0][0]);
    assertEquals(true, grid.dirtPathFlags[0][0]);
  }

  private static int pack(int orientation, int style, int sequence) {
    return (orientation << 24) | (style << 12) | sequence;
  }

  private static int index(int orientation, int style, int sequence) {
    return DT1.Tile.Index.create(orientation, style, sequence);
  }
}
