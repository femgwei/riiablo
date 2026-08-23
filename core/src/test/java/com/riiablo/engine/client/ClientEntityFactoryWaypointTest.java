package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;

class ClientEntityFactoryWaypointTest {
  @Test
  void waypointIsSelectableBeforeItsFirstModeTransition() {
    Objects.Entry waypoint = new Objects.Entry();
    waypoint.SubClass = Engine.Object.SUBCLASS_WAYPOINT;
    waypoint.Selectable = new boolean[8];

    assertTrue(ClientEntityFactory.isWaypoint(waypoint));
    assertTrue(ClientEntityFactory.isInitiallySelectable(waypoint));
  }

  @Test
  void ordinaryObjectUsesNeutralModeSelectableFlag() {
    Objects.Entry object = new Objects.Entry();
    object.Selectable = new boolean[8];

    assertFalse(ClientEntityFactory.isInitiallySelectable(object));
    object.Selectable[Engine.Object.MODE_NU] = true;
    assertTrue(ClientEntityFactory.isInitiallySelectable(object));
  }
}
