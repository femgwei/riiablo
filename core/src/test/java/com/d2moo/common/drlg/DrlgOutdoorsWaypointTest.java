package com.d2moo.common.drlg;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DrlgOutdoorsWaypointTest {
  @Test
  void coldPlainsUsesExactBloodMoorVisFlagWhenAvailable() {
    assertTrue(DrlgOutdoors.matchesColdPlainsWaypointLink(0x40, 0x40));
    assertFalse(DrlgOutdoors.matchesColdPlainsWaypointLink(0x20, 0x40));
  }

  @Test
  void coldPlainsFallsBackToAnOutdoorLinkWhenRuntimeVisIsMissing() {
    assertTrue(DrlgOutdoors.matchesColdPlainsWaypointLink(0x10, 0));
    assertTrue(DrlgOutdoors.matchesColdPlainsWaypointLink(0x800, 0));
    assertFalse(DrlgOutdoors.matchesColdPlainsWaypointLink(0x10000, 0));
  }
}
