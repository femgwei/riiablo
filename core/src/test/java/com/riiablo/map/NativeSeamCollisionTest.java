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
    outdoor.setNativeCollisionMask(13, 6, DT1.Tile.FLAG_BLOCK_PLAYER_WALK);
    assertTrue(map.isBlockedForPath(town, 12, 6, 9, 2, false), "neighbor footprint obstacle");
    assertFalse(map.isBlockedForPath(detached, 13, 6, 9, 1, false), "detached scope retained");
    assertTrue(map.isBlockedForPath(detached, 11, 6, 9, 1, false), "detached cannot borrow town");
  }

  @Test void shadowCaptureRetainsNativeSeamScopeWithoutChangingTheRoute() {
    Map map = new Map(0, 0);
    Map.Zone town = zone(map, 40, 0);
    zone(map, 41, 12);
    Map.Zone detached = zone(map, 42, 12);
    map.addWarpDestinationOverride(40, 0, 41);
    town.setNativeSeamAnchor(0, 11, 6);
    java.util.List<NativePathShadow.Result> rows = new java.util.ArrayList<>();
    com.riiablo.map.pfa.GraphPath before = new com.riiablo.map.pfa.GraphPath();
    com.riiablo.map.pfa.GraphPath after = new com.riiablo.map.pfa.GraphPath();
    com.badlogic.gdx.math.Vector2 start = new com.badlogic.gdx.math.Vector2(3, 6);
    com.badlogic.gdx.math.Vector2 target = new com.badlogic.gdx.math.Vector2(20, 6);
    map.setPathShadow(null);
    assertTrue(map.findPath(town, start, target, 9, 2, before, null, 31, -1, false));
    map.setPathShadow(new NativePathShadow(1, 1, rows::add));
    assertTrue(map.findPath(town, start, target, 9, 2, after, null, 31, -1, false));
    assertEquals(before.toString(), after.toString());
    assertEquals(1, rows.size());
    NativePathShadow.Result row = rows.get(0);
    assertEquals(NativePathShadow.Status.COMPARED, row.status);
    assertEquals(NativePathShadow.Outcome.EXACT, row.reference.outcome(row.target));
    assertFalse(row.differs());
    assertTrue(map.isBlockedForPath(detached, 11, 6, 9, 2, false), "detached scope must not gain a seam");
    System.out.println("[NATIVE_PATH_SHADOW_FIXTURE] native-seam " + row);
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
