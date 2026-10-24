package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** Regression coverage for re-entrant zone queries on LibGDX Array-backed maps. */
class MapZoneLookupTest {
  @Test
  void compatibilityLookupAllowsReentrantContains() {
    Map map = new Map(0, 0);
    Map owner = map;
    Map.Zone zone = new Map.Zone() {
      private boolean reentered;

      @Override
      public boolean contains(int x, int y) {
        if (!reentered) {
          reentered = true;
          assertSame(this, owner.getZone(x, y));
        }
        return super.contains(x, y);
      }
    };
    zone.setPosition(0, 0);
    zone.width = zone.height = 10;
    zone.tilesX = zone.tilesY = 2;
    zone.flags = new byte[100];
    map.zones.add(zone);

    assertSame(zone, map.getZone(1, 1));
  }
}
