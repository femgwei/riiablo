package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Signed native coordinates must align with automap tile projection. */
public class ZoneCoordinateAlignmentTest {
  @Test
  public void negativeSubtileOriginUsesFloorTileDivision() {
    Map.Zone zone = new Map.Zone();
    zone.setPosition(-1, -6);

    assertEquals(0, zone.getLocalTX(-1));
    assertEquals(1, zone.getLocalTX(0));
    assertEquals(0, zone.getLocalTY(-2));
    assertEquals(1, zone.getLocalTY(-1));
  }
}
