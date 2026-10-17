package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Act2TombHiddenWallResourceTest extends RiiabloTest {
  @Test void tombHiddenWallsDoNotInstallCollisionArtwork() {
    for (String seedText : System.getProperty("map.audit.seeds", "127897359").split(",")) {
      int seed = Integer.parseInt(seedText.trim());
      Map map = new Map(seed, 0);
      map.act2Builder().generate(map, seed, 0);
      DT1s library = new DT1s();
      java.util.Set<String> loaded = new java.util.HashSet<>();
      int hiddenWalls = 0;
      int visibleWalls = 0;
      for (Map.Zone zone : map.getZones()) {
        if (zone.levelId() < 55 || zone.levelId() > 72) continue;
        TileGrid grid = zone.nativeTileGrid();
        assertNotNull(grid);
        for (int index = 1; index <= 255; index++) {
          String source = grid.sourceFile((byte) index);
          if (source != null && loaded.add(source)) {
            library.add(DT1.loadFromFile(Riiablo.mpqs.resolve(source)), source);
          }
        }
        DT1.Tile[][] layers = new DT1.Tile[Map.MAX_LAYERS][];
        layers[Map.FLOOR_OFFSET] = new DT1.Tile[grid.width * grid.height];
        Act1MapBuilderD2MOD.LayerApplyCounts counts = Act1MapBuilderD2MOD.applyTileGridLayers(
            grid, library, layers, grid.width, grid.width, grid.height, null);
        assertEquals(0, counts.failedResolve);
        for (int slot = 0; slot < TileGrid.MAX_WALL_LAYERS; slot++) {
          for (int y = 0; y < grid.height; y++) for (int x = 0; x < grid.width; x++) {
            int id = grid.wallIds[slot][y][x];
            if (id == -1 || Orientation.isSpecial(DT1.Tile.Index.orientation(id))) continue;
            String source = grid.sourceFile(grid.wallSourceFiles[slot][y][x]);
            DT1.Tile installed = layers[Map.WALL_OFFSET + slot] == null ? null
                : layers[Map.WALL_OFFSET + slot][y * grid.width + x];
            int flags = grid.wallNativeFlags[slot][y][x];
            String context = "seed=" + seed + " level=" + zone.levelId()
                + " pos=" + x + "," + y + " slot=" + slot + " tile=" + id
                + " source=" + source + " flags=0x" + Integer.toHexString(flags);
            if ((flags & 8) != 0 && (flags & 0x200) == 0) {
              System.out.println("[TOMB_HIDDEN_WALL] " + context);
              assertNull(installed, context + " hidden wall artwork installed");
              hiddenWalls++;
            } else {
              assertNotNull(installed, context + " visible wall lost");
              visibleWalls++;
            }
          }
        }
        assertNotNull(grid.nativeCollisionFlags, "native collision must be retained");
      }
      assertTrue(hiddenWalls > 0, "exercise real tomb hidden walls");
      assertTrue(visibleWalls > 0, "exercise real tomb visible walls");
    }
  }
}
