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
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.SuperUniques;
import com.riiablo.drlg.TileGrid;
import com.riiablo.map.Act1MapBuilderD2MOD;
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
    return populateZones(gameSeed, diff, map, D2C_Acts.ACT_IV, TOWN, LEVELS);
  }

  /** Shared native terrain export; coordinates remain relative to the act's town. */
  public static boolean populateZones(int gameSeed, int diff, Map map,
      byte actId, int townId, int[] levelIds) {
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
      act.setAct(actId);
      act.setTownId(townId);
      act.setPMemPool(new D2MemoryPool());
      drlg = DrlgDrlg.allocDrlg(act, actId, archive, gameSeed,
          townId, 0, null, (byte) diff, null, null);
      if (drlg == null) return false;

      Zone town = findZone(map, townId);
      D2DrlgLevel nativeTown = DrlgDrlg.getLevel(drlg, townId);
      int townX = nativeTown != null && nativeTown.getLevelCoords() != null
          ? nativeTown.getLevelCoords().getNPosX() : 0;
      int townY = nativeTown != null && nativeTown.getLevelCoords() != null
          ? nativeTown.getLevelCoords().getNPosY() : 0;
      int worldX = town == null ? 0 : town.x();
      int worldY = town == null ? 0 : town.y();
      D2MooTileApplier applier = new D2MooTileApplier();
      int exportedLevels = 0;

      // Resolve every level before projecting coordinates/orths: the lava
      // maze places its final bridge relative to the independent Chaos level.
      for (int levelId : levelIds) {
        D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
        if (level != null && level.getFirstRoomEx() == null) DrlgDrlg.initLevel(level);
      }

      for (int levelId : levelIds) {
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
              "A" + (actId + 1) + " native level=" + levelId + " has no floor; keeping compatibility Zone");
          continue;
        }

        zone.setNativeTileGrid(grid, dt1Mask);
        projectRooms(level, zone);
        if (actId == D2C_Acts.ACT_IV) {
          int[] vis = com.d2moo.common.drlg.DrlgDrlgRoom.getVisArrayFromLevelId(drlg, levelId);
          int[] warps = com.d2moo.common.drlg.DrlgDrlgWarp.getWarpIdArrayFromLevelId(drlg, levelId);
          for (int slot = 0; vis != null && warps != null
              && slot < Math.min(8, Math.min(vis.length, warps.length)); slot++) {
            if (vis[slot] <= 0) continue;
            map.addWarpDestinationOverride(levelId, slot, vis[slot]);
            if (warps[slot] != -1) continue;
            int[] anchor = NativeLevelSeamAnchor.resolve(level, DrlgDrlg.getLevel(drlg, vis[slot]));
            if (anchor != null) zone.setNativeSeamAnchor(slot, anchor[0], anchor[1]);
          }
          // The River's bridge is a RoomEx orth, not a UNIT_TILE. Its reverse
          // link on the independent Chaos preset is likewise seamless.
          for (int destinationId : levelIds) {
            if (destinationId == levelId) continue;
            int[] anchor = NativeLevelSeamAnchor.resolve(level, DrlgDrlg.getLevel(drlg, destinationId));
            if (anchor == null) continue;
            int chosen = -1;
            for (int slot = 0; slot < 8; slot++) {
              if (map.getWarpDestinationOverride(levelId, slot) == destinationId) { chosen = slot; break; }
            }
            if (chosen < 0) for (int slot = 0; slot < 8; slot++) {
              if ((vis == null || slot >= vis.length || vis[slot] == 0)
                  && map.getWarpDestinationOverride(levelId, slot) <= 0) { chosen = slot; break; }
            }
            if (chosen >= 0) {
              map.addWarpDestinationOverride(levelId, chosen, destinationId);
              zone.setNativeSeamAnchor(chosen, anchor[0], anchor[1]);
            }
          }
        }
        int[] objectCounts = {0, 0};
        int[] nativeWarpUnits = {0, 0};
        com.badlogic.gdx.utils.Array<Map.NativeMonster> exportedMonsters =
            new com.badlogic.gdx.utils.Array<>();
        String[] townMonsterSource = {null};
        boolean[] townMonsterSourcesMatch = {true};
        int[] monsterCounts = {0, 0};
        int[] exportedVis = com.d2moo.common.drlg.DrlgDrlgRoom.getVisArrayFromLevelId(drlg, levelId);
        int[] exportedWarps = com.d2moo.common.drlg.DrlgDrlgWarp.getWarpIdArrayFromLevelId(drlg, levelId);
        int presetUnits = DrlgExport.exportLevelPresetUnits(drlg, levelId,
            (exportLevelId, roomId, unitType, index, mode, x, y, ds1Raw, spawned,
                externalEntity, sourceFile) -> {
              if (unitType == D2UnitTypes.UNIT_TILE) {
                // UNIT_TILE class index is LvlWarp.Id (DrlgRoomTile.cpp
                // AddWarp), while VisN independently names the destination.
                int slot = NativeWarpSlotResolver.resolve(level, exportedVis, exportedWarps, roomId, index);
                if (slot >= 0) {
                  zone.addNativeWarpMarker(slot, x, y);
                  nativeWarpUnits[0]++;
                }
                nativeWarpUnits[1]++;
                return;
              }
              if (unitType == D2UnitTypes.UNIT_MONSTER) {
                monsterCounts[0]++;
                if (zone.isTown() && externalEntity) {
                  if (sourceFile == null || sourceFile.isEmpty()) {
                    townMonsterSourcesMatch[0] = false;
                  } else if (townMonsterSource[0] == null) {
                    townMonsterSource[0] = sourceFile;
                  } else if (!sameSource(townMonsterSource[0], sourceFile)) {
                    townMonsterSourcesMatch[0] = false;
                  }
                }
                if (!externalEntity || x < 0 || y < 0
                    || x >= width * com.riiablo.map.DT1.Tile.SUBTILE_SIZE
                    || y >= height * com.riiablo.map.DT1.Tile.SUBTILE_SIZE) {
                  if (x < 0 || y < 0
                      || x >= width * com.riiablo.map.DT1.Tile.SUBTILE_SIZE
                      || y >= height * com.riiablo.map.DT1.Tile.SUBTILE_SIZE) {
                    monsterCounts[1]++;
                  }
                  return;
                }
                NativeMonsterResolution resolved = resolveNativeMonster(index, ds1Raw, actId + 1);
                exportedMonsters.add(new Map.NativeMonster(roomId, resolved.monsterId, mode,
                    x, y, resolved.superUniqueId, resolved.superUniqueKey, spawned));
                return;
              }
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
        int queuedMonsters = queueNativeMonsters(zone, exportedMonsters);
        zone.getNativeMonsters().addAll(exportedMonsters);
        if (zone.isTown() && !exportedMonsters.isEmpty()
            && townMonsterSourcesMatch[0] && townMonsterSource[0] != null
            && sameSource(zone.presetSourceFile(), townMonsterSource[0])) {
          zone.replaceNativeTownMonsters(townMonsterSource[0], exportedMonsters);
        }
        exportedLevels++;
        if (Gdx.app != null) Gdx.app.log(TAG, String.format(
            "A%d native level=%d rooms=%d tiles=%dx%d floors=%d walls=%d shadows=%d dt1Mask=0x%X objects=%d invalid=%d",
            actId + 1, levelId, level.getRooms(), width, height, floors,
            applier.getExportedWallCount(), applier.getExportedShadowCount(), dt1Mask,
            presetUnits, objectCounts[1])
            + String.format(" unitTileWarp=%d/%d monsters=%d/%d queuedMonsters=%d",
                nativeWarpUnits[0], nativeWarpUnits[1], monsterCounts[0], monsterCounts[1],
                queuedMonsters));
        D2Log.debug("ACT4_BRIDGE exported level=%d floors=%d rooms=%d objects=%d",
            levelId, floors, level.getRooms(), presetUnits);
        applier.resetLastExportedFloorCount();
      }
      if (Gdx.app != null) Gdx.app.log(TAG, "A" + (actId + 1) + " native terrain summary levels="
          + exportedLevels + "/" + levelIds.length);
      if (actId == D2C_Acts.ACT_II) NativeTownExit.capture(map, drlg, townId);
      return exportedLevels > 0;
    } catch (Throwable t) {
      if (Gdx.app != null) Gdx.app.error(TAG,
          "Native Act " + (actId + 1) + " export unavailable; keeping compatibility Zones", t);
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

  static int queueNativeMonsters(Zone zone,
      com.badlogic.gdx.utils.Array<Map.NativeMonster> monsters) {
    if (zone == null || zone.isTown() || monsters == null) return 0;
    int queued = 0;
    for (Map.NativeMonster monster : monsters) {
      if (monster == null || monster.spawned || monster.monsterId < 0) continue;
      Map.RoomEx room = monster.roomId >= 0 && monster.roomId < zone.getRoomsEx().size
          ? zone.getRoomsEx().get(monster.roomId) : null;
      float worldX = zone.x() + monster.x;
      float worldY = zone.y() + monster.y;
      if (room == null || !room.contains(worldX, worldY)) room = zone.findRoomEx(worldX, worldY);
      if (room == null) continue;
      room.addMonsterSpawn(monster.monsterId, worldX, worldY, -1, false,
          monster.superUniqueId, monster.superUniqueKey);
      queued++;
    }
    return queued;
  }

  /** Resolves Act IV MonPreset indices and encoded SuperUnique ids. */
  static NativeMonsterResolution resolveNativeMonster(int index, boolean ds1Raw) {
    return resolveNativeMonster(index, ds1Raw, 4);
  }

  private static NativeMonsterResolution resolveNativeMonster(int index, boolean ds1Raw, int act) {
    if (Riiablo.files == null || Riiablo.files.monstats == null) {
      return new NativeMonsterResolution(index, -1, null);
    }
    String placement = null;
    if (ds1Raw && Riiablo.files.MonPreset != null) {
      try {
        placement = Riiablo.files.MonPreset.getPlace(act, index);
      } catch (RuntimeException ignored) {}
    }
    MonStats.Entry monster = Act1MapBuilderD2MOD.resolveD2MooPresetMonster(index, placement);
    SuperUniques.Entry superUnique = placement == null || Riiablo.files.SuperUniques == null
        ? null : Riiablo.files.SuperUniques.get(placement);
    if (monster == null && superUnique != null) monster = Riiablo.files.monstats.get(superUnique.MonClass);
    if (monster == null && superUnique == null && Riiablo.files.SuperUniques != null) {
      int suIndex = index - Riiablo.files.monstats.size();
      superUnique = Riiablo.files.SuperUniques.get(suIndex);
      if (superUnique != null) monster = Riiablo.files.monstats.get(superUnique.MonClass);
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
    static boolean mpqsMissing() { return com.riiablo.Riiablo.mpqs == null; }
  }
}
