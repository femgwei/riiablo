package com.d2moo.common.dungeon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgCoords;

import org.junit.jupiter.api.Test;

class DungeonCoordinatesTest {
  @Test
  void roundTripsNativeTileProjection() {
    int[] x = {3};
    int[] y = {-2};

    Dungeon.gameTileToClientCoords(x, y);
    assertArrayEquals(new int[] {400}, x);
    assertArrayEquals(new int[] {40}, y);

    Dungeon.clientToGameTileCoords(x, y);
    assertArrayEquals(new int[] {3}, x);
    assertArrayEquals(new int[] {-2}, y);
  }

  @Test
  void roundTripsNativeSubtileProjection() {
    int[] x = {7};
    int[] y = {-3};

    Dungeon.gameSubtileToClientCoords(x, y);
    assertArrayEquals(new int[] {160}, x);
    assertArrayEquals(new int[] {32}, y);

    Dungeon.clientToGameSubtileCoords(x, y);
    assertArrayEquals(new int[] {7}, x);
    assertArrayEquals(new int[] {-3}, y);
  }

  @Test
  void roundTripsNativeUnscaledGameProjection() {
    int[] x = {20};
    int[] y = {-12};

    Dungeon.gameToClientCoords(x, y);
    assertArrayEquals(new int[] {16}, x);
    assertArrayEquals(new int[] {2}, y);

    Dungeon.clientToGameCoords(x, y);
    assertArrayEquals(new int[] {20}, x);
    assertArrayEquals(new int[] {-12}, y);
  }

  @Test
  void matchesNativeDrawPositionOffsetsAndNegativeDivision() {
    int[] x = {0};
    int[] y = {0};
    Dungeon.gameToClientTileDrawPositionCoords(3, 2, x, y);
    assertArrayEquals(new int[] {0}, x);
    assertArrayEquals(new int[] {280}, y);

    Dungeon.gameToClientSubtileDrawPositionCoords(3, 2, x, y);
    assertArrayEquals(new int[] {0}, x);
    assertArrayEquals(new int[] {56}, y);

    Dungeon.clientTileDrawPositionToGameCoords(320, 120, x, y);
    assertArrayEquals(new int[] {3}, x);
    assertArrayEquals(new int[] {-1}, y);

    Dungeon.clientSubtileDrawPositionToGameCoords(64, 24, x, y);
    assertArrayEquals(new int[] {3}, x);
    assertArrayEquals(new int[] {-1}, y);

    Dungeon.clientTileDrawPositionToGameCoords(160, 0, x, y);
    assertArrayEquals(new int[] {1}, x);
    assertArrayEquals(new int[] {-2}, y);

    Dungeon.clientSubtileDrawPositionToGameCoords(32, 0, x, y);
    assertArrayEquals(new int[] {1}, x);
    assertArrayEquals(new int[] {-2}, y);
  }

  @Test
  void convertsGameTilesToFiveByFiveSubtiles() {
    int[] x = {7};
    int[] y = {-3};
    Dungeon.gameTileToSubtileCoords(x, y);
    assertArrayEquals(new int[] {35}, x);
    assertArrayEquals(new int[] {-15}, y);
  }

  @Test
  void appliesNativeRoomEdgeRules() {
    D2ActiveRoom first = room(0, 0, 10, 10);
    D2ActiveRoom touching = room(10, 2, 5, 5);
    D2ActiveRoom separated = room(11, 2, 5, 5);

    assertTrue(Dungeon.doRoomsTouchOrOverlap(first, touching));
    assertFalse(Dungeon.doRoomsTouchOrOverlap(first, separated));
    assertTrue(Dungeon.areTileCoordinatesInsideRoom(first, 0, 0));
    assertTrue(Dungeon.areTileCoordinatesInsideRoom(first, 9, 9));
    assertFalse(Dungeon.areTileCoordinatesInsideRoom(first, 10, 9));
  }

  @Test
  void appliesNativeSubtileExclusiveMaximumEdges() {
    D2DrlgCoords coords = new D2DrlgCoords();
    coords.setNSubtileX(50);
    coords.setNSubtileY(75);
    coords.setNSubtileWidth(20);
    coords.setNSubtileHeight(10);

    assertTrue(Dungeon.areSubtileCoordinatesInsideRoom(coords, 50, 75));
    assertTrue(Dungeon.areSubtileCoordinatesInsideRoom(coords, 69, 84));
    assertFalse(Dungeon.areSubtileCoordinatesInsideRoom(coords, 70, 84));
    assertFalse(Dungeon.areSubtileCoordinatesInsideRoom(coords, 69, 85));
  }

  private static D2ActiveRoom room(int x, int y, int width, int height) {
    D2ActiveRoom room = new D2ActiveRoom();
    room.setNTileXPos(x);
    room.setNTileYPos(y);
    room.setNTileWidth(width);
    room.setNTileHeight(height);
    return room;
  }
}
