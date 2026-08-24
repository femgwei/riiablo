package com.d2moo.common.dungeon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgCoord;
import com.d2moo.common.drlg.D2DrlgCoords;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgRoomTilesStrc;

import org.junit.jupiter.api.Test;

class DungeonActiveRoomTest {
  @Test
  void allocRoomPreservesNativeStructureAndCoordinateCopy() {
    D2DrlgAct act = new D2DrlgAct();
    D2DrlgRoom drlgRoom = room(2, 3, 4, 5);
    drlgRoom.setPpRoomsNear(new D2DrlgRoom[] {drlgRoom});
    drlgRoom.setNRoomsNear(1);
    D2DrlgCoords coords = coords(2, 3, 4, 5);
    D2DrlgRoomTilesStrc tiles = new D2DrlgRoomTilesStrc();

    D2ActiveRoom room = Dungeon.allocRoom(act, drlgRoom, coords, tiles, 0x12345678, 4);

    assertSame(room, act.getRoom());
    assertSame(act, room.getAct());
    assertSame(tiles, room.getPRoomTiles());
    assertSame(drlgRoom, Dungeon.getRoomExFromRoom(room));
    assertEquals(0x12345678, room.getSeed().getNLowSeed());
    assertEquals(666, room.getSeed().getNHighSeed());
    assertEquals(4, room.getDwFlags());
    assertEquals(10, room.getCoords().getNSubtileX());
    assertEquals(25, room.getCoords().getNSubtileHeight());
    assertEquals(1, room.getNNumRooms());
    assertSame(room, room.getPpRoomList()[0]);
    assertTrue(act.isHasPendingRoomsUpdates());

    coords.setNSubtileX(999);
    assertEquals(10, room.getCoords().getNSubtileX(), "native allocation copies coordinates");
  }

  @Test
  void resolvesActiveRoomAdjacencyAndCoordinateLookups() {
    D2DrlgAct act = new D2DrlgAct();
    D2DrlgRoom firstDrlg = room(0, 0, 5, 5);
    D2DrlgRoom secondDrlg = room(5, 0, 5, 5);
    D2DrlgRoom[] near = {firstDrlg, secondDrlg};
    firstDrlg.setPpRoomsNear(near);
    firstDrlg.setNRoomsNear(2);
    secondDrlg.setPpRoomsNear(near);
    secondDrlg.setNRoomsNear(2);

    D2ActiveRoom first = Dungeon.allocRoom(
        act, firstDrlg, coords(0, 0, 5, 5), new D2DrlgRoomTilesStrc(), 1, 0);
    D2ActiveRoom second = Dungeon.allocRoom(
        act, secondDrlg, coords(5, 0, 5, 5), new D2DrlgRoomTilesStrc(), 2, 0);

    assertArrayEquals(new D2ActiveRoom[] {first, second},
        Dungeon.getAdjacentRoomsListFromRoom(first));
    assertSame(second, Dungeon.getAdjacentRoomByTileCoordinates(first, 6, 2));
    assertSame(second, Dungeon.getRoomAtPosition(first, 30, 10));
    assertSame(first, Dungeon.findRoomByTileCoordinates(act, 2, 2));
    assertSame(second, Dungeon.findRoomBySubtileCoordinates(act, 30, 10));

    D2DrlgCoords copy = Dungeon.getRoomCoordinates(second);
    copy.setNSubtileX(999);
    assertEquals(25, second.getCoords().getNSubtileX());
  }

  @Test
  void removalUnlinksOnlyActiveRoomAndPreservesDrlgTopology() {
    D2DrlgAct act = new D2DrlgAct();
    D2DrlgLevel level = new D2DrlgLevel();
    D2DrlgRoom firstDrlg = room(0, 0, 5, 5);
    D2DrlgRoom secondDrlg = room(5, 0, 5, 5);
    firstDrlg.setLevel(level);
    secondDrlg.setLevel(level);
    level.setFirstRoomEx(firstDrlg);
    firstDrlg.setDrlgRoomNext(secondDrlg);
    D2DrlgRoom[] near = {firstDrlg, secondDrlg};
    firstDrlg.setPpRoomsNear(near);
    firstDrlg.setNRoomsNear(2);
    secondDrlg.setPpRoomsNear(near);
    secondDrlg.setNRoomsNear(2);

    D2ActiveRoom first = Dungeon.allocRoom(
        act, firstDrlg, coords(0, 0, 5, 5), null, 1, 0);
    D2ActiveRoom second = Dungeon.allocRoom(
        act, secondDrlg, coords(5, 0, 5, 5), null, 2, 0);
    assertSame(second, act.getRoom());

    Dungeon.removeRoomFromAct(secondDrlg);

    assertSame(first, act.getRoom());
    assertEquals(1, first.getNNumRooms());
    assertSame(first, first.getPpRoomList()[0]);
    assertNull(secondDrlg.getRoom());
    assertSame(firstDrlg, level.getFirstRoomEx());
    assertSame(secondDrlg, firstDrlg.getDrlgRoomNext(),
        "active room removal must not delete generated DRLG topology");
  }

  private static D2DrlgRoom room(int x, int y, int width, int height) {
    D2DrlgCoord coord = new D2DrlgCoord();
    coord.setNTileXPos(x);
    coord.setNTileYPos(y);
    coord.setNTileWidth(width);
    coord.setNTileHeight(height);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setDrlgCoord(coord);
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
