package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

/** Collision-only regression tests for synthetic Act V UNIT_TILE warps. */
class Act5WarpMarkerPositionTest {
  @Test
  void markerSearchLeavesBlockedTempleCentre() {
    Map.Zone zone = zone(100, 200, 12, 12);
    // mainIndex 0's preferred tile is (5,5). Block the complete tile so the
    // result must be another tile, not merely another subtile in the centre.
    for (int y = 25; y < 30; y++) {
      for (int x = 25; x < 30; x++) {
        zone.or(x, y, DT1.Tile.FLAG_BLOCK_WALK);
      }
    }

    Vector2 result = new Vector2();
    assertTrue(Act5MapBuilderD2MOD.resolveWarpMarkerPosition(zone, 0, result));
    assertNotEquals(new Vector2(25, 25), result);
    assertTrue(zone.contains(zone.x + (int) result.x, zone.y + (int) result.y));
    assertTrue((zone.flags((int) result.x, (int) result.y)
        & DT1.Tile.FLAG_BLOCK_WALK) == 0);
  }

  @Test
  void markerSearchRejectsFullyBlockedZone() {
    Map.Zone zone = zone(0, 0, 6, 6);
    for (int y = 0; y < zone.height; y++) {
      for (int x = 0; x < zone.width; x++) {
        zone.or(x, y, DT1.Tile.FLAG_BLOCK_WALK);
      }
    }

    assertFalse(Act5MapBuilderD2MOD.resolveWarpMarkerPosition(zone, 0, new Vector2()));
  }

  private static Map.Zone zone(int x, int y, int tilesX, int tilesY) {
    Map.Zone zone = new Map.Zone();
    zone.x = x;
    zone.y = y;
    zone.tilesX = tilesX;
    zone.tilesY = tilesY;
    zone.width = tilesX * DT1.Tile.SUBTILE_SIZE;
    zone.height = tilesY * DT1.Tile.SUBTILE_SIZE;
    zone.flags = new byte[zone.width * zone.height];
    return zone;
  }
}
