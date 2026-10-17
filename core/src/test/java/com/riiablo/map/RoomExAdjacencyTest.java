package com.riiablo.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomExAdjacencyTest {
  @Test void populationAndAiShareLiveActiveRoomSeedWithoutResettingIt() {
    com.d2moo.common.drlg.D2DrlgRoom raw = new com.d2moo.common.drlg.D2DrlgRoom();
    raw.setSeed(new com.d2moo.common.drlg.D2Seed(123, 456));
    com.d2moo.common.drlg.D2ActiveRoom active = new com.d2moo.common.drlg.D2ActiveRoom();
    com.d2moo.common.drlg.D2Seed live = new com.d2moo.common.drlg.D2Seed(789, 666);
    active.setSeed(live); raw.setRoom(active);
    Map.RoomEx room = new Map.RoomEx(0, 0, 0, 40, 40);
    room.setNativePopulationMetadata(raw);
    org.junit.jupiter.api.Assertions.assertSame(live, room.runtimeSeed());
    com.d2moo.common.seed.Seed.rollRandomNumber(room.runtimeSeed());
    room.setNativePopulationMetadata(raw);
    org.junit.jupiter.api.Assertions.assertSame(live, room.runtimeSeed());
    org.junit.jupiter.api.Assertions.assertNotEquals(789, live.getNLowSeed());
  }

  @Test
  void nativeSeamVisibilityRequiresLevelQualifiedRoomLinks() {
    Map map = new Map(1, 0);
    Map.Zone a = zone(map, 26, 0), b = zone(map, 27, 80);
    Map.Zone overlap = zone(map, 28, 0), detached = zone(map, 29, 80);
    Map.RoomEx ar = a.addRoomEx(0, 0, 80, 80);
    Map.RoomEx br = b.addRoomEx(80, 0, 40, 80);
    Map.RoomEx far = b.addRoomEx(120, 0, 40, 80);
    overlap.addRoomEx(0, 0, 80, 80); detached.addRoomEx(80, 0, 80, 80);
    com.d2moo.common.drlg.D2DrlgLevel nativeA = new com.d2moo.common.drlg.D2DrlgLevel();
    com.d2moo.common.drlg.D2DrlgLevel nativeB = new com.d2moo.common.drlg.D2DrlgLevel();
    nativeA.setLevelId(26); nativeB.setLevelId(27);
    com.d2moo.common.drlg.D2DrlgRoom rawA = new com.d2moo.common.drlg.D2DrlgRoom();
    com.d2moo.common.drlg.D2DrlgRoom rawB = new com.d2moo.common.drlg.D2DrlgRoom();
    rawA.setLevel(nativeA); rawB.setLevel(nativeB);
    nativeA.setFirstRoomEx(rawA); nativeB.setFirstRoomEx(rawB);
    rawA.setPpRoomsNear(new com.d2moo.common.drlg.D2DrlgRoom[]{rawB}); rawA.setNRoomsNear(1);
    rawB.setPpRoomsNear(new com.d2moo.common.drlg.D2DrlgRoom[]{rawA}); rawB.setNRoomsNear(1);
    ar.setNativePopulationMetadata(rawA); br.setNativePopulationMetadata(rawB);
    ar.setAdjacentRoomIds(new int[0]); br.setAdjacentRoomIds(new int[0]); far.setAdjacentRoomIds(new int[0]);
    assertFalse(map.areNativeRoomsVisible(a, 79, 10, b, 81, 10), "room link alone is not a seam");
    map.addWarpDestinationOverride(26, 0, 27); a.setNativeSeamAnchor(0, 79, 10);
    assertTrue(map.areNativeRoomsVisible(a, 79, 10, b, 81, 10));
    assertTrue(map.areNativeRoomsVisible(b, 81, 10, a, 79, 10));
    assertFalse(map.areNativeRoomsVisible(a, 79, 10, b, 121, 10), "no whole-level visibility");
    assertFalse(map.areNativeRoomsVisible(a, 79, 10, detached, 81, 10), "local room id zero must not alias");
    assertFalse(map.areNativeRoomsVisible(a, 10, 10, overlap, 10, 10));
    assertFalse(map.areNativeRoomsVisible(a, 100, 10, b, 81, 10), "source outside room");
  }

  private static Map.Zone zone(Map map, int level, int x) {
    Map.Zone zone = new Map.Zone(); zone.map = map;
    zone.level = new com.riiablo.codec.excel.Levels.Entry(); zone.level.Id = level;
    zone.x = x; zone.width = zone.height = 80; map.zones.add(zone); return zone;
  }
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
