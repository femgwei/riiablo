package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Act5TownHiddenFloorResourceTest extends RiiabloTest {
  @Test void townCollisionArtworkStaysHiddenWithoutRemovingCollision() {
    Map map = new Map(127897359, 0);
    map.act5Builder().generate(map, map.seed, 0);
    Map.Zone town = null;
    for (Map.Zone zone : map.getZones()) if (zone.levelId() == 109) town = zone;
    assertNotNull(town);
    TileGrid grid = town.nativeTileGrid();
    int shenk = 0;
    for (Map.Zone zone : map.getZones()) if (zone.levelId() == 110) {
      for (Map.NativeMonster monster : zone.getNativeMonsters()) {
        if (monster.superUniqueId == 42) {
          shenk++;
          int wx = (int) Math.floor(zone.x() + monster.x);
          int wy = (int) Math.floor(zone.y() + monster.y);
          assertTrue(zone.contains(wx, wy));
          assertFalse(town.contains(wx, wy));
        }
      }
    }
    assertEquals(1, shenk, "native siege preset owns exactly one Shenk placement");
    DT1s library = new DT1s();
    for (int i = 1; i <= 255; i++) {
      String source = grid.sourceFile((byte) i);
      if (source != null) library.add(DT1.loadFromFile(Riiablo.mpqs.resolve(source)), source);
    }
    DT1.Tile[][] layers = new DT1.Tile[Map.MAX_LAYERS][];
    layers[Map.FLOOR_OFFSET] = new DT1.Tile[grid.width * grid.height];
    Act1MapBuilderD2MOD.applyTileGridLayers(grid, library, layers, grid.width, grid.width, grid.height, null);
    int hidden = 0;
    for (int y = 0; y < grid.height; y++) for (int x = 0; x < grid.width; x++) {
      for (int slot = 0; slot < 2; slot++) {
        int id = slot == 0 ? grid.floorIds[y][x] : grid.floorOverlayIds[y][x];
        byte sourceIndex = slot == 0 ? grid.floorSourceFiles[y][x] : grid.floorOverlaySourceFiles[y][x];
        String source = grid.sourceFile(sourceIndex);
        if (id == -1 || source == null || !source.toLowerCase(java.util.Locale.ROOT).endsWith("/collision.dt1")) continue;
        int flags = slot == 0 ? grid.floorNativeFlags[y][x] : grid.floorOverlayNativeFlags[y][x];
        assertFalse(Act1MapBuilderD2MOD.isNativeFloorVisible(flags), "native collision flags=" + flags);
        assertTrue(layers[Map.FLOOR_OFFSET + slot] == null
            || layers[Map.FLOOR_OFFSET + slot][y * grid.width + x] == null);
        hidden++;
      }
    }
    assertTrue(hidden > 0, "real Harrogath collision artwork must be exercised");
    assertNotNull(grid.nativeCollisionFlags, "native movement collision remains available");
  }

  @Test void revealableCaveFloorsAreNotSuppressed() {
    assertTrue(Act1MapBuilderD2MOD.isNativeFloorVisible(0));
    assertFalse(Act1MapBuilderD2MOD.isNativeFloorVisible(8));
    assertTrue(Act1MapBuilderD2MOD.isNativeFloorVisible(8 | 0x200));
  }
}
