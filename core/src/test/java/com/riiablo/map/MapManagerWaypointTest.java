package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import com.riiablo.engine.Engine;

class MapManagerWaypointTest {
  @Test
  void waypointStartsInPersistedModeBeforeCofLoading() {
    assertEquals(Engine.Object.MODE_NU, MapManager.resolveWaypointInitialMode(false));
    assertEquals(Engine.Object.MODE_ON, MapManager.resolveWaypointInitialMode(true));
  }

  @Test
  void waypointTravelPreservesExactObjectCenter() {
    Vector2 waypoint = new Vector2(119.25f, 94.75f);
    Vector2 out = new Vector2();

    assertSame(out, MapManager.copyWaypointCenter(waypoint, out));
    assertEquals(waypoint.x, out.x);
    assertEquals(waypoint.y, out.y);
  }

  @Test
  void nativeOutdoorPresetUnitsWaitForRoomActivation() {
    Map.Zone zone = new Map.Zone();
    Map.RoomEx room = zone.addRoomEx(0, 0, 40, 40);
    room.setAdjacentRoomIds(new int[0]);

    assertFalse(MapManager.shouldCreateNativeObjectsImmediately(zone));

    zone.town = true;
    assertTrue(MapManager.shouldCreateNativeObjectsImmediately(zone));

    Map.Zone legacy = new Map.Zone();
    legacy.addRoomEx(0, 0, 40, 40);
    assertTrue(MapManager.shouldCreateNativeObjectsImmediately(legacy));
  }
}
