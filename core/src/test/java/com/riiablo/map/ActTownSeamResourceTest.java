package com.riiablo.map;

import com.riiablo.RiiabloTest;
import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real MPQ terrain, not runtime warp markers, for the reported seed. */
class ActTownSeamResourceTest extends RiiabloTest {
  @Test void townAndFirstOutdoorHaveAnOpenNativeTerrainSeam() {
    for (int act = 1; act <= 4; act++) {
      Map map = new Map(127897359, 0);
      if (act == 1) map.act2Builder().generate(map, map.seed, 0);
      if (act == 2) map.act3Builder().generate(map, map.seed, 0);
      if (act == 3) map.act4Builder().generate(map, map.seed, 0);
      if (act == 4) {
        java.io.PrintStream previous = System.err;
        java.io.ByteArrayOutputStream warnings = new java.io.ByteArrayOutputStream();
        try {
          System.setErr(new java.io.PrintStream(warnings));
          map.act5Builder().generate(map, map.seed, 0);
          map.act5Builder().configureAct5OutdoorWarps(map);
          map.act5Builder().configureAct5QuestWarps(map);
        } finally { System.setErr(previous); }
        previous.print(warnings.toString());
        assertFalse(warnings.toString().contains("sub_6FD84780:"),
            "A5 must not feed A1 substitution styles to the siege callback: " + warnings);
        assertFalse(warnings.toString().contains("Could not place enough prisons"),
            "reported seed must place all three A5 quest prisons: " + warnings);
      }
      int townId = act == 1 ? 40 : act == 2 ? 75 : act == 3 ? 103 : 109;
      Map.Zone town = null, outdoor = null;
      for (Map.Zone zone : map.getZones()) {
        if (zone.levelId() == townId) town = zone;
        if (zone.levelId() == townId + 1) outdoor = zone;
      }
      assertNotNull(town);
      assertNotNull(outdoor);
      System.out.println("TOWN_BOUNDS " + town + " OUTDOOR_BOUNDS " + outdoor);
      assertNotNull(town.nativeTileGrid(), "native town " + townId);
      assertTrue(map.areZonesAdjacent(town, outdoor), "geometric seam " + townId);
      assertTrue(map.isNativeSeamConnection(town, outdoor), "native walking link " + townId);
      assertTrue(map.nativeWalkEdges.size > 0, "raw native manifest captured before DRLG is freed");
      boolean nativeTownExit = false;
      for (Map.NativeWalkEdge edge : map.nativeWalkEdges) {
        if (edge.from == townId && edge.to == townId + 1) nativeTownExit = true;
      }
      assertTrue(nativeTownExit, "manifest includes native town exit " + townId);
      int[] outdoorChain = act == 1
          ? new int[] {40, 41, 42, 43, 44, 45}
          : act == 2
              ? new int[] {75, 76, 77, 78, 79, 80, 81, 82, 83}
              : act == 3
                  ? new int[] {103, 104, 105, 106}
                  : new int[] {109, 110};
      for (int i = 1; i < outdoorChain.length; i++) {
        Map.Zone previous = zone(map, outdoorChain[i - 1]);
        Map.Zone next = zone(map, outdoorChain[i]);
        assertNotNull(previous, "outdoor source " + outdoorChain[i - 1]);
        assertNotNull(next, "outdoor destination " + outdoorChain[i]);
        assertTrue(map.areZonesAdjacent(previous, next),
            "geometric outdoor seam " + outdoorChain[i - 1] + "->" + outdoorChain[i]);
        assertTrue(map.isNativeSeamConnection(previous, next),
            "native outdoor seam " + outdoorChain[i - 1] + "->" + outdoorChain[i]);
      }
      int crossings = 0;
      for (int y = town.y(); y < town.y() + town.height(); y++) {
        for (int x = town.x(); x < town.x() + town.width(); x++) {
          if (!open(town, x, y)) continue;
          if (open(outdoor, x - 1, y) || open(outdoor, x + 1, y)
              || open(outdoor, x, y - 1) || open(outdoor, x, y + 1)) crossings++;
        }
      }
      System.out.println("NATIVE_TOWN_SEAM " + townId + "->" + (townId + 1)
          + " preset=" + town.presetSourceFile() + " open=" + crossings);
      assertTrue(crossings > 0, "walkable native seam " + townId);
      for (Map.Zone zone : new Map.Zone[] {town, outdoor}) {
        zone.flags = zone.nativeTileGrid().nativeCollisionFlags.clone();
      }
      int playerCrossings = 0;
      for (int y = town.y(); y < town.y() + town.height(); y++) {
        for (int x = town.x(); x < town.x() + town.width(); x++) {
          for (int[] step : new int[][] {{-1,0},{1,0},{0,-1},{0,1}}) {
            int tx = x + step[0], ty = y + step[1];
            if (!outdoor.contains(tx, ty)
                || map.isBlockedForPath(town, x, y, 9, 2, false)
                || map.isBlockedForPath(town, tx, ty, 9, 2, false)) continue;
            com.badlogic.gdx.math.Vector2 src = new com.badlogic.gdx.math.Vector2(x, y);
            com.badlogic.gdx.math.Vector2 dst = new com.badlogic.gdx.math.Vector2(tx, ty);
            com.badlogic.gdx.ai.utils.Collision<com.badlogic.gdx.math.Vector2> hit =
                new com.badlogic.gdx.ai.utils.Collision<>(new com.badlogic.gdx.math.Vector2(), new com.badlogic.gdx.math.Vector2());
            assertFalse(map.castRay(town, new com.badlogic.gdx.ai.utils.Ray<>(src, dst), 9, 2, hit, false));
            assertFalse(map.castRay(outdoor, new com.badlogic.gdx.ai.utils.Ray<>(dst, src), 9, 2, hit, false));
            assertTrue(map.findPath(town, src, dst, 9, 2,
                new com.badlogic.gdx.ai.pfa.DefaultGraphPath<>(), null, -1, -1, false));
            if (playerCrossings == 0) assertWalkWithoutTeleport(map, town, outdoor, src, dst);
            playerCrossings++;
          }
        }
      }
      assertTrue(playerCrossings > 0, "size-two scoped player path crosses seam " + townId);
      System.out.println("PLAYER_TOWN_SEAM " + townId + " crossings=" + playerCrossings);
      if (townId == 103) {
        TileGrid grid = town.nativeTileGrid();
        int overlays = 0, stairs = 0;
        for (int y = 0; y < grid.height; y++) for (int x = 0; x < grid.width; x++) {
          if (grid.floorOverlayIds[y][x] != -1) overlays++;
          String source = grid.sourceFile(grid.floorSourceFiles[y][x]);
          if (source != null && source.toLowerCase(java.util.Locale.ROOT).contains("stairs.dt1")) stairs++;
        }
        assertTrue(overlays > 0, "fortress retains the second DS1 floor layer");
        assertTrue(stairs > 0, "fortress stair surface is not erased by blank filler");
      }
    }
  }

