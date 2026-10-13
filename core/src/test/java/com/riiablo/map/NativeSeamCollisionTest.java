package com.riiablo.map;

import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Levels;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeSeamCollisionTest extends RiiabloTest {
  @Test void scopedCollisionOnlyCrossesExplicitNativeSeamsAndKeepsFootprint() {
    Map map = new Map(0, 0);
    Map.Zone town = zone(map, 40, 0);
    Map.Zone outdoor = zone(map, 41, 12);
    Map.Zone detached = zone(map, 42, 12);
    assertTrue(map.isBlockedForPath(town, 12, 6, 9, 2, false), "unlinked edge");
    map.addWarpDestinationOverride(40, 0, 41);
    assertTrue(map.isBlockedForPath(town, 12, 6, 9, 2, false), "warp alone is not a seam");
    town.setNativeSeamAnchor(0, 11, 6);
    for (Map.Zone ignored : map.getZones()) {
      assertFalse(map.isBlockedForPath(town, 12, 6, 9, 2, false),
          "seam queries must not invalidate a caller's Zone iterator");
    }
    assertFalse(map.isBlockedForPath(town, 12, 6, 9, 2, false));
    assertFalse(map.isBlockedForPath(outdoor, 11, 6, 9, 2, false), "reverse traversal");
    outdoor.flags[6 * outdoor.width + 1] = (byte) DT1.Tile.FLAG_BLOCK_PLAYER_WALK;
    assertTrue(map.isBlockedForPath(town, 12, 6, 9, 2, false), "neighbor footprint obstacle");
    assertFalse(map.isBlockedForPath(detached, 13, 6, 9, 1, false), "detached scope retained");
    assertTrue(map.isBlockedForPath(detached, 11, 6, 9, 1, false), "detached cannot borrow town");
  }

  private static Map.Zone zone(Map map, int id, int x) {
    Map.Zone zone = new Map.Zone();
    zone.map = map;
    zone.level = new Levels.Entry();
    zone.level.Id = id;
    zone.x = x;
    zone.width = zone.height = 12;
    zone.flags = new byte[144];
    map.zones.add(zone);
    return zone;
  }
}
