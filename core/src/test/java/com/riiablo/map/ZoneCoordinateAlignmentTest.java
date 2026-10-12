package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import com.riiablo.drlg.TileGrid;

/** Signed native coordinates must align with automap tile projection. */
public class ZoneCoordinateAlignmentTest {
  @Test
  public void negativeSubtileOriginUsesFloorTileDivision() {
    Map.Zone zone = new Map.Zone();
    zone.setPosition(-1, -6);

    assertEquals(0, zone.getLocalTX(-1));
    assertEquals(1, zone.getLocalTX(0));
    assertEquals(0, zone.getLocalTY(-2));
    assertEquals(1, zone.getLocalTY(-1));
  }

  @Test
  public void adjacentZoneWinsOverBoundaryWallAtSharedEdge() throws IOException {
    Map map = new Map(0, 0);
    Map.Zone adjacent = new Map.Zone();
    adjacent.setPosition(10760, -560);
    adjacent.width = 320;
    adjacent.height = 90;
    adjacent.tilesX = 64;
    adjacent.tilesY = 18;

    Map.Zone nativeZone = new Map.Zone();
    nativeZone.setPosition(10760, -760);
    nativeZone.width = 280;
    nativeZone.height = 200;
    nativeZone.tilesX = 56;
    nativeZone.tilesY = 40;
    nativeZone.putBoundaryWall(Map.WALL_OFFSET, 32, 40,
        tile(Orientation.LEFT_WALL, 4, 0));
    adjacent.flags = new byte[adjacent.width * adjacent.height];

    // The adjacent zone is deliberately inserted first, matching the Act I
    // layout where Monastery Gate follows Outer Cloister's bottom edge.
    map.zones.add(adjacent);
    map.zones.add(nativeZone);

    assertSame(adjacent, map.getZone(10760 + 32 * 5, -760 + 40 * 5));
    assertSame(nativeZone, map.getRenderZone(10760 + 32 * 5, -760 + 40 * 5));
    assertTrue(map.areZonesAdjacent(adjacent, nativeZone));
    assertEquals(Orientation.LEFT_WALL,
        nativeZone.get(Map.WALL_OFFSET, nativeZone.tx + 32, nativeZone.ty + 40).orientation);
    assertEquals(0, map.flags(10760 + 32 * 5, -760 + 40 * 5));
  }

  @Test
  public void nativeWallTypeUsesWorldCoordinatesAndExactTileFallback() throws IOException {
    Map.Zone zone = new Map.Zone();
    zone.setPosition(100, 200);

    TileGrid grid = new TileGrid(4, 4);
    grid.wallTileTypes[1][2][3] = Orientation.RIGHT_WALL;
    zone.setNativeTileGrid(grid, 0);

    assertEquals(Orientation.RIGHT_WALL,
        zone.nativeWallTileType(1, zone.tx + 3, zone.ty + 2));

    DT1.Tile tile = tile(Orientation.LEFT_WALL, 8, 3);
    int tileId = DT1.Tile.Index.create(tile.orientation, tile.mainIndex, tile.subIndex);
    zone.putNativeWallTileTypeByTile(1, 2, tileId, Orientation.LEFT_WALL_DOOR);
    assertEquals(Orientation.LEFT_WALL_DOOR,
        zone.nativeWallTileType(0, zone.tx + 1, zone.ty + 2, tile));

    // Town DS1 graphics may use a different tile identity than the native
    // export.  A single native wall at the coordinate must still provide its
    // logical type when the exact tile-id match is unavailable.
    Map.Zone fallback = new Map.Zone();
    fallback.setPosition(100, 200);
    fallback.putNativeWallTileTypeByTile(1, 2, tileId, Orientation.RIGHT_WALL_DOOR);
    DT1.Tile differentTile = tile(Orientation.LEFT_WALL, 9, 4);
    assertEquals(Orientation.RIGHT_WALL_DOOR,
        fallback.nativeWallTileType(0, fallback.tx + 1, fallback.ty + 2, differentTile));

    Map.Zone sparse = new Map.Zone();
    sparse.setPosition(100, 200);
    sparse.putNativeWallTileType(2, 1, 2, Orientation.LOWER_RIGHT_WALL);
    assertEquals(Orientation.LOWER_RIGHT_WALL,
        sparse.nativeWallTileType(2, sparse.tx + 1, sparse.ty + 2));

    sparse.clearNativeWallTileTypes();
    assertEquals(-1, sparse.nativeWallTileType(2, sparse.tx + 1, sparse.ty + 2));
  }

  private static DT1.Tile tile(int orientation, int mainIndex, int subIndex)
      throws IOException {
    byte[] bytes = new byte[DT1.Tile.SIZE];
    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(20, orientation);
    buffer.putInt(24, mainIndex);
    buffer.putInt(28, subIndex);
    buffer.putInt(32, 1);
    return new DT1.Tile(new ByteArrayInputStream(bytes));
  }
}
