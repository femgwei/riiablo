package com.riiablo.map;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeFreeCoordinatesTest {

  @Test
  void searchesForACompleteMediumPlayerFootprint() {
    Map.Zone zone = zone(0, 0, 9, 9);
    zone.or(4, 4, DT1.Tile.FLAG_BLOCK_WALK);
    zone.or(3, 2, DT1.Tile.FLAG_BLOCK_WALK);

    Vector2 result = new Vector2();
    assertTrue(zone.findFreeCoordinates(
        new Vector2(4, 4), 2, 4, true, result));
    assertFalse(new Vector2(4, 4).equals(result));
    assertFootprintWalkable(zone, result, 2);
  }

  @Test
  void nativeRoomFootprintRejectsRectangularLevelVoid() {
    Map.Zone zone = zone(100, 200, 12, 8);
    Map.RoomEx room = zone.addRoomEx(104, 202, 4, 4);
    room.setAdjacentRoomIds(new int[0]);

    Vector2 result = new Vector2();
    assertFalse(zone.findFreeCoordinates(
        new Vector2(101, 201), 1, 2, true, result));
    assertTrue(zone.findFreeCoordinates(
        new Vector2(101, 201), 1, 5, true, result));
    assertTrue(room.contains(result.x, result.y));
  }

  @Test
  void neighborRoomSearchMatchesNativeAllowNeighborRoomsFlag() {
    Map.Zone zone = zone(0, 0, 10, 5);
    Map.RoomEx source = zone.addRoomEx(0, 0, 5, 5);
    Map.RoomEx neighbor = zone.addRoomEx(5, 0, 5, 5);
    source.setAdjacentRoomIds(new int[] {neighbor.id});
    neighbor.setAdjacentRoomIds(new int[] {source.id});
    for (int y = 0; y < 5; y++) {
      for (int x = 0; x < 5; x++) zone.or(x, y, DT1.Tile.FLAG_BLOCK_WALK);
    }

    Vector2 origin = new Vector2(2, 2);
    Vector2 result = new Vector2();
    assertFalse(zone.findFreeCoordinates(origin, 1, 5, false, result));
    assertTrue(zone.findFreeCoordinates(origin, 1, 5, true, result));
    assertEquals(neighbor, zone.findRoomEx(result.x, result.y));
  }

  @Test
  void neverReturnsCoordinatesOutsideZoneBounds() {
    Map.Zone zone = zone(30, 40, 3, 3);
    Arrays.fill(zone.flags, (byte) DT1.Tile.FLAG_BLOCK_WALK);
    Vector2 result = new Vector2();
    assertFalse(zone.findFreeCoordinates(
        new Vector2(29, 39), 1, 50, true, result));
  }

  @Test
  void callerMaskCanIncludeNativeNoPlayerCollision() {
    Map.Zone zone = zone(0, 0, 7, 7);
    zone.or(3, 3, DT1.Tile.FLAG_BLOCK_PLAYER_WALK);
    Vector2 result = new Vector2();

    assertTrue(zone.findFreeCoordinates(
        new Vector2(3, 3), 1, 0, true, result));
    assertEquals(new Vector2(3, 3), result);
    assertTrue(zone.findFreeCoordinates(
        new Vector2(3, 3), 1, 2,
        DT1.Tile.FLAG_BLOCK_WALK | DT1.Tile.FLAG_BLOCK_PLAYER_WALK,
        true, result));
    assertFalse(new Vector2(3, 3).equals(result));
  }

  private static Map.Zone zone(int x, int y, int width, int height) {
    Map.Zone zone = new Map.Zone();
    zone.x = x;
    zone.y = y;
    zone.width = width;
    zone.height = height;
    zone.tilesX = Math.max(1, width / DT1.Tile.SUBTILE_SIZE);
    zone.tilesY = Math.max(1, height / DT1.Tile.SUBTILE_SIZE);
    zone.flags = new byte[width * height];
    return zone;
  }

  private static void assertFootprintWalkable(Map.Zone zone, Vector2 position, int unitSize) {
    int radius = Math.max(0, unitSize - 1);
    for (int dy = -radius; dy <= radius; dy++) {
      for (int dx = -radius; dx <= radius; dx++) {
        int x = Math.round(position.x) + dx;
        int y = Math.round(position.y) + dy;
        assertTrue(zone.contains(x, y));
        assertEquals(0, zone.flags(x - zone.x, y - zone.y) & DT1.Tile.FLAG_BLOCK_WALK);
      }
    }
  }
}
