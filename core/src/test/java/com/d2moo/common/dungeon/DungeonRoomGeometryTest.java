package com.d2moo.common.dungeon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.d2moo.common.drlg.D2ActiveRoom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class DungeonRoomGeometryTest {
  @Test
  void computesNativeDrawAndSubtileRectangles() {
    D2ActiveRoom room = room(2, 3, 4, 5);

    assertArrayEquals(new int[] {-560, 280, 160, 640},
        Dungeon.getRoomDrawRect(room));
    assertArrayEquals(new int[] {10, 15, 30, 40},
        Dungeon.getSubtileRect(room));
    assertArrayEquals(new int[4], Dungeon.getRoomDrawRect(null));
    assertArrayEquals(new int[4], Dungeon.getSubtileRect(null));
  }

  @Test
  void visitsAdjacentRoomsInOrderAndStopsWhenCallbackReturnsFalse() {
    D2ActiveRoom first = room(0, 0, 1, 1);
    D2ActiveRoom second = room(1, 0, 1, 1);
    D2ActiveRoom third = room(2, 0, 1, 1);
    D2ActiveRoom source = room(0, 0, 3, 1);
    source.setPpRoomList(new D2ActiveRoom[] {first, second, third});
    source.setNNumRooms(3);
    List<D2ActiveRoom> visited = new ArrayList<>();

    Dungeon.callRoomCallback(source, (adjacent, output) -> {
      output.add(adjacent);
      return output.size() < 2;
    }, visited);

    assertEquals(Arrays.asList(first, second), visited);
    Dungeon.callRoomCallback(null, (adjacent, output) -> true, visited);
    Dungeon.callRoomCallback(source, null, visited);
    assertEquals(2, visited.size());
  }

  @Test
  void preservesKnownBrokenOverlapExportBehavior() {
    assertEquals(4,
        Dungeon.checkRoomsOverlappingBroken(room(0, 0, 3, 4), room(100, 100, 1, 1)));
    assertEquals(1,
        Dungeon.checkRoomsOverlappingBroken(room(0, 0, 0, 4), null));
    assertEquals(3,
        Dungeon.checkRoomsOverlappingBroken(room(0, 0, 3, 0), null));
    assertEquals(0, Dungeon.checkRoomsOverlappingBroken(null, null));
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
