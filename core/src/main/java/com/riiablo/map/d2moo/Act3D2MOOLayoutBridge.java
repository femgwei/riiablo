package com.riiablo.map.d2moo;

import com.badlogic.gdx.Gdx;
import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.drlg.D2C_Acts;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.DrlgDrlg;
import com.d2moo.common.drlg.DrlgExport;
import com.d2moo.common.util.D2FileReader;
import com.d2moo.common.util.D2MemoryPool;
import com.riiablo.map.Map;
import com.riiablo.map.Map.Zone;
import com.riiablo.drlg.TileGrid;

/**
 * Resource-gated Act III bridge to the native D2MOO DRLG.
 *
 * <p>The compatibility builder still owns Zone allocation and monster setup;
 * this bridge only replaces terrain/collision data when the user's MPQ set is
 * available.  A missing or incomplete resource set therefore keeps the
 * previous generator usable instead of making headless tests fail.</p>
 */
public final class Act3D2MOOLayoutBridge {
  private static final String TAG = "Act3D2MOOLayoutBridge";
  private static final int FIRST_LEVEL = D2LevelIds.LEVEL_KURASTDOCKTOWN;
  private static final int LAST_LEVEL = D2LevelIds.LEVEL_TRAVINCAL;

  private Act3D2MOOLayoutBridge() {}

  public static boolean populateZones(int gameSeed, int diff, Map map) {
    if (map == null || RiiabloAccess.mpqsMissing()) return false;
    D2DrlgStrc drlg = null;
    try {
      DataTbls.setLevelDefBinCache(Act1D2MOOLayoutBridge.buildLevelDefCache(diff, -1));
      DataTbls.setLevelTypesTxtCache(Act1D2MOOLayoutBridge.buildLevelTypesCache());
      D2FileReader.ArchiveReader archive = Act1D2MOOLayoutBridge::readArchiveFile;
      DataTbls.loadLvlPrestTxt(archive, 0);
      DataTbls.loadLvlSubTxt(archive);
      DataTbls.loadLvlMazeTxt(archive);
      DataTbls.loadLvlWarpTxt(archive);

      D2DrlgAct act = new D2DrlgAct();
      act.setAct(D2C_Acts.ACT_III);
      act.setTownId(FIRST_LEVEL);
      act.setPMemPool(new D2MemoryPool());
      drlg = DrlgDrlg.allocDrlg(act, D2C_Acts.ACT_III, archive, gameSeed,
          FIRST_LEVEL, 0, null, (byte) diff, null, null);
      if (drlg == null) return false;

      D2MooTileApplier applier = new D2MooTileApplier();
      int exportedLevels = 0;
      for (int levelId = FIRST_LEVEL; levelId <= LAST_LEVEL; levelId++) {
        Zone zone = findZone(map, levelId);
        D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
        if (zone == null || level == null) continue;
        if (level.getFirstRoomEx() == null) DrlgDrlg.initLevel(level);
        if (level.getLevelCoords() == null || level.getFirstRoomEx() == null) continue;
        int width = level.getLevelCoords().getNWidth();
        int height = level.getLevelCoords().getNHeight();
        if (width <= 0 || height <= 0) continue;
        TileGrid grid = new TileGrid(width, height);
        applier.putGrid(levelId, grid);
        int floors = DrlgExport.exportLevelTiles(drlg, levelId, applier);
        int dt1Mask = DrlgExport.collectLevelDt1Mask(drlg, levelId);
        if (floors > 0) {
          zone.setNativeTileGrid(grid, dt1Mask);
          exportedLevels++;
        }
        if (Gdx.app != null) {
          Gdx.app.log(TAG, String.format(
              "Act3 native level=%d rooms=%d tiles=%dx%d floors=%d walls=%d shadows=%d dt1Mask=0x%X",
              levelId, level.getRooms(), width, height, floors,
              applier.getExportedWallCount(), applier.getExportedShadowCount(), dt1Mask));
        }
        applier.resetLastExportedFloorCount();
      }
      if (Gdx.app != null) Gdx.app.log(TAG,
          String.format("Act3 native terrain summary: levels=%d/%d", exportedLevels,
              LAST_LEVEL - FIRST_LEVEL + 1));
      return exportedLevels > 0;
    } catch (Throwable t) {
      if (Gdx.app != null) Gdx.app.error(TAG,
          "Native Act III generation unavailable; keeping compatibility terrain", t);
      return false;
    } finally {
      if (drlg != null) DrlgDrlg.freeDrlg(drlg);
      Act1D2MOOLayoutBridge.releaseDataTables();
    }
  }

  private static Zone findZone(Map map, int levelId) {
    for (Zone zone : map.getZones()) {
      if (zone != null && zone.level != null && zone.level.Id == levelId) return zone;
    }
    return null;
  }

  private static final class RiiabloAccess {
    static boolean mpqsMissing() {
      return com.riiablo.Riiablo.mpqs == null;
    }
  }
}
