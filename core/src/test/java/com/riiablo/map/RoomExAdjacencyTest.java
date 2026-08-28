package com.riiablo.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomExAdjacencyTest {
  @Test
  void currentAndTouchingNativeRoomsAreVisibleButDistantRoomsAreNot() {
    Map.RoomEx current = new Map.RoomEx(0, 0, 0, 40, 40);
    Map.RoomEx touching = new Map.RoomEx(1, 40, 0, 40, 40);
    Map.RoomEx sharedBorderGap = new Map.RoomEx(2, 44, 0, 40, 40);
    Map.RoomEx distant = new Map.RoomEx(3, 60, 0, 40, 40);

    assertTrue(current.contains(0, 0));
    assertTrue(current.contains(39.99f, 39.99f));
    assertFalse(current.contains(40, 20));
    assertTrue(current.touches(touching, 5));
    assertTrue(current.touches(sharedBorderGap, 5));
    assertFalse(current.touches(distant, 5));
  }

  @Test
  void nativeTopologyOverridesGeometricTouching() {
    Map.Zone zone = new Map.Zone();
    Map.RoomEx first = zone.addRoomEx(0, 0, 40, 40);
    Map.RoomEx second = zone.addRoomEx(40, 0, 40, 40);
    Map.RoomEx third = zone.addRoomEx(44, 0, 60, 40);
    first.setAdjacentRoomIds(new int[] {second.id});
    second.setAdjacentRoomIds(new int[] {first.id});
    third.setAdjacentRoomIds(new int[0]);

    assertTrue(zone.areRoomsAdjacent(10, 10, 50, 10));
    // The rectangles are within the legacy geometric margin, but D2MOO did
    // not report the third room as near.
    assertTrue(first.touches(third, 5));
    assertFalse(zone.areRoomsAdjacent(10, 10, 90, 10));
  }

  @Test
  void legacyRoomsUseGeometricFallbackWhenTopologyIsUnavailable() {
    Map.Zone zone = new Map.Zone();
    zone.addRoomEx(0, 0, 40, 40);
    zone.addRoomEx(40, 0, 40, 40);
    assertTrue(zone.areRoomsAdjacent(10, 10, 50, 10));
  }
}
