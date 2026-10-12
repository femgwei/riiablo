package com.riiablo.map.d2moo;

import com.badlogic.gdx.Gdx;
import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.drlg.D2C_Acts;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgPresetRoomStrc;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2UnitTypes;
import com.d2moo.common.drlg.DrlgDrlg;
import com.d2moo.common.drlg.DrlgExport;
import com.d2moo.common.util.D2FileReader;
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2MemoryPool;
import com.riiablo.drlg.TileGrid;
import com.riiablo.map.Map;
import com.riiablo.map.Map.Zone;

import java.util.IdentityHashMap;

/** Resource-gated D2MOO DRLG bridge for Act IV terrain and preset objects. */
public final class Act4D2MOOLayoutBridge {
  private static final String TAG = "Act4D2MOOLayoutBridge";
  private static final int TOWN = D2LevelIds.LEVEL_THEPANDEMONIUMFORTRESS;

  /**
   * The native Act IV outdoor linker creates the four outdoor levels and its
   * independent Chaos Sanctuary linker creates 108.  River of Flame is a
   * maze level, but is included here because it is part of the playable Act IV
   * progression and receives the same native RoomEx/tile export.
   */
  static final int[] LEVELS = {
      TOWN,
      D2LevelIds.LEVEL_OUTERSTEPPES,
      D2LevelIds.LEVEL_PLAINSOFDESPAIR,
      D2LevelIds.LEVEL_CITYOFTHEDAMNED,
      D2LevelIds.LEVEL_RIVEROFFLAME,
      D2LevelIds.LEVEL_CHAOSSANCTUM
  };

  private Act4D2MOOLayoutBridge() {}

  /**
   * Replaces compatibility terrain only when the complete MPQ-backed native
   * DRLG can be built.  Any missing level or export keeps its existing Zone,
   * so headless/reduced-resource tests remain playable.
   */
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
      act.setAct(D2C_Acts.ACT_IV);
      act.setTownId(TOWN);
      act.setPMemPool(new D2MemoryPool());
      drlg = DrlgDrlg.allocDrlg(act, D2C_Acts.ACT_IV, archive, gameSeed,
          TOWN, 0, null, (byte) diff, null, null);
      if (drlg == null) return false;

      Zone town = findZone(map, TOWN);
      D2DrlgLevel nativeTown = DrlgDrlg.getLevel(drlg, TOWN);
      int townX = nativeTown != null && nativeTown.getLevelCoords() != null
          ? nativeTown.getLevelCoords().getNPosX() : 0;
      int townY = nativeTown != null && nativeTown.getLevelCoords() != null
          ? nativeTown.getLevelCoords().getNPosY() : 0;
      int worldX = town == null ? 0 : town.x();
      int worldY = town == null ? 0 : town.y();
      D2MooTileApplier applier = new D2MooTileApplier();
      int exportedLevels = 0;

      for (int levelId : LEVELS) {
        Zone zone = findZone(map, levelId);
        D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
        if (zone == null || level == null) {
          logProbe(levelId, zone, level);
          continue;
        }
        if (level.getFirstRoomEx() == null) DrlgDrlg.initLevel(level);
        if (level.getFirstRoomEx() == null || level.getLevelCoords() == null) continue;
        int width = level.getLevelCoords().getNWidth();
        int height = level.getLevelCoords().getNHeight();
        if (width <= 0 || height <= 0) continue;

        zone.setPosition(worldX + (level.getLevelCoords().getNPosX() - townX)
                * com.riiablo.map.DT1.Tile.SUBTILE_SIZE,
            worldY + (level.getLevelCoords().getNPosY() - townY)
                * com.riiablo.map.DT1.Tile.SUBTILE_SIZE);
        TileGrid grid = new TileGrid(width, height);
        applier.putGrid(levelId, grid);
        int floors = DrlgExport.exportLevelTiles(drlg, levelId, applier);
        int dt1Mask = DrlgExport.collectLevelDt1Mask(drlg, levelId);
        if (floors <= 0) {
          if (Gdx.app != null) Gdx.app.log(TAG,
              "A4 native level=" + levelId + " has no floor; keeping compatibility Zone");
          continue;
        }

        projectRooms(level, zone);
        int[] objectCounts = {0, 0};
        int presetUnits = DrlgExport.exportLevelPresetUnits(drlg, levelId,
            (exportLevelId, roomId, unitType, index, mode, x, y, ds1Raw, spawned,
                externalEntity, sourceFile) -> {
              if (unitType != D2UnitTypes.UNIT_OBJECT) return;
              objectCounts[0]++;
              if (x < 0 || y < 0
                  || x >= width * com.riiablo.map.DT1.Tile.SUBTILE_SIZE
                  || y >= height * com.riiablo.map.DT1.Tile.SUBTILE_SIZE) {
                objectCounts[1]++;
                return;
              }
              zone.getNativeObjects().add(new Map.NativeObject(roomId, index, mode, x, y,
                  ds1Raw, spawned, sourceFile, externalEntity));
            });
        zone.setNativeTileGrid(grid, dt1Mask);
        exportedLevels++;
        if (Gdx.app != null) Gdx.app.log(TAG, String.format(
            "A4 native level=%d rooms=%d tiles=%dx%d floors=%d walls=%d shadows=%d dt1Mask=0x%X objects=%d invalid=%d",
            levelId, level.getRooms(), width, height, floors,
            applier.getExportedWallCount(), applier.getExportedShadowCount(), dt1Mask,
            presetUnits, objectCounts[1]));
        D2Log.debug("ACT4_BRIDGE exported level=%d floors=%d rooms=%d objects=%d",
            levelId, floors, level.getRooms(), presetUnits);
        applier.resetLastExportedFloorCount();
      }
      if (Gdx.app != null) Gdx.app.log(TAG, "A4 native terrain summary levels="
          + exportedLevels + "/" + LEVELS.length);
      return exportedLevels > 0;
    } catch (Throwable t) {
      if (Gdx.app != null) Gdx.app.error(TAG,
          "Native Act IV export unavailable; keeping compatibility Zones", t);
      return false;
    } finally {
      if (drlg != null) DrlgDrlg.freeDrlg(drlg);
      Act1D2MOOLayoutBridge.releaseDataTables();
    }
  }

  private static void logProbe(int levelId, Zone zone, D2DrlgLevel level) {
    if (Gdx.app != null) Gdx.app.debug(TAG, "A4 native probe level=" + levelId
        + " zone=" + (zone != null) + " native=" + (level != null));
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
      Map.RoomEx projected = zone.addRoomEx(
          zone.x() + (room.getNTileXPos() - levelX)
              * com.riiablo.map.DT1.Tile.SUBTILE_SIZE,
          zone.y() + (room.getNTileYPos() - levelY)
              * com.riiablo.map.DT1.Tile.SUBTILE_SIZE,
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
    static boolean mpqsMissing() { return com.riiablo.Riiablo.mpqs == null; }
  }
}
