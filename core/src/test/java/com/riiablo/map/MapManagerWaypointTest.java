package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.riiablo.engine.Engine;

class MapManagerWaypointTest {
  @Test
  void waypointStartsInPersistedModeBeforeCofLoading() {
    assertEquals(Engine.Object.MODE_NU, MapManager.resolveWaypointInitialMode(false));
    assertEquals(Engine.Object.MODE_ON, MapManager.resolveWaypointInitialMode(true));
  }
}
