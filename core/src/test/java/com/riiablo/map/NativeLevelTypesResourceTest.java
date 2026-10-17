package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Excel;
import com.riiablo.codec.excel.LvlTypes;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the production table loader and the installed 1.10f MPQ. */
class NativeLevelTypesResourceTest extends RiiabloTest {
  @Test void productionLevelTypesMatchNativeRowsWithoutExpansionSeparator() {
    LvlTypes nativeRows = Excel.load(LvlTypes.class,
        Riiablo.mpqs.resolve("data/global/excel/LvlTypes.txt"), Excel.EXPANSION);
    for (int type = 0; type < nativeRows.size(); type++) {
      LvlTypes.Entry expected = nativeRows.get(type);
      LvlTypes.Entry actual = Riiablo.files.LvlTypes.get(type);
      assertNotNull(actual, "missing level type=" + type);
      assertEquals(expected.Name, actual.Name,
          "level type=" + type + " expected Id=" + expected.Id + " actual Id=" + actual.Id);
      assertArrayEquals(expected.File, actual.File, "level type=" + type);
    }
    assertEquals(29, Riiablo.files.LvlTypes.get(29).Id);
    assertEquals("Expansion/town/blacksmith.dt1",
        Riiablo.files.LvlTypes.get(29).File[0].replace('\\', '/'));
    assertEquals(30, Riiablo.files.LvlTypes.get(30).Id);
    assertEquals(31, Riiablo.files.LvlTypes.get(31).Id);
    assertEquals(nativeRows.size(), Riiablo.files.LvlTypes.size());
  }

  @Test void actFiveProductionDependenciesInstallNativeTerrain() {
    Map map = new Map(127897359, 0);
    map.act5Builder().generate(map, map.seed, 0);
    for (Map.Zone zone : map.getZones()) {
      DT1s library = new DT1s();
      // Exercise Zone's production type/mask selection. Loading the DT1s
      // directly from export source names would miss the reported regression.
      for (AssetDescriptor dependency : zone.getDependencies()) {
        if (dependency.type == DT1.class) {
          library.add(DT1.loadFromFile(Riiablo.mpqs.resolve(dependency.fileName)),
              dependency.fileName);
        }
      }
      TileGrid grid = zone.nativeTileGrid();
      assertNotNull(grid, "level=" + zone.levelId());
      DT1.Tile[][] layers = new DT1.Tile[Map.MAX_LAYERS][];
      layers[Map.FLOOR_OFFSET] = new DT1.Tile[grid.width * grid.height];
      Act1MapBuilderD2MOD.LayerApplyCounts counts = Act1MapBuilderD2MOD.applyTileGridLayers(
          grid, library, layers, grid.width, grid.width, grid.height, null);
      assertEquals(0, counts.failedResolve, "production DT1 dependencies level=" + zone.levelId());
      assertTrue(counts.floors > 0, "visible terrain level=" + zone.levelId());
    }
  }
}
