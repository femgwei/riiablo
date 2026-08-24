package com.d2moo.common.dungeon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.d2moo.common.datatbls.D2LevelDefBin;
import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgCoord;
import com.d2moo.common.drlg.D2DrlgCoords;
import com.d2moo.common.drlg.D2DrlgLogicalRoomInfo;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2RoomCoordListStrc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DungeonPortalCoordQueryTest {
  @AfterEach
  void clearTables() {
    DataTbls.unloadLevelDefsBin();
  }

  @Test
  void convertsPortalFlagsAndLevelIdsInNativeTableOrder() {
    DataTbls.setLevelDefBinCache(new D2LevelDefBin[] {
        levelDef(1, false), levelDef(8, true), levelDef(40, true), levelDef(3, false)
    });

    assertArrayEquals(new int[] {8, 40},
        Dungeon.getPortalLevelArrayFromPortalFlags(0b11));
    assertArrayEquals(new int[] {40},
        Dungeon.getPortalLevelArrayFromPortalFlags(0b10));
    assertArrayEquals(new int[0], Dungeon.getPortalLevelArrayFromPortalFlags(0));
    assertEquals(1, Dungeon.getPortalFlagFromLevelId(8));
    assertEquals(2, Dungeon.getPortalFlagFromLevelId(40));
    assertEquals(0, Dungeon.getPortalFlagFromLevelId(99));
  }

  @Test
  void queriesCurrentAndAdjacentRoomCoordinateListsWithoutMutation() {
    D2DrlgAct act = new D2DrlgAct();
    D2DrlgRoom firstDrlg = room(0, 0, 5, 5, 11);
    D2DrlgRoom secondDrlg = room(5, 0, 5, 5, 22);
    D2DrlgRoom[] near = {firstDrlg, secondDrlg};
    firstDrlg.setPpRoomsNear(near);
    firstDrlg.setNRoomsNear(2);
    secondDrlg.setPpRoomsNear(near);
    secondDrlg.setNRoomsNear(2);

    D2ActiveRoom first = Dungeon.allocRoom(
        act, firstDrlg, coords(0, 0, 5, 5), null, 1, 0);
    Dungeon.allocRoom(act, secondDrlg, coords(5, 0, 5, 5), null, 2, 0);

    D2RoomCoordListStrc firstList = firstDrlg.getLogicalRoomInfo().getPCoordList();
    assertEquals(11, Dungeon.getRoomCoordListIndex(first, 10, 10));
    assertEquals(22, Dungeon.getRoomCoordListIndex(first, 30, 10));
    assertEquals(0, Dungeon.getRoomCoordListIndex(first, 60, 10));
    assertEquals(0, Dungeon.getRoomCoordListIndex(null, 10, 10));
    assertSame(firstList, Dungeon.getRoomCoordListAt(first, 10, 10));
    assertSame(firstList, Dungeon.getRoomCoordList(first));
    assertNull(Dungeon.getRoomCoordListAt(null, 10, 10));
    assertNull(Dungeon.getRoomCoordList(null));
    assertEquals(11, firstList.getNIndex(), "queries must not mutate the coordinate list");
  }

  private static D2LevelDefBin levelDef(int levelId, boolean portal) {
    D2LevelDefBin levelDef = new D2LevelDefBin();
    levelDef.setDwLevelId(levelId);
    levelDef.setDwPortal(portal ? 1 : 0);
    return levelDef;
  }

  private static D2DrlgRoom room(int x, int y, int width, int height, int listIndex) {
    D2DrlgCoord coord = new D2DrlgCoord();
    coord.setNTileXPos(x);
    coord.setNTileYPos(y);
    coord.setNTileWidth(width);
    coord.setNTileHeight(height);
    D2RoomCoordListStrc coordList = new D2RoomCoordListStrc();
    coordList.setNIndex(listIndex);
    D2DrlgLogicalRoomInfo logicalInfo = new D2DrlgLogicalRoomInfo();
    logicalInfo.setHasCoordList(true);
    logicalInfo.setPCoordList(coordList);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setDrlgCoord(coord);
    room.setLogicalRoomInfo(logicalInfo);
    return room;
  }

  private static D2DrlgCoords coords(int x, int y, int width, int height) {
    D2DrlgCoords coords = new D2DrlgCoords();
    coords.setNTileXPos(x);
    coords.setNTileYPos(y);
    coords.setNTileWidth(width);
    coords.setNTileHeight(height);
    coords.setNSubtileX(x * 5);
    coords.setNSubtileY(y * 5);
    coords.setNSubtileWidth(width * 5);
    coords.setNSubtileHeight(height * 5);
    return coords;
  }
}
