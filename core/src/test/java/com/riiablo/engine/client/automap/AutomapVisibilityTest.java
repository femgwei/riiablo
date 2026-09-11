package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AutomapVisibilityTest {
  @Test
  void onlyCurrentAndSightRoomsAreVisible() {
    assertTrue(AutomapVisibility.isActivationVisible(0));
    assertTrue(AutomapVisibility.isActivationVisible(1));
    assertFalse(AutomapVisibility.isActivationVisible(2));
    assertFalse(AutomapVisibility.isActivationVisible(3));
    assertFalse(AutomapVisibility.isActivationVisible(4));
    assertFalse(AutomapVisibility.isActivationVisible(-1));
  }

  @Test
  void nullRoomNeverLeaksAnEntityWhenNativeTrackingIsActive() {
    assertFalse(AutomapVisibility.isRoomVisible(null));
  }

  @Test
  void duplicateEntityRefreshKeepsOneMarkerAndNativeCell() {
    AutomapManager manager = new AutomapManager();
    manager.addEntityMarker(17, AutomapIconType.MONSTER, 1, 2, "Fallen",
        AutomapManager.COLOR_MONSTER, 4);
    manager.addNativeEntityMarker(17, AutomapIconType.MONSTER, 3, 4, "Fallen",
        AutomapManager.COLOR_MONSTER, 4, 9);

    // The manager's marker list is intentionally private; this assertion is
    // exercised through the stable collection accessors used by render tests.
    assertTrue(manager.getEntityMarkerCount() == 1);
    assertTrue(manager.getEntityMarker(0).nativeCell == 9);
    assertTrue(manager.getEntityMarker(0).worldX == 3);
  }
}
