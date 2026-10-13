package com.riiablo.map;

import com.riiablo.RiiabloTest;
import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real MPQ terrain, not runtime warp markers, for the reported seed. */
class ActTownSeamResourceTest extends RiiabloTest {
  @Test void townAndFirstOutdoorHaveAnOpenNativeTerrainSeam() {
    for (int act = 1; act <= 3; act++) {
      Map map = new Map(127897359, 0);
      if (act == 1) Act2MapBuilderD2MOD.INSTANCE.generate(map, map.seed, 0);
      if (act == 2) Act3MapBuilderD2MOD.INSTANCE.generate(map, map.seed, 0);
      if (act == 3) Act4MapBuilderD2MOD.INSTANCE.generate(map, map.seed, 0);
      int townId = act == 1 ? 40 : act == 2 ? 75 : 103;
      Map.Zone town = null, outdoor = null;
      for (Map.Zone zone : map.getZones()) {
        if (zone.levelId() == townId) town = zone;
        if (zone.levelId() == townId + 1) outdoor = zone;
      }
      assertNotNull(town);
      assertNotNull(outdoor);
      assertNotNull(town.nativeTileGrid(), "native town " + townId);
      assertTrue(map.areZonesAdjacent(town, outdoor), "geometric seam " + townId);
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

  private static boolean open(Map.Zone zone, int worldX, int worldY) {
    int x = worldX - zone.x(), y = worldY - zone.y();
    TileGrid grid = zone.nativeTileGrid();
    if (grid == null || x < 0 || y < 0 || x >= zone.width() || y >= zone.height()) return false;
    if (grid.nativeCollisionFlags == null || !grid.nativeCollisionCells[y / 5][x / 5]) return false;
    return (grid.nativeCollisionFlags[y * zone.width() + x] & 9) == 0;
  }
}
