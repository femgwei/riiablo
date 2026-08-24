package com.d2moo.common.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgCoords;
import com.d2moo.common.drlg.D2DrlgDeleteStrc;
import com.d2moo.common.drlg.D2DrlgFlags;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgRoomFlags;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2DrlgTileGrid;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.drlg.DrlgActivate;

import org.junit.jupiter.api.Test;

class DungeonRoomDeletionTest {
  @Test
  void storesNativeLifoDeletionRecordsAndMarksActPending() {
    RoomGraph graph = roomGraph(false);

    Dungeon.allocDrlgDelete(graph.activeRoom, 1, 101);
    Dungeon.allocDrlgDelete(graph.activeRoom, 2, 202);

    D2DrlgDeleteStrc head = Dungeon.getDrlgDeleteFromRoom(graph.activeRoom);
    assertEquals(2, head.getUnitType());
    assertEquals(202, head.getUnitGuid());
    assertEquals(1, head.getNext().getUnitType());
    assertEquals(101, head.getNext().getUnitGuid());
    assertTrue(graph.act.isHasPendingRoomDeletions());

    Dungeon.freeDrlgDelete(graph.activeRoom);

    assertNull(Dungeon.getDrlgDeleteFromRoom(graph.activeRoom));
    assertNull(head.getNext());
  }

  @Test
  void serverRemovalPreservesRegenerationMarkerAndCanRebuildRoom() {
    RoomGraph graph = roomGraph(false);
    D2ActiveRoom removedRoom = graph.activeRoom;
    removedRoom.setDwFlags(5);
    Dungeon.allocDrlgDelete(removedRoom, 1, 101);

    Dungeon.removeRoomFromAct(graph.act, removedRoom);

    assertNull(graph.act.getRoom());
    assertNull(graph.drlgRoom.getRoom());
    assertNull(graph.drlgRoom.getTileGrid());
    assertFalse((graph.drlgRoom.getFlags() & D2DrlgRoomFlags.HAS_ROOM) != 0);
    assertTrue((graph.drlgRoom.getFlags() & D2DrlgRoomFlags.ROOM_FREED_SRV) != 0);
    assertEquals(1, graph.drlgRoom.getOtherFlags());
    assertEquals(1, graph.drlg.getFreedRooms());
    assertNull(removedRoom.getPDrlgRoom());
    assertNull(removedRoom.getAct());
    assertNull(removedRoom.getPCollisionGrid());
    assertNull(removedRoom.getPDrlgDelete());

    graph.drlgRoom.setTileGrid(new D2DrlgTileGrid());
    DrlgActivate.roomEx_EnsureHasRoom(graph.drlgRoom, false);

    assertNotNull(graph.drlgRoom.getRoom());
    assertNotSame(removedRoom, graph.drlgRoom.getRoom());
    assertSame(graph.drlgRoom.getRoom(), graph.act.getRoom());
    assertTrue((graph.drlgRoom.getFlags() & D2DrlgRoomFlags.HAS_ROOM) != 0);
    assertTrue((graph.drlgRoom.getFlags() & D2DrlgRoomFlags.ROOM_FREED_SRV) != 0);
  }

  @Test
  void clientRemovalDoesNotSetServerRegenerationMarker() {
    RoomGraph graph = roomGraph(true);

    Dungeon.removeRoomFromAct(graph.act, graph.activeRoom);

    assertFalse((graph.drlgRoom.getFlags() & D2DrlgRoomFlags.HAS_ROOM) != 0);
    assertFalse((graph.drlgRoom.getFlags() & D2DrlgRoomFlags.ROOM_FREED_SRV) != 0);
    assertEquals(1, graph.drlg.getFreedRooms());
  }

  private static RoomGraph roomGraph(boolean client) {
    RoomGraph graph = new RoomGraph();
    graph.act = new D2DrlgAct();
    graph.act.setClient(client);
    graph.drlg = new D2DrlgStrc();
    graph.drlg.setAct(graph.act);
    graph.drlg.setFlags(client ? D2DrlgFlags.ONCLIENT : 0);
    graph.act.setDrlg(graph.drlg);

    D2DrlgLevel level = new D2DrlgLevel();
    level.setDrlg(graph.drlg);
    graph.drlgRoom = new D2DrlgRoom();
    graph.drlgRoom.setLevel(level);
    graph.drlgRoom.setType(-1);
    graph.drlgRoom.setFlags(D2DrlgRoomFlags.HAS_ROOM);
    graph.drlgRoom.setTileGrid(new D2DrlgTileGrid());
    graph.drlgRoom.setSeed(new D2Seed());
    graph.drlgRoom.setNTileXPos(10);
    graph.drlgRoom.setNTileYPos(20);
    graph.drlgRoom.setNTileWidth(2);
    graph.drlgRoom.setNTileHeight(2);
    graph.drlgRoom.setPpRoomsNear(new D2DrlgRoom[] {graph.drlgRoom});
    graph.drlgRoom.setNRoomsNear(1);
    level.setFirstRoomEx(graph.drlgRoom);
    graph.drlg.setLevel(level);

    D2DrlgCoords coords = new D2DrlgCoords();
    coords.setNTileXPos(10);
    coords.setNTileYPos(20);
    coords.setNTileWidth(2);
    coords.setNTileHeight(2);
    coords.setNSubtileX(50);
    coords.setNSubtileY(100);
    coords.setNSubtileWidth(10);
    coords.setNSubtileHeight(10);
    graph.activeRoom = Dungeon.allocRoom(
        graph.act, graph.drlgRoom, coords,
        graph.drlgRoom.getTileGrid().getPTiles(), 1, 0);
    return graph;
  }

  private static final class RoomGraph {
    D2DrlgAct act;
    D2DrlgStrc drlg;
    D2DrlgRoom drlgRoom;
    D2ActiveRoom activeRoom;
  }
}
