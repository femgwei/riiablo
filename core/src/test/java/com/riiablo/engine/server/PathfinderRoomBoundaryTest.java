package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;

class PathfinderRoomBoundaryTest {
  @Test
  void nativeMonsterPathAllowsOnlyCurrentOrDirectAdjacentRoom() {
    Map.Zone zone = new Map.Zone();
    Map.RoomEx first = zone.addRoomEx(0, 0, 40, 40);
    Map.RoomEx second = zone.addRoomEx(40, 0, 40, 40);
    Map.RoomEx third = zone.addRoomEx(80, 0, 40, 40);
    first.setAdjacentRoomIds(new int[] {second.id});
    second.setAdjacentRoomIds(new int[] {first.id, third.id});
    third.setAdjacentRoomIds(new int[] {second.id});

    assertTrue(Pathfinder.isRoomPathAllowed(zone, new Vector2(10, 10), new Vector2(50, 10)));
    assertFalse(Pathfinder.isRoomPathAllowed(zone, new Vector2(10, 10), new Vector2(90, 10)));
  }

  @Test
  void legacyZoneKeepsGeometryFallback() {
    Map.Zone zone = new Map.Zone();
    zone.addRoomEx(0, 0, 40, 40);
    zone.addRoomEx(40, 0, 40, 40);
    assertTrue(Pathfinder.isRoomPathAllowed(zone, new Vector2(10, 10), new Vector2(50, 10)));
  }
}
