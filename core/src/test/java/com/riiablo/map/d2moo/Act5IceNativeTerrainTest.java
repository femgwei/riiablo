package com.riiablo.map.d2moo;

import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.drlg.*;
import com.d2moo.common.util.D2FileReader;
import com.d2moo.common.util.D2MemoryPool;
import com.riiablo.RiiabloTest;
import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Uses actual MPQ DS1/DT1 data; a wall-only export is not a terrain pass. */
class Act5IceNativeTerrainTest extends RiiabloTest {
  @Test void everyIceCaveIncludingTheThreeBranchesExportsNativeFloors() {
    D2DrlgStrc drlg = null;
    try {
      DataTbls.setLevelDefBinCache(Act1D2MOOLayoutBridge.buildLevelDefCache(0, -1));
      DataTbls.setLevelTypesTxtCache(Act1D2MOOLayoutBridge.buildLevelTypesCache());
      D2FileReader.ArchiveReader archive = Act1D2MOOLayoutBridge::readArchiveFile;
      DataTbls.loadLvlPrestTxt(archive, 0);
      DataTbls.loadLvlSubTxt(archive);
      DataTbls.loadLvlMazeTxt(archive);
      DataTbls.loadLvlWarpTxt(archive);
      D2DrlgAct act = new D2DrlgAct();
      act.setAct(D2C_Acts.ACT_V);
      act.setTownId(D2LevelIds.LEVEL_HARROGATH);
      act.setPMemPool(new D2MemoryPool());
      drlg = DrlgDrlg.allocDrlg(act, D2C_Acts.ACT_V, archive, 0x171A6100,
          D2LevelIds.LEVEL_HARROGATH, 0, null, (byte) 0, null, null);
      assertNotNull(drlg);
      for (int id : new int[] {113, 114, 115, 116, 118, 119}) {
        D2DrlgLevel level = DrlgDrlg.getLevel(drlg, id);
        assertNotNull(level);
        if (level.getFirstRoomEx() == null) DrlgDrlg.initLevel(level);
        D2MooTileApplier applier = new D2MooTileApplier();
        TileGrid grid = new TileGrid(level.getLevelCoords().getNWidth(),
            level.getLevelCoords().getNHeight());
        applier.putGrid(id, grid);
        DrlgExport.exportLevelTiles(drlg, id, applier);
        assertTrue(applier.getLastExportedFloorCount() > 0, "native floor level=" + id);
        java.util.Set<Integer> destinations = new java.util.HashSet<>();
        int[] nativeVis = DrlgDrlgRoom.getVisArrayFromLevelId(drlg, id);
        int[] nativeWarps = DrlgDrlgWarp.getWarpIdArrayFromLevelId(drlg, id);
        DrlgExport.exportLevelPresetUnits(drlg, id,
            (levelId, roomId, type, index, mode, x, y, raw, spawned, external, source) -> {
              if (type == D2UnitTypes.UNIT_TILE) for (int slot = 0; slot < 8; slot++) {
                if (nativeWarps[slot] == index && index >= 0) destinations.add(nativeVis[slot]);
              }
            });
        // Warp triggers can be exported as special DS1 walls instead of
        // UNIT_TILE entities. The production bridge consumes both forms.
        int[] vis = DrlgDrlgRoom.getVisArrayFromLevelId(drlg, id);
        for (int layer = 0; layer < TileGrid.MAX_WALL_LAYERS; layer++) {
          for (int y = 0; y < grid.height; y++) for (int x = 0; x < grid.width; x++) {
            int tile = grid.wallIds[layer][y][x];
            if (!com.riiablo.map.Map.ID.isWarp(tile)) continue;
            int slot = com.riiablo.map.DT1.Tile.Index.mainIndex(tile);
            if (slot >= 0 && slot < vis.length) destinations.add(vis[slot]);
          }
        }
        int branch = id == 113 ? 114 : id == 115 ? 116 : id == 118 ? 119 : -1;
        if (branch != -1) assertTrue(destinations.contains(branch),
            "missing native branch marker level=" + id + " destinations=" + destinations);
        System.out.println("[ICE_NATIVE] level=" + id + " floors="
            + applier.getLastExportedFloorCount() + " destinations=" + destinations);
      }
    } finally {
      if (drlg != null) DrlgDrlg.freeDrlg(drlg);
      Act1D2MOOLayoutBridge.releaseDataTables();
    }
  }
}
