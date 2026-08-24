package com.d2moo.common.drlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DrlgDrlgLogicParityTest {
  @Test
  void nonLogicalRoomGetsOneNativeFullRoomCoordinateList() {
    D2DrlgRoom room = room(10, 20, 8, 6);

    DrlgDrlgLogic.allocCoordLists(room);

    D2DrlgLogicalRoomInfo info = room.getLogicalRoomInfo();
    assertNotNull(info);
    assertTrue(info.hasCoordList());
    assertEquals(1, info.getNLists());
    assertEquals(1, room.getLevel().getCoordLists());
    D2RoomCoordListStrc list = info.getPCoordList();
    assertEquals(1, list.getNIndex());
    assertBox(list.getPBox(0), 10, 20, 18, 26);
    assertBox(list.getPBox(1), 10, 20, 18, 26);
    assertSame(list, DrlgDrlgLogic.sub_6FD77110(room, 50, 100));
  }

  @Test
  void gridBackedLookupUsesExactCellReferenceInsteadOfHashOrBoundingGuess() {
    D2DrlgRoom room = room(10, 20, 3, 2);
    D2DrlgLogicalRoomInfo info = new D2DrlgLogicalRoomInfo();
    room.setLogicalRoomInfo(info);
    DrlgDrlgGrid.initializeGridCells(null, info.getPIndexX(), 4, 3);
    for (int y = 0; y < 3; y++) {
      for (int x = 0; x < 4; x++) {
        int index = x < 2 ? 11 : 22;
        DrlgDrlgGrid.alterGridFlag(info.getPIndexX(), x, y, 0x10000000 | index,
            DrlgDrlgGrid.FlagOperation.OVERWRITE);
      }
    }

    DrlgDrlgLogic.assignCoordListsForGrids(room, info, 1);

    D2RoomCoordListStrc left = DrlgDrlgLogic.sub_6FD77110(room, 10 * 5, 20 * 5);
    D2RoomCoordListStrc right = DrlgDrlgLogic.sub_6FD77110(room, 12 * 5, 20 * 5);
    assertNotNull(left);
    assertNotNull(right);
    assertNotSame(left, right);
    assertEquals(11, left.getNIndex());
    assertEquals(22, right.getNIndex());
    assertSame(left, DrlgDrlgLogic.sub_6FD77110(room, 11 * 5, 21 * 5));
    assertSame(right, DrlgDrlgLogic.sub_6FD77110(room, 13 * 5, 21 * 5));
  }

  @Test
  void layerAndObjectWallFlagsUseNativeMapTileBitPositions() {
    D2DrlgTileDataStrc tile = new D2DrlgTileDataStrc();
    tile.setNTileType(DrlgRoomTile.TILETYPE_WALL_LEFT);
    tile.setDwFlags(2 << 14); // stored wall layer is logical layer + 1
    assertTrue(DrlgDrlgLogic.checkLayer1ButNotWallObject(tile));

    tile.setDwFlags((2 << 14) | 0x000800);
    assertFalse(DrlgDrlgLogic.checkLayer1ButNotWallObject(tile));

    tile.setDwFlags(1 << 14); // logical layer 0
    assertFalse(DrlgDrlgLogic.checkLayer1ButNotWallObject(tile));
  }

  private static D2DrlgRoom room(int x, int y, int width, int height) {
    D2DrlgStrc drlg = new D2DrlgStrc();
    D2DrlgLevel level = new D2DrlgLevel();
    level.setDrlg(drlg);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setLevel(level);
    room.setNTileXPos(x);
    room.setNTileYPos(y);
    room.setNTileWidth(width);
    room.setNTileHeight(height);
    return room;
  }

  private static void assertBox(
      D2DrlgCoord box, int x, int y, int right, int bottom) {
    assertEquals(x, box.getNPosX());
    assertEquals(y, box.getNPosY());
    assertEquals(right, box.getNWidth());
    assertEquals(bottom, box.getNHeight());
  }
}
