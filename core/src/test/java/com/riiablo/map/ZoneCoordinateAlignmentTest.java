package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

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
  public void boundaryWallOwnerWinsOverAdjacentZoneAtSharedEdge() throws IOException {
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

    assertSame(nativeZone, map.getZone(10760 + 32 * 5, -760 + 40 * 5));
    assertEquals(Orientation.LEFT_WALL,
        nativeZone.get(Map.WALL_OFFSET, nativeZone.tx + 32, nativeZone.ty + 40).orientation);
    assertEquals(0, map.flags(10760 + 32 * 5, -760 + 40 * 5));
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
