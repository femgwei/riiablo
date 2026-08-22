package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.utils.IntMap;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.DrlgExport;
import com.riiablo.codec.excel.Levels;
import com.riiablo.drlg.DrlgLevel;
import com.riiablo.drlg.TileGrid;

class Act1MapBuilderD2MooLayersTest {
  @Test
  void collectsBaseAndPresetRoomDt1MasksForExportedLevel() {
    D2DrlgStrc drlg = new D2DrlgStrc();
    D2DrlgLevel level = new D2DrlgLevel();
    level.setLevelId(3);
    drlg.setLevel(level);

    D2DrlgRoom outdoor = new D2DrlgRoom();
    outdoor.setDt1Mask(0x44103);
    D2DrlgRoom preset = new D2DrlgRoom();
    preset.setDt1Mask(0x10280000);
    outdoor.setDrlgRoomNext(preset);
    level.setFirstRoomEx(outdoor);

    assertEquals(0x102C4103, DrlgExport.collectLevelDt1Mask(drlg, 3));
  }

  @Test
  void appliesExportedFloorWallAndShadowLayersWithoutOverwritingEmptyCells() throws Exception {
    DT1.Tile floor = tile(Orientation.FLOOR, 1, 1);
    DT1.Tile wall0 = tile(Orientation.LEFT_WALL, 2, 1);
    DT1.Tile wall1 = tile(Orientation.RIGHT_NORTH_CORNER_WALL, 2, 2);
    DT1.Tile shadow = tile(Orientation.SHADOW, 3, 1);
    DT1.Tile preserved = tile(Orientation.FLOOR, 9, 9);

    DT1s dt1s = new DT1s();
    dt1s.add(floor);
    dt1s.add(wall0);
    dt1s.add(wall1);
    dt1s.add(shadow);

    TileGrid grid = new TileGrid(3, 2);
    grid.floorIds[0][0] = floor.id;
    grid.floorIds[0][2] = DT1.Tile.Index.create(Orientation.FLOOR, 99, 99);
    grid.wallIds[0][0][1] = wall0.id;
    grid.wallIds[1][1][0] = wall1.id;
    grid.shadowIds[1][1] = shadow.id;

    DT1.Tile[][] layers = new DT1.Tile[Map.MAX_LAYERS][];
    layers[Map.FLOOR_OFFSET] = new DT1.Tile[12];
    layers[Map.FLOOR_OFFSET][1] = preserved;
    layers[Map.FLOOR_OFFSET][2] = preserved;
    layers[Map.FLOOR_OFFSET][11] = preserved;
    IntMap<Integer> floorHistogram = new IntMap<>();

    Act1MapBuilderD2MOD.LayerApplyCounts counts =
        Act1MapBuilderD2MOD.applyTileGridLayers(
            grid, dt1s, layers, 4, 3, 2, floorHistogram);

    assertEquals(1, counts.floors);
    assertEquals(2, counts.walls);
    assertEquals(1, counts.shadows);
    assertEquals(1, counts.failedResolve);
    assertEquals(1, counts.failedFloors);
    assertEquals(0, counts.failedWalls);
    assertEquals(0, counts.failedShadows);
    assertEquals(1, floorHistogram.get(floor.id, 0));

    assertSame(floor, layers[Map.FLOOR_OFFSET][0]);
    assertSame(preserved, layers[Map.FLOOR_OFFSET][1]);
    assertSame(preserved, layers[Map.FLOOR_OFFSET][2]);
    assertSame(preserved, layers[Map.FLOOR_OFFSET][11]);
    assertSame(wall0, layers[Map.WALL_OFFSET][1]);
    assertSame(wall1, layers[Map.WALL_OFFSET + 1][4]);
    assertNull(layers[Map.WALL_OFFSET + 2]);
    assertSame(shadow, layers[Map.SHADOW_OFFSET][5]);
  }

