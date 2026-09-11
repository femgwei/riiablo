package com.riiablo.map.d2moo;

import com.badlogic.gdx.Gdx;
import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.drlg.D2C_Acts;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgPresetRoomStrc;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2UnitTypes;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.DrlgDrlg;
import com.d2moo.common.drlg.DrlgExport;
import com.d2moo.common.util.D2FileReader;
import com.d2moo.common.util.D2MemoryPool;
import com.riiablo.map.Map;
import com.riiablo.map.Map.Zone;
import com.riiablo.drlg.TileGrid;

import java.util.IdentityHashMap;

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
  /** Act III progression levels exported by the bridge, including Durance. */
  private static final int[] NATIVE_LEVELS = {
      D2LevelIds.LEVEL_KURASTDOCKTOWN,
      D2LevelIds.LEVEL_SPIDERFOREST,
      D2LevelIds.LEVEL_GREATMARSH,
      D2LevelIds.LEVEL_FLAYERJUNGLE,
      D2LevelIds.LEVEL_LOWERKURAST,
      D2LevelIds.LEVEL_KURASTBAZAAR,
      D2LevelIds.LEVEL_UPPERKURAST,
      D2LevelIds.LEVEL_KURASTCAUSEWAY,
      D2LevelIds.LEVEL_TRAVINCAL,
      D2LevelIds.LEVEL_SPIDERCAVE,
      D2LevelIds.LEVEL_SPIDERCAVERN,
      D2LevelIds.LEVEL_DURANCEOFHATELEVEL1,
      D2LevelIds.LEVEL_DURANCEOFHATELEVEL2,
      D2LevelIds.LEVEL_DURANCEOFHATELEVEL3
  };

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
      D2DrlgLevel townLevel = DrlgDrlg.getLevel(drlg, FIRST_LEVEL);
      Zone townZone = findZone(map, FIRST_LEVEL);
      int nativeTownX = townLevel != null && townLevel.getLevelCoords() != null
          ? townLevel.getLevelCoords().getNPosX() : 0;
      int nativeTownY = townLevel != null && townLevel.getLevelCoords() != null
          ? townLevel.getLevelCoords().getNPosY() : 0;
      int worldTownX = townZone == null ? 0 : townZone.x();
      int worldTownY = townZone == null ? 0 : townZone.y();
      int exportedLevels = 0;
      for (int levelId : NATIVE_LEVELS) {
        Zone zone = findZone(map, levelId);
        D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
        if (zone == null || level == null) continue;
        if (level.getFirstRoomEx() == null) DrlgDrlg.initLevel(level);
        if (level.getLevelCoords() == null || level.getFirstRoomEx() == null) continue;
        int width = level.getLevelCoords().getNWidth();
        int height = level.getLevelCoords().getNHeight();
        if (width <= 0 || height <= 0) continue;

        // D2MOO coordinates are tile-space.  Riiablo Zone positions are
        // subtiles, so keep the town anchor and project every other level
        // relative to the native town origin.  This removes the old custom
        // vertical stacking that could overlap jungle and Kurast regions.
        int nativeX = level.getLevelCoords().getNPosX();
        int nativeY = level.getLevelCoords().getNPosY();
        zone.setPosition(worldTownX + (nativeX - nativeTownX)
                * com.riiablo.map.DT1.Tile.SUBTILE_SIZE,
            worldTownY + (nativeY - nativeTownY)
                * com.riiablo.map.DT1.Tile.SUBTILE_SIZE);
        TileGrid grid = new TileGrid(width, height);
        applier.putGrid(levelId, grid);
        int floors = DrlgExport.exportLevelTiles(drlg, levelId, applier);
        int[] rawObjects = {0, 0};
        com.badlogic.gdx.utils.Array<Map.NativeObject> exportedObjects =
            new com.badlogic.gdx.utils.Array<>();
        int presetUnits = DrlgExport.exportLevelPresetUnits(drlg, levelId,
            (exportLevelId, unitType, index, mode, x, y, ds1Raw, spawned) -> {
              if (unitType != D2UnitTypes.UNIT_OBJECT) return;
              rawObjects[0]++;
              if (x < 0 || y < 0
                  || x >= width * com.riiablo.map.DT1.Tile.SUBTILE_SIZE
                  || y >= height * com.riiablo.map.DT1.Tile.SUBTILE_SIZE) {
                rawObjects[1]++;
                return;
              }
              exportedObjects.add(new Map.NativeObject(
                  index, mode, x, y, ds1Raw, spawned));
            });
        int dt1Mask = DrlgExport.collectLevelDt1Mask(drlg, levelId);
        if (floors > 0) {
          projectRooms(level, zone);
          zone.getNativeObjects().addAll(exportedObjects);
          zone.setNativeTileGrid(grid, dt1Mask);
          exportedLevels++;
        } else {
          // Do not leave a partial native topology behind when a resource
          // archive is incomplete: MapManager would otherwise wait for room
          // activation and skip the compatibility object path.
          zone.getRoomsEx().clear();
          zone.getNativeObjects().clear();
        }
        if (Gdx.app != null) {
          Gdx.app.log(TAG, String.format(
              "Act3 native level=%d rooms=%d tiles=%dx%d floors=%d walls=%d shadows=%d dt1Mask=0x%X",
              levelId, level.getRooms(), width, height, floors,
              applier.getExportedWallCount(), applier.getExportedShadowCount(), dt1Mask)
              + String.format(" objects=%d/%d invalidObjectPos=%d",
                  presetUnits, rawObjects[0], rawObjects[1]));
        }
        applier.resetLastExportedFloorCount();
      }
      if (Gdx.app != null) Gdx.app.log(TAG,
          String.format("Act3 native terrain summary: levels=%d/%d", exportedLevels,
              NATIVE_LEVELS.length));
      return exportedLevels > 0;
    } catch (Throwable t) {
      if (Gdx.app != null) Gdx.app.error(TAG,
          "Native Act III generation stopped after a per-level export failure; keeping completed levels",
          t);
      // A later preset (currently Travincal on some 1.10f seeds) may fail
      // after earlier levels have been exported successfully. Preserve those
      // completed native grids; the failing level remains on the compatibility
      // builder and is explicitly reported in the log instead of invalidating
      // the whole act.
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

  private static void projectRooms(D2DrlgLevel level, Zone zone) {
    int levelX = level.getLevelCoords().getNPosX();
    int levelY = level.getLevelCoords().getNPosY();
    zone.getRoomsEx().clear();
    IdentityHashMap<D2DrlgRoom, Integer> ids = new IdentityHashMap<>();
    java.util.ArrayList<D2DrlgRoom> nativeRooms = new java.util.ArrayList<>();
    for (D2DrlgRoom room = level.getFirstRoomEx(); room != null;
        room = room.getDrlgRoomNext()) {
      ids.put(room, nativeRooms.size());
      nativeRooms.add(room);
      int localX = room.getNTileXPos() - levelX;
      int localY = room.getNTileYPos() - levelY;
      Map.RoomEx projected = zone.addRoomEx(
          zone.x() + localX * com.riiablo.map.DT1.Tile.SUBTILE_SIZE,
          zone.y() + localY * com.riiablo.map.DT1.Tile.SUBTILE_SIZE,
          room.getNTileWidth() * com.riiablo.map.DT1.Tile.SUBTILE_SIZE,
          room.getNTileHeight() * com.riiablo.map.DT1.Tile.SUBTILE_SIZE);
      Object maze = room.getMazeOrOutdoor();
      if (maze instanceof D2DrlgPresetRoomStrc) {
        D2DrlgPresetRoomStrc preset = (D2DrlgPresetRoomStrc) maze;
        projected.setPreset(preset.getNLevelPrest(), preset.getNPickedFile());
      }
    }
    for (int i = 0; i < nativeRooms.size(); i++) {
      D2DrlgRoom room = nativeRooms.get(i);
      D2DrlgRoom[] near = room.getPpRoomsNear();
      int count = near == null ? 0 : Math.min(room.getNRoomsNear(), near.length);
      com.badlogic.gdx.utils.IntArray adjacent = new com.badlogic.gdx.utils.IntArray(count);
      for (int j = 0; j < count; j++) {
        Integer id = near[j] == null ? null : ids.get(near[j]);
        if (id != null && id != i) adjacent.add(id);
      }
      zone.getRoomsEx().get(i).setAdjacentRoomIds(adjacent.toArray());
    }
  }

  private static final class RiiabloAccess {
    static boolean mpqsMissing() {
      return com.riiablo.Riiablo.mpqs == null;
    }
  }
}
