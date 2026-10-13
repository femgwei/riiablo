package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Installation-level audit, without GL: generation alone is not rendering. */
class MapResourceAuditTest extends RiiabloTest {
  @Test void nativeExportsResolveAndVisibleEntrancesReachInstalledLayers() {
    for (String seedText : System.getProperty("map.audit.seeds", "127897359").split(",")) {
      int seed = Integer.parseInt(seedText.trim());
      DT1s library = new DT1s();
      java.util.Set<String> loaded = new java.util.HashSet<>();
      for (int act = 1; act <= 4; act++) {
        Map map = new Map(seed, 0);
        if (act == 1) Act2MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
        if (act == 2) Act3MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
        if (act == 3) Act4MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
        if (act == 4) Act5MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
        for (Map.Zone zone : map.getZones()) {
          TileGrid grid = zone.nativeTileGrid();
          assertNotNull(grid, "missing native export seed=" + seed + " level=" + zone.levelId());
          for (int index = 1; index <= 255; index++) {
            String source = grid.sourceFile((byte) index);
            if (source == null) continue;
            if (loaded.add(source.toLowerCase(java.util.Locale.ROOT))) {
              library.add(DT1.loadFromFile(Riiablo.mpqs.resolve(source)), source);
            }
          }
          DT1.Tile[][] layers = new DT1.Tile[Map.MAX_LAYERS][];
          layers[Map.FLOOR_OFFSET] = new DT1.Tile[grid.width * grid.height];
          Act1MapBuilderD2MOD.LayerApplyCounts counts = Act1MapBuilderD2MOD.applyTileGridLayers(
              grid, library, layers, grid.width, grid.width, grid.height, null);
          String context = "seed=" + seed + " level=" + zone.levelId();
          System.out.println("[MAP_RESOURCE_AUDIT] " + context + " floors=" + counts.floors
              + " walls=" + counts.walls + " shadows=" + counts.shadows
              + " failedResolve=" + counts.failedResolve
              + " objects=" + zone.getNativeObjects().size + " monsters=" + zone.getNativeMonsters().size);
          assertEquals(0, counts.failedResolve, context + " missing DT1 installation");
          for (int slot = 0; slot < TileGrid.MAX_WALL_LAYERS; slot++) {
            for (int y = 0; y < grid.height; y++) for (int x = 0; x < grid.width; x++) {
              int id = grid.wallIds[slot][y][x];
              String source = grid.sourceFile(grid.wallSourceFiles[slot][y][x]);
              if (id == -1 || !Orientation.isSpecial(DT1.Tile.Index.orientation(id)) || source == null
                  || source.replace('\\', '/').toLowerCase(java.util.Locale.ROOT).endsWith("/warp.dt1")) continue;
              DT1.Tile installed = layers[Map.WALL_OFFSET + slot] == null ? null
                  : layers[Map.WALL_OFFSET + slot][y * grid.width + x];
              if (grid.hiddenWallCells[slot][y][x]) assertNull(installed, context + " hidden marker drawn");
              else assertNotNull(installed, context + " visible entrance dropped at " + x + "," + y);
            }
          }
        }
      }
    }
  }
}