  private static void assertWalkWithoutTeleport(Map map, Map.Zone town, Map.Zone outdoor,
      com.badlogic.gdx.math.Vector2 src, com.badlogic.gdx.math.Vector2 dst) {
    com.artemis.World world = new com.artemis.World(new com.artemis.WorldConfigurationBuilder().with(
        new com.riiablo.engine.server.DynamicUnitCollisionSystem(false),
        new com.riiablo.engine.server.VelocityAdder(),
        new com.riiablo.engine.server.RoomEntityTrackingSystem()).build().register("map", map));
    try {
      int player = world.create();
      com.riiablo.engine.server.component.Position position = world.getMapper(
          com.riiablo.engine.server.component.Position.class).create(player);
      position.position.set(src);
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(player);
      world.getMapper(com.riiablo.engine.server.component.Size.class).create(player).size = 2;
      com.riiablo.engine.server.component.MapWrapper wrapper = world.getMapper(
          com.riiablo.engine.server.component.MapWrapper.class).create(player).set(map, town);
      com.riiablo.engine.server.component.Velocity velocity = world.getMapper(
          com.riiablo.engine.server.component.Velocity.class).create(player);
      velocity.velocity.set(dst).sub(src).scl(25);
      world.setDelta(0.04f);
      world.process();
      assertTrue(position.position.epsilonEquals(dst, 0.0001f), "ordinary movement crosses town edge");
      assertSame(outdoor, wrapper.zone, "room tracking switches level without a position jump");
      velocity.velocity.set(src).sub(dst).scl(25);
      world.process();
      assertTrue(position.position.epsilonEquals(src, 0.0001f));
      assertSame(town, wrapper.zone);
      int marker = world.create();
      world.getMapper(com.riiablo.engine.server.component.Warp.class).create(marker)
          .set(Orientation.SPECIAL_10, null, outdoor.level);
      world.getMapper(com.riiablo.engine.server.component.MapWrapper.class).create(marker).set(map, town);
      assertFalse(new SeamWarpInteractor(world, map).warp(player, marker),
          "even a stale town marker must not teleport");
      assertTrue(position.position.epsilonEquals(src, 0.0001f));
    } finally { world.dispose(); }
  }

  private static final class SeamWarpInteractor extends com.riiablo.engine.server.WarpInteractor {
    SeamWarpInteractor(com.artemis.World world, Map map) {
      this.map = map;
      mWarp = world.getMapper(com.riiablo.engine.server.component.Warp.class);
      mMapWrapper = world.getMapper(com.riiablo.engine.server.component.MapWrapper.class);
    }
  }

  private static boolean open(Map.Zone zone, int worldX, int worldY) {
    int x = worldX - zone.x(), y = worldY - zone.y();
    TileGrid grid = zone.nativeTileGrid();
    if (grid == null || x < 0 || y < 0 || x >= zone.width() || y >= zone.height()) return false;
    if (grid.nativeCollisionFlags == null || !grid.nativeCollisionCells[y / 5][x / 5]) return false;
    return (grid.nativeCollisionFlags[y * zone.width() + x] & 9) == 0;
  }

  private static Map.Zone zone(Map map, int levelId) {
    for (Map.Zone zone : map.getZones()) if (zone.levelId() == levelId) return zone;
    return null;
  }
}
