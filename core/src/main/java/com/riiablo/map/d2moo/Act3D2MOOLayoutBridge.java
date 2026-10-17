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
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2MemoryPool;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.SuperUniques;
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
      D2LevelIds.LEVEL_SWAMPYPITLVL1,
      D2LevelIds.LEVEL_SWAMPYPITLVL2,
      D2LevelIds.LEVEL_FLAYERDUNGEONLVL1,
      D2LevelIds.LEVEL_FLAYERDUNGEONLVL2,
      D2LevelIds.LEVEL_SWAMPYPITLVL3,
      D2LevelIds.LEVEL_FLAYERDUNGEONLVL3,
      D2LevelIds.LEVEL_SEWERSA3LEV1,
      D2LevelIds.LEVEL_SEWERSA3LEV2,
      D2LevelIds.LEVEL_RUINEDTEMPLE,
      D2LevelIds.LEVEL_DISUSEDFANE,
      D2LevelIds.LEVEL_FORGOTTENRELIQUARY,
      D2LevelIds.LEVEL_FORGOTTENTEMPLE,
      D2LevelIds.LEVEL_RUINEDFANE,
      D2LevelIds.LEVEL_DISUSEDRELIQUARY,
      D2LevelIds.LEVEL_DURANCEOFHATELEVEL1,
      D2LevelIds.LEVEL_DURANCEOFHATELEVEL2,
      D2LevelIds.LEVEL_DURANCEOFHATELEVEL3
  };

  private Act3D2MOOLayoutBridge() {}

  public static boolean populateZones(int gameSeed, int diff, Map map) {
    if (map == null || D2MOOLayoutContext.mpqs() == null) return false;
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
      D2Log.debug("ACT3_BRIDGE alloc levels town=%s", DrlgDrlg.getLevel(drlg, FIRST_LEVEL) != null);

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
        D2Log.debug("ACT3_BRIDGE level=%d zone=%s native=%s rooms=%s coords=%s",
            levelId, zone != null, level != null,
            level != null ? level.getFirstRoomEx() != null : false,
            level != null && level.getLevelCoords() != null
                ? String.format("(%d,%d %dx%d)", level.getLevelCoords().getNPosX(),
                    level.getLevelCoords().getNPosY(), level.getLevelCoords().getNWidth(),
                    level.getLevelCoords().getNHeight()) : "none");
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
        if (Gdx.app != null && (levelId >= D2LevelIds.LEVEL_SWAMPYPITLVL1
            && levelId <= D2LevelIds.LEVEL_DISUSEDRELIQUARY)) {
          com.d2moo.common.drlg.D2DrlgWarp warp =
              DrlgDrlg.getDrlgWarpFromLevelId(drlg, levelId);
          Gdx.app.log(TAG, "Act3 native level warp table: level=" + levelId
              + " vis=" + java.util.Arrays.toString(warp == null ? null : warp.getNVis())
              + " warp=" + java.util.Arrays.toString(warp == null ? null : warp.getNWarp()));
        }
        zone.setPosition(worldTownX + (nativeX - nativeTownX)
                * com.riiablo.map.DT1.Tile.SUBTILE_SIZE,
            worldTownY + (nativeY - nativeTownY)
                * com.riiablo.map.DT1.Tile.SUBTILE_SIZE);
        TileGrid grid = new TileGrid(width, height);
        applier.putGrid(levelId, grid);
        int floors = DrlgExport.exportLevelTiles(drlg, levelId, applier);
        int dt1Mask = DrlgExport.collectLevelDt1Mask(drlg, levelId);
        if (floors > 0) {
          zone.setNativeTileGrid(grid, dt1Mask);
          if (zone.isTown()) {
            NativeTownPortalAnchor.capture(level, zone);
            Object preset = level.getFirstRoomEx().getMazeOrOutdoor();
            if (preset instanceof D2DrlgPresetRoomStrc) {
              D2DrlgPresetRoomStrc selected = (D2DrlgPresetRoomStrc) preset;
              zone.selectNativeTownPreset(selected.getNLevelPrest(), selected.getNPickedFile());
            }
          }
        }
        int[] rawObjects = {0, 0};
        int[] nativeWarpUnits = {0, 0};
        com.badlogic.gdx.utils.Array<Map.NativeObject> exportedObjects =
            new com.badlogic.gdx.utils.Array<>();
        com.badlogic.gdx.utils.Array<Map.NativeMonster> exportedMonsters =
            new com.badlogic.gdx.utils.Array<>();
        String[] townMonsterSource = {null};
        boolean[] townMonsterSourcesMatch = {true};
        int queuedMonsters = 0;
        int[] exportedVis = com.d2moo.common.drlg.DrlgDrlgRoom.getVisArrayFromLevelId(drlg, levelId);
        int[] exportedWarps = com.d2moo.common.drlg.DrlgDrlgWarp.getWarpIdArrayFromLevelId(drlg, levelId);
        int presetUnits = DrlgExport.exportLevelPresetUnits(drlg, levelId,
            (exportLevelId, roomId, unitType, index, mode, x, y, ds1Raw, spawned,
                externalEntity, sourceFile) -> {
              if (unitType == D2UnitTypes.UNIT_TILE) {
                // AddWarp exports the LvlWarp definition Id, not VisN.
                int slot = NativeWarpSlotResolver.resolve(level, exportedVis, exportedWarps, roomId, index);
                if (slot >= 0) {
                  zone.addNativeWarpMarker(slot, x, y);
                  nativeWarpUnits[0]++;
                }
                nativeWarpUnits[1]++;
                return;
              }
              if (unitType == D2UnitTypes.UNIT_MONSTER) {
                if (zone.isTown() && externalEntity) {
                  if (sourceFile == null || sourceFile.isEmpty()) {
                    townMonsterSourcesMatch[0] = false;
                  } else if (townMonsterSource[0] == null) {
                    townMonsterSource[0] = sourceFile;
                  } else if (!sameSource(townMonsterSource[0], sourceFile)) {
                    townMonsterSourcesMatch[0] = false;
                  }
                }
                if (externalEntity && x >= 0 && y >= 0
                    && x < width * com.riiablo.map.DT1.Tile.SUBTILE_SIZE
                    && y < height * com.riiablo.map.DT1.Tile.SUBTILE_SIZE) {
                  NativeMonsterResolution resolved = resolveNativeMonster(index);
                  exportedMonsters.add(new Map.NativeMonster(
                      roomId, resolved.monsterId, mode, x, y,
                      resolved.superUniqueId, resolved.superUniqueKey, spawned));
                }
                return;
              }
              if (unitType != D2UnitTypes.UNIT_OBJECT) return;
              rawObjects[0]++;
              if (x < 0 || y < 0
                  || x >= width * com.riiablo.map.DT1.Tile.SUBTILE_SIZE
                  || y >= height * com.riiablo.map.DT1.Tile.SUBTILE_SIZE) {
                rawObjects[1]++;
                return;
              }
              exportedObjects.add(new Map.NativeObject(
                  roomId, index, mode, x, y, ds1Raw, spawned, sourceFile, externalEntity));
            });
        if (floors > 0) {
          projectRooms(level, zone);
          com.riiablo.map.NativeMonsterSpawnExclusion.capture(level, zone);
          queuedMonsters = queueNativeMonsters(zone, exportedMonsters);
          zone.getNativeObjects().addAll(exportedObjects);
          zone.getNativeMonsters().addAll(exportedMonsters);
          if (zone.isTown() && !exportedMonsters.isEmpty()
              && townMonsterSourcesMatch[0] && townMonsterSource[0] != null
              && sameSource(zone.presetSourceFile(), townMonsterSource[0])) {
            // The native town export owns dynamic NPCs from this exact DS1;
            // keep static objects on the compatibility path, but do not create
            // a second copy of the native UNIT_MONSTER entries.
            zone.replaceNativeTownMonsters(townMonsterSource[0], exportedMonsters);
          }
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
              + String.format(" objects=%d/%d invalidObjectPos=%d unitTileWarp=%d/%d",
                  presetUnits, rawObjects[0], rawObjects[1],
                  nativeWarpUnits[0], nativeWarpUnits[1])
              + String.format(" queuedMonsters=%d", queuedMonsters));
        }
        D2Log.debug("ACT3_BRIDGE exported level=%d floors=%d rooms=%d objects=%d nativeObjects=%d",
            levelId, floors, level.getRooms(), rawObjects[0], zone.getNativeObjects().size);
        applier.resetLastExportedFloorCount();
      }
      if (Gdx.app != null) Gdx.app.log(TAG,
          String.format("Act3 native terrain summary: levels=%d/%d", exportedLevels,
              NATIVE_LEVELS.length));
      int seams = NativeLevelSeamAnchor.installOutdoorSeams(map, drlg);
      if (Gdx.app != null) Gdx.app.log(TAG, "A3 native outdoor seams installed=" + seams);
      NativeTownExit.capture(map, drlg, FIRST_LEVEL);
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

  public static boolean populateZones(D2MOOLayoutContext context, int gameSeed, int diff, Map map) {
    try (D2MOOLayoutContext.Scope ignored = context.activate()) {
      return populateZones(gameSeed, diff, map);
    }
  }

  private static Zone findZone(Map map, int levelId) {
    for (Zone zone : map.getZones()) {
      if (zone != null && zone.level != null && zone.level.Id == levelId) return zone;
    }
    return null;
  }

  /** MPQ file names are case-insensitive and may include the tile-root prefix. */
  private static boolean sameSource(String a, String b) {
    if (a == null || b == null) return false;
    return normalizeSource(a).equals(normalizeSource(b));
  }

  private static String normalizeSource(String source) {
    String normalized = source.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
    String root = "data/global/tiles/";
    return normalized.startsWith(root) ? normalized.substring(root.length()) : normalized;
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
      projected.setNativePopulationMetadata(room);
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

  /**
   * Native D2Game queues preset monsters when their RoomEx enters the client
   * ring. Act III used to retain the audit list but never attached those units
   * to a projected RoomEx, so every non-town native monster silently vanished.
   */
  static int queueNativeMonsters(Zone zone,
      com.badlogic.gdx.utils.Array<Map.NativeMonster> monsters) {
    if (zone == null || zone.isTown() || monsters == null) return 0;
    int queued = 0;
    for (Map.NativeMonster monster : monsters) {
      if (monster == null || monster.spawned) continue;
      Map.RoomEx room = monster.roomId >= 0 && monster.roomId < zone.getRoomsEx().size
          ? zone.getRoomsEx().get(monster.roomId) : null;
      float worldX = zone.x() + monster.x;
      float worldY = zone.y() + monster.y;
      if (room == null || !room.contains(worldX, worldY)) {
        room = zone.findRoomEx(worldX, worldY);
      }
      if (room == null) {
        if (Gdx.app != null) Gdx.app.error(TAG, String.format(
            "Act3 native monster has no RoomEx: level=%d room=%d monster=%d pos=(%.1f,%.1f)",
            zone.levelId(), monster.roomId, monster.monsterId, monster.x, monster.y));
        continue;
      }
      room.addMonsterSpawn(monster.monsterId, worldX, worldY, -1, false,
          monster.superUniqueId, monster.superUniqueKey);
      queued++;
    }
    return queued;
  }

  /**
   * Resolves an Act III UNIT_MONSTER class id and preserves native
   * SuperUniques identity.  D2MOO emits both MonPreset placement ids and the
   * encoded {@code monstatsCount + superUniquesCount + hcIdx} form; the latter
   * is used by preset bosses that have no MonPreset row at the exported site.
   */
  static NativeMonsterResolution resolveNativeMonster(int index) {
    if (D2MOOLayoutContext.files() == null || D2MOOLayoutContext.files().monstats == null) {
      return new NativeMonsterResolution(index, -1, null);
    }
    String placement = null;
    if (D2MOOLayoutContext.files().MonPreset != null) {
      try {
        placement = D2MOOLayoutContext.files().MonPreset.getPlace(3, index);
      } catch (RuntimeException ignored) {
        // Keep the direct class-id lookup for native units without a MonPreset row.
      }
    }
    MonStats.Entry monster = com.riiablo.map.Act1MapBuilderD2MOD
        .resolveD2MooPresetMonster(index, placement);
    SuperUniques.Entry superUnique = placement == null || D2MOOLayoutContext.files().SuperUniques == null
        ? null : D2MOOLayoutContext.files().SuperUniques.get(placement);
    if (monster == null && superUnique != null) {
      monster = D2MOOLayoutContext.files().monstats.get(superUnique.MonClass);
    }
    if (monster == null && superUnique == null && D2MOOLayoutContext.files().SuperUniques != null) {
      // D2MOO encodes preset SuperUniques as
      // MonStatsCount + hcIdx.
      int suIndex = index - D2MOOLayoutContext.files().monstats.size();
      superUnique = D2MOOLayoutContext.files().SuperUniques.get(suIndex);
      if (superUnique != null) {
        monster = D2MOOLayoutContext.files().monstats.get(superUnique.MonClass);
      }
    }
    return new NativeMonsterResolution(monster == null ? index : monster.hcIdx,
        superUnique == null ? -1 : superUnique.hcIdx,
        superUnique == null ? null : superUnique.Superunique);
  }

  static final class NativeMonsterResolution {
    final int monsterId;
    final int superUniqueId;
    final String superUniqueKey;

    NativeMonsterResolution(int monsterId, int superUniqueId, String superUniqueKey) {
      this.monsterId = monsterId;
      this.superUniqueId = superUniqueId;
      this.superUniqueKey = superUniqueKey;
    }
  }

  private static final class RiiabloAccess {
    static boolean mpqsMissing() {
      return D2MOOLayoutContext.mpqs() == null;
    }
  }
}
