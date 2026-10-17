package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Act2TombEntranceResourceTest extends RiiabloTest {
  @Test void reportedRockyWasteRetainsNativeTombEntranceArtwork() {
    Map map = new Map(127897359, 0);
    map.act2Builder().generate(map, map.seed, 0);
    Map.Zone rocky = null;
    for (Map.Zone zone : map.getZones()) if (zone.levelId() == 41) rocky = zone;
    assertNotNull(rocky);
    TileGrid grid = rocky.nativeTileGrid();
    DT1s library = new DT1s();
    java.util.Set<String> loaded = new java.util.HashSet<>();
    int entrances = 0;
    for (int slot = 0; slot < TileGrid.MAX_WALL_LAYERS; slot++) {
      for (int y = 0; y < grid.height; y++) for (int x = 0; x < grid.width; x++) {
        int id = grid.wallIds[slot][y][x];
        if (id == -1 || !Orientation.isSpecial(DT1.Tile.Index.orientation(id))) continue;
        String source = grid.sourceFile(grid.wallSourceFiles[slot][y][x]);
        System.out.println("TOMB_ENTRANCE source=" + source + " tile=" + id
            + " pos=" + x + "," + y + " hidden=" + grid.hiddenWallCells[slot][y][x]);
        if (source == null) continue; // synthetic interaction marker, no native artwork
        if (loaded.add(source)) library.add(DT1.loadFromFile(Riiablo.mpqs.resolve(source)), source);
        if (!grid.hiddenWallCells[slot][y][x]) entrances++;
      }
    }
    assertTrue(entrances > 0);
    DT1.Tile[][] layers = new DT1.Tile[Map.MAX_LAYERS][];
    layers[Map.FLOOR_OFFSET] = new DT1.Tile[grid.width * grid.height];
    Act1MapBuilderD2MOD.applyTileGridLayers(grid, library, layers,
        grid.width, grid.width, grid.height, null);
    int drawn = 0;
    for (int slot = 0; slot < TileGrid.MAX_WALL_LAYERS; slot++) {
      DT1.Tile[] layer = layers[Map.WALL_OFFSET + slot];
      if (layer == null) continue;
      for (DT1.Tile tile : layer) {
        if (tile != null && Orientation.isSpecial(tile.orientation)) drawn++;
      }
    }
    assertEquals(entrances, drawn, "native entrance graphics installed, not just Warp interaction");
    for (int slot = 0; slot < TileGrid.MAX_WALL_LAYERS; slot++) {
      for (int y = 0; y < grid.height; y++) for (int x = 0; x < grid.width; x++) {
        int id = grid.wallIds[slot][y][x];
        if (id != -1 && Orientation.isSpecial(DT1.Tile.Index.orientation(id))) {
          grid.hiddenWallCells[slot][y][x] = true;
        }
      }
    }
    DT1.Tile[][] hiddenLayers = new DT1.Tile[Map.MAX_LAYERS][];
    hiddenLayers[Map.FLOOR_OFFSET] = new DT1.Tile[grid.width * grid.height];
    Act1MapBuilderD2MOD.applyTileGridLayers(grid, library, hiddenLayers,
        grid.width, grid.width, grid.height, null);
    for (int slot = 0; slot < TileGrid.MAX_WALL_LAYERS; slot++) {
      DT1.Tile[] layer = hiddenLayers[Map.WALL_OFFSET + slot];
      if (layer == null) continue;
      for (DT1.Tile tile : layer) {
        assertTrue(tile == null || !Orientation.isSpecial(tile.orientation),
            "hidden entrance markers must remain invisible");
      }
    }
  }
}
