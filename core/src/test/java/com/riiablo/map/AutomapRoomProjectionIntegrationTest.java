package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.engine.client.automap.AutomapLayer;
import com.riiablo.engine.client.automap.AutomapVisibility;

import org.junit.jupiter.api.Test;

/** Verifies Automap visibility follows native RoomEx activation across rooms. */
class AutomapRoomProjectionIntegrationTest {
  @Test
  void currentAndSightRoomsRevealTogetherAcrossRoomBoundary() {
    Map.Zone zone = threeRoomZone();
    zone.enterClientRoom(0);

    AutomapLayer layer = new AutomapLayer(1);
    layer.updateRoomExploration(zone, 10, 10);

    // Room 0 is CLIENT_IN_ROOM and room 1 is CLIENT_IN_SIGHT.
    assertTrue(layer.isExplored(10, 10));
    assertTrue(layer.isExplored(50, 10));
    // Room 2 is two links away and must remain hidden.
    assertFalse(layer.isExplored(90, 10));

    assertTrue(AutomapVisibility.isEntityVisible(zone, 10, 10));
    assertTrue(AutomapVisibility.isEntityVisible(zone, 50, 10));
    assertFalse(AutomapVisibility.isEntityVisible(zone, 90, 10));
  }

  @Test
  void movingClientRoomUpdatesVisibleRingWithoutCoordinateDrift() {
    Map.Zone zone = threeRoomZone();
    zone.enterClientRoom(0);
    zone.changeClientRoom(0, 1);

    AutomapLayer layer = new AutomapLayer(1);
    layer.updateRoomExploration(zone, 50, 10);

    assertTrue(layer.isExplored(10, 10));
    assertTrue(layer.isExplored(50, 10));
    assertTrue(layer.isExplored(90, 10));
    assertFalse(AutomapVisibility.isEntityVisible(zone, 130, 10));
  }

  private static Map.Zone threeRoomZone() {
    Map.Zone zone = new Map.Zone();
    Map.RoomEx first = zone.addRoomEx(0, 0, 40, 40);
    Map.RoomEx second = zone.addRoomEx(40, 0, 40, 40);
    Map.RoomEx third = zone.addRoomEx(80, 0, 40, 40);
    first.setAdjacentRoomIds(new int[] {second.id});
    second.setAdjacentRoomIds(new int[] {first.id, third.id});
    third.setAdjacentRoomIds(new int[] {second.id});
    return zone;
  }
}
