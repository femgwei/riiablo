package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.utils.IntMap;
import com.riiablo.drlg.TileGrid;

class Act1MapBuilderD2MooLayersTest {
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
