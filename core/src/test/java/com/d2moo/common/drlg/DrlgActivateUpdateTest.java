package com.d2moo.common.drlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class DrlgActivateUpdateTest {
  @Test
  void allocatesOutOfSightRoomsIncrementallyFromCircularStatusList() {
    D2DrlgStrc drlg = new D2DrlgStrc();
    D2DrlgAct act = new D2DrlgAct();
    act.setDrlg(drlg);
    drlg.setAct(act);
    DrlgActivate.initializeRoomExStatusLists(drlg);

    D2DrlgLevel level = new D2DrlgLevel();
    level.setDrlg(drlg);
    D2DrlgRoom first = linkOutOfSightRoom(drlg, level, 10, 20);
    D2DrlgRoom second = linkOutOfSightRoom(drlg, level, 20, 20);

    drlg.setRoomsInitTimeout((byte) 1);
    DrlgActivate.update(drlg);

    assertNotNull(first.getRoom());
    assertNull(second.getRoom(), "one update may allocate only one room");
    assertSame(first, drlg.getDrlgRoom());
    assertEquals(1, drlg.getAllocatedRooms());
    assertEquals(7, Byte.toUnsignedInt(drlg.getRoomsInitTimeout()));

    drlg.setRoomsInitTimeout((byte) 1);
    DrlgActivate.update(drlg);

    assertNotNull(second.getRoom());
    assertSame(second, drlg.getDrlgRoom());
    assertEquals(2, drlg.getAllocatedRooms());
  }

  @Test
  void treatsNativeTimeoutAsUnsignedByte() {
    D2DrlgStrc drlg = new D2DrlgStrc();
    DrlgActivate.initializeRoomExStatusLists(drlg);
    drlg.setRoomsInitTimeout((byte) 0);

    DrlgActivate.update(drlg);

    assertEquals(255, Byte.toUnsignedInt(drlg.getRoomsInitTimeout()));
  }

  @Test
  void snapshotsClientAndServerAllocationStatistics() {
    D2DrlgStrc server = new D2DrlgStrc();
    server.setAllocatedRooms(11);
    server.setFreedRooms(4);
    server.setRoomsInitTimeout((byte) 2);
    DrlgActivate.update(server);

    D2DrlgStrc client = new D2DrlgStrc();
    client.setFlags(D2DrlgFlags.ONCLIENT);
    client.setAllocatedRooms(8);
    client.setFreedRooms(3);
    client.setRoomsInitTimeout((byte) 2);
    DrlgActivate.update(client);

    int[] clientAllocated = new int[1];
    int[] clientFreed = new int[1];
    int[] serverAllocated = new int[1];
    int[] serverFreed = new int[1];
    DrlgActivate.getRoomsAllocationStats(
        clientAllocated, clientFreed, serverAllocated, serverFreed);

    assertEquals(8, clientAllocated[0]);
    assertEquals(3, clientFreed[0]);
    assertEquals(11, serverAllocated[0]);
    assertEquals(4, serverFreed[0]);
  }

  private static D2DrlgRoom linkOutOfSightRoom(
      D2DrlgStrc drlg, D2DrlgLevel level, int tileX, int tileY) {
    D2DrlgRoom room = new D2DrlgRoom();
    room.setLevel(level);
    room.setRoomStatus(D2DrlgRoomStatus.CLIENT_OUT_OF_SIGHT);
    room.setType(-1);
    room.setNTileXPos(tileX);
    room.setNTileYPos(tileY);
    room.setNTileWidth(2);
    room.setNTileHeight(2);
    room.setSeed(new D2Seed());
    room.setTileGrid(new D2DrlgTileGrid());

    D2DrlgRoom head = drlg.getStatusRoomsLists()[
        D2DrlgRoomStatus.CLIENT_OUT_OF_SIGHT.getValue()];
    room.setStatusNext(head);
    room.setStatusPrev(head.getStatusPrev());
    head.getStatusPrev().setStatusNext(room);
    head.setStatusPrev(room);
    return room;
  }
}