  @Test
  void rebuildsCollisionFromFinalLayersAndPreservesOutsideRegion() throws Exception {
    DT1.Tile floor = tile(Orientation.FLOOR, 1, 1);
    DT1.Tile wall = tile(Orientation.LEFT_WALL, 2, 1);
    floor.flags[0] = DT1.Tile.FLAG_BLOCK_WALK;
    wall.flags[0] = DT1.Tile.FLAG_BLOCK_LIGHT;
    wall.flags[24] = DT1.Tile.FLAG_BLOCK_JUMP;

    DT1.Tile[][] layers = new DT1.Tile[Map.MAX_LAYERS][];
    layers[Map.FLOOR_OFFSET] = new DT1.Tile[] {floor, null};
    layers[Map.WALL_OFFSET] = new DT1.Tile[] {wall, null};

    byte[] flags = new byte[2 * DT1.Tile.SUBTILE_SIZE * DT1.Tile.SUBTILE_SIZE];
    Arrays.fill(flags, (byte) 0x7F);
    for (int y = 0; y < DT1.Tile.SUBTILE_SIZE; y++) {
      Arrays.fill(flags,
          y * 2 * DT1.Tile.SUBTILE_SIZE + DT1.Tile.SUBTILE_SIZE,
          (y + 1) * 2 * DT1.Tile.SUBTILE_SIZE,
          (byte) 0x55);
    }

    Act1MapBuilderD2MOD.CollisionApplyCounts counts =
        Act1MapBuilderD2MOD.rebuildTileCollisionFlags(
            layers, null, flags, 2, 1, 1, 1);

    assertEquals(2, counts.tiles);
    assertEquals(0, counts.siblingTiles);
    assertEquals(2, counts.blockedSubtiles);
    assertEquals(DT1.Tile.FLAG_BLOCK_WALK | DT1.Tile.FLAG_BLOCK_LIGHT, flags[40] & 0xFF);
    assertEquals(DT1.Tile.FLAG_BLOCK_JUMP, flags[4] & 0xFF);
    assertEquals(0, flags[0] & 0xFF);
    assertEquals(0x55, flags[5] & 0xFF);
    assertEquals(0x55, flags[49] & 0xFF);
  }

  @Test
  void leavesExportedVoidCellsUndrawnAndBlocksTheirCollision() throws Exception {
    DT1.Tile floor = tile(Orientation.FLOOR, 1, 1);
    TileGrid footprint = new TileGrid(2, 1);
    footprint.floorIds[0][0] = floor.id;
    footprint.exportedFloorCells[0][0] = true;

    DT1.Tile[][] layers = new DT1.Tile[Map.MAX_LAYERS][];
    layers[Map.FLOOR_OFFSET] = new DT1.Tile[] {floor, null};
    byte[] flags = new byte[2 * DT1.Tile.SUBTILE_SIZE * DT1.Tile.SUBTILE_SIZE];

    Act1MapBuilderD2MOD.CollisionApplyCounts counts =
        Act1MapBuilderD2MOD.rebuildTileCollisionFlags(
            footprint, layers, null, flags, 2, 1, 2, 1);

    assertEquals(1, counts.tiles);
    assertEquals(1, counts.voidTiles);
    assertEquals(25, counts.blockedSubtiles);
    assertEquals(0, flags[0] & 0xFF);
    assertEquals(DT1.Tile.FLAG_BLOCK_WALK, flags[5] & 0xFF);
    assertEquals(DT1.Tile.FLAG_BLOCK_WALK, flags[49] & 0xFF);
  }

  @Test
  void convertsGeneratorOffsetsAsZoneLocalTileCoordinates() {
    assertEquals(0, Act1MapBuilderD2MOD.toLocalGridCoordinate(0, 8));
    assertEquals(2, Act1MapBuilderD2MOD.toLocalGridCoordinate(16, 8));
    assertEquals(-1, Act1MapBuilderD2MOD.toLocalGridCoordinate(-1, 8));

    assertEquals(67, Act1MapBuilderD2MOD.toLocalTileIndex(3, 2, 32, 24));
    assertEquals(-1, Act1MapBuilderD2MOD.toLocalTileIndex(-1, 2, 32, 24));
    assertEquals(-1, Act1MapBuilderD2MOD.toLocalTileIndex(32, 2, 32, 24));
  }

  @Test
  void customDrlgLayoutDimensionsDriveBothExportGrids() {
    Levels.Entry entry = new Levels.Entry();
    entry.Id = 3;
    entry.SizeX = new int[] {80, 80, 80};
    entry.SizeY = new int[] {160, 160, 160};

    DrlgLevel level = new DrlgLevel(entry, 0, 160, 64);

    assertEquals(160, level.tilesX);
    assertEquals(64, level.tilesY);
    assertEquals(160, level.grid.width);
    assertEquals(64, level.grid.height);
    assertEquals(20, level.drlgGrid.gridWidth);
    assertEquals(8, level.drlgGrid.gridHeight);
  }

  @Test
  void repairsNorthTownSeamAlongBloodMoorBoundary() {
    TileGrid grid = seamGrid(7, 5, 4, 4, 0x1234);
    grid.wallIds[0][4][2] = 99;
    grid.shadowIds[4][2] = 98;

    Act1MapBuilderD2MOD.SeamRepairResult result =
        Act1MapBuilderD2MOD.repairTownSeam(grid, 1, 4, 1, 8);

    assertEquals(true, result.found);
    assertEquals(4, result.targetX);
    assertEquals(4, result.targetY);
    assertEquals(3, result.carved);
    assertSeamCell(grid, 1, 4, 0x1234);
    assertSeamCell(grid, 2, 4, 0x1234);
    assertSeamCell(grid, 3, 4, 0x1234);
    assertEquals(-1, grid.wallIds[0][4][2]);
    assertEquals(-1, grid.shadowIds[4][2]);
  }

