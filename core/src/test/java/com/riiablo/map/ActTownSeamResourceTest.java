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
