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

  @Test
  void activationSnapshotDoesNotMutateGeneratedCells() {
    Map.Zone zone = threeRoomZone();
    AutomapLayer layer = new AutomapLayer(1);
    layer.addFloor(101, 10, 10);
    layer.addWall(102, 50, 10);
    layer.addObject(103, 90, 10);

    int floors = layer.floors.size;
    int walls = layer.walls.size;
    int objects = layer.objects.size;
    int floorX = layer.floors.get(0).xPixel;
    int wallX = layer.walls.get(0).xPixel;
    int objectX = layer.objects.get(0).xPixel;

    zone.enterClientRoom(0);
    layer.updateRoomExploration(zone, 10, 10);
    zone.changeClientRoom(0, 1);
    layer.updateRoomExploration(zone, 50, 10);

    assertTrue(zone.getRoomsEx().get(1).getActivationStatus() == Map.RoomEx.CLIENT_IN_ROOM);
    assertTrue(layer.floors.size == floors);
    assertTrue(layer.walls.size == walls);
    assertTrue(layer.objects.size == objects);
    assertTrue(layer.floors.get(0).xPixel == floorX);
    assertTrue(layer.walls.get(0).xPixel == wallX);
    assertTrue(layer.objects.get(0).xPixel == objectX);
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