  @Test
  void repairsSouthTownSeamInwardFromTopBoundary() {
    TileGrid grid = seamGrid(6, 5, 3, 2, 0x2345);

    Act1MapBuilderD2MOD.SeamRepairResult result =
        Act1MapBuilderD2MOD.repairTownSeam(grid, 3, 0, 3, 8);

    assertEquals(true, result.found);
    assertEquals(2, result.carved);
    assertSeamCell(grid, 3, 0, 0x2345);
    assertSeamCell(grid, 3, 1, 0x2345);
  }

  @Test
  void repairsEastTownSeamInwardFromLeftBoundary() {
    TileGrid grid = seamGrid(5, 6, 2, 1, 0x3456);

    Act1MapBuilderD2MOD.SeamRepairResult result =
        Act1MapBuilderD2MOD.repairTownSeam(grid, 0, 1, 2, 8);

    assertEquals(true, result.found);
    assertEquals(2, result.carved);
    assertSeamCell(grid, 0, 1, 0x3456);
    assertSeamCell(grid, 1, 1, 0x3456);
  }

  @Test
  void repairsWestTownSeamInwardFromRightBoundary() {
    TileGrid grid = seamGrid(5, 6, 2, 3, 0x4567);

    Act1MapBuilderD2MOD.SeamRepairResult result =
        Act1MapBuilderD2MOD.repairTownSeam(grid, 4, 3, 0, 8);

    assertEquals(true, result.found);
    assertEquals(2, result.carved);
    assertSeamCell(grid, 4, 3, 0x4567);
    assertSeamCell(grid, 3, 3, 0x4567);
  }

  @Test
  void leavesAlreadyConnectedTownSeamUnchanged() {
    TileGrid grid = seamGrid(5, 5, 2, 4, 0x5678);

    Act1MapBuilderD2MOD.SeamRepairResult result =
        Act1MapBuilderD2MOD.repairTownSeam(grid, 2, 4, 1, 8);

    assertEquals(true, result.found);
    assertEquals(0, result.carved);
    assertEquals(0x5678, grid.floorIds[4][2]);
  }

  @Test
  void fillsOnlyEmptyOutdoorRoomContainingTownExit() {
    TileGrid grid = new TileGrid(16, 8);
    for (int y = 0; y < 8; y++) {
      for (int x = 8; x < 16; x++) {
        grid.floorIds[y][x] = 0x7000 + y * 8 + x;
        grid.exportedFloorCells[y][x] = true;
      }
    }

    Act1MapBuilderD2MOD.SeamRepairResult result =
        Act1MapBuilderD2MOD.repairTownSeam(grid, 3, 7, 1, 8);

    assertEquals(true, result.found);
    assertEquals(8, result.targetX);
    assertEquals(7, result.targetY);
    assertEquals(5, result.carved);
    assertEquals(59, result.roomFill);
    for (int y = 0; y < 8; y++) {
      for (int x = 0; x < 8; x++) {
        assertEquals(true, grid.exportedFloorCells[y][x]);
        assertEquals(grid.floorIds[y][x + 8], grid.floorIds[y][x]);
      }
    }
    assertEquals(false, grid.dirtPathFlags[0][0]);
    assertEquals(true, grid.dirtPathFlags[7][3]);
    assertEquals(true, grid.dirtPathFlags[7][8]);
  }

  @Test
  void refusesToFillTownSeamBeyondOneRoomEx() {
    TileGrid grid = seamGrid(12, 1, 11, 0, 0x6789);

    Act1MapBuilderD2MOD.SeamRepairResult result =
        Act1MapBuilderD2MOD.repairTownSeam(grid, 0, 0, 2, 8);

    assertEquals(false, result.found);
    assertEquals(0, result.carved);
    assertEquals(false, grid.exportedFloorCells[0][0]);
  }

  private static TileGrid seamGrid(int width, int height, int floorX, int floorY, int floorId) {
    TileGrid grid = new TileGrid(width, height);
    grid.floorIds[floorY][floorX] = floorId;
    grid.exportedFloorCells[floorY][floorX] = true;
    return grid;
  }

  private static void assertSeamCell(TileGrid grid, int x, int y, int floorId) {
    assertEquals(true, grid.exportedFloorCells[y][x]);
    assertEquals(true, grid.dirtPathFlags[y][x]);
    assertEquals(floorId, grid.floorIds[y][x]);
  }

  private static DT1.Tile tile(int orientation, int mainIndex, int subIndex) throws IOException {
    byte[] bytes = new byte[DT1.Tile.SIZE];
    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(20, orientation);
    buffer.putInt(24, mainIndex);
    buffer.putInt(28, subIndex);
    buffer.putInt(32, 1);
    return new DT1.Tile(new ByteArrayInputStream(bytes));
  }
}
