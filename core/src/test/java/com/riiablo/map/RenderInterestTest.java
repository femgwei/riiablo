package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.camera.IsometricCamera;
import org.junit.jupiter.api.Test;

class RenderInterestTest {
  @Test
  void expandsCameraSeedByOneNativeAdjacencyRing() {
    Map.Zone zone = lineZone(4);
    RenderInterest interest = new RenderInterest(1);

    interest.beginUpdate();
    interest.seed(zone, 1);
    interest.endUpdate();

    assertTrue(interest.contains(zone, 0));
    assertTrue(interest.contains(zone, 1));
    assertTrue(interest.contains(zone, 2));
    assertFalse(interest.contains(zone, 3));
    assertEquals(1, interest.seedRoomCount());
    assertEquals(3, interest.interestedRoomCount());
  }

  @Test
  void supportsAdditionalBfsRingsWithoutChangingRoomActivation() {
    Map.Zone zone = lineZone(4);
    Map.RoomEx seed = zone.getRoomsEx().get(1);
    int activation = seed.getActivationStatus();
    RenderInterest interest = new RenderInterest(2);

    interest.beginUpdate();
    interest.seed(zone, seed.id);
    interest.endUpdate();

    assertTrue(interest.contains(zone, 3));
    assertEquals(activation, seed.getActivationStatus());
    assertEquals(0, seed.getClientInRoomRefs());
    assertEquals(0, seed.getClientInSightRefs());
    assertEquals(0, seed.getClientOutOfSightRefs());
    assertEquals(0, seed.getUntileRefs());
  }

  @Test
  void replacesInterestOnTheNextCameraUpdate() {
    Map.Zone zone = lineZone(4);
    RenderInterest interest = new RenderInterest(0);
    interest.beginUpdate();
    interest.seed(zone, 0);
    interest.endUpdate();
    assertTrue(interest.contains(zone, 0));

    interest.beginUpdate();
    interest.seed(zone, 3);
    interest.endUpdate();

    assertFalse(interest.contains(zone, 0));
    assertTrue(interest.contains(zone, 3));
  }

  @Test
  void legacyOrUnknownRoomsFailOpen() {
    Map.Zone legacy = new Map.Zone();
    legacy.addRoomEx(0, 0, 40, 40);
    RenderInterest interest = new RenderInterest();
    interest.beginUpdate();
    interest.endUpdate();

    assertTrue(interest.contains(legacy, 0));

    Map.Zone nativeZone = lineZone(1);
    assertTrue(interest.contains(nativeZone, -1));
    assertTrue(interest.contains(nativeZone, 10f, 100f));
  }

  @Test
  void detectsProjectedRoomOverlapWithCameraBounds() {
    IsometricCamera camera = new IsometricCamera();
    Map.RoomEx room = new Map.RoomEx(0, 0, 0, 40, 40);

    assertTrue(RenderInterest.intersectsCamera(
        room, camera, -100f, -400f, 100f, -200f));
    assertFalse(RenderInterest.intersectsCamera(
        room, camera, 1000f, 1000f, 1200f, 1200f));
  }

  private static Map.Zone lineZone(int count) {
    Map.Zone zone = new Map.Zone();
    for (int i = 0; i < count; i++) zone.addRoomEx(i * 40, 0, 40, 40);
    for (int i = 0; i < count; i++) {
      if (count == 1) {
        zone.getRoomsEx().get(i).setAdjacentRoomIds(new int[0]);
      } else if (i == 0) {
        zone.getRoomsEx().get(i).setAdjacentRoomIds(new int[] {1});
      } else if (i == count - 1) {
        zone.getRoomsEx().get(i).setAdjacentRoomIds(new int[] {i - 1});
      } else {
        zone.getRoomsEx().get(i).setAdjacentRoomIds(new int[] {i - 1, i + 1});
      }
    }
    return zone;
  }
}
