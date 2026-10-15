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
import com.d2moo.common.drlg.DrlgDrlg;
import com.d2moo.common.drlg.DrlgExport;
import com.d2moo.common.drlg.D2UnitTypes;
import com.d2moo.common.util.D2FileReader;
import com.d2moo.common.util.D2MemoryPool;
import com.d2moo.common.util.D2Log;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.SuperUniques;
import com.riiablo.drlg.TileGrid;
import com.riiablo.map.Act1MapBuilderD2MOD;
import com.riiablo.map.Map;
import com.riiablo.map.Map.Zone;

import java.util.IdentityHashMap;

/** Resource-gated D2MOO DRLG bridge for the Act V main progression. */
public final class Act5D2MOOLayoutBridge {
  private static final String TAG = "Act5D2MOOLayoutBridge";
  private static final int TOWN = D2LevelIds.LEVEL_HARROGATH;
  /** Main progression followed by the independent Nihlathak quest branch. */
  static final int[] LEVELS = {
      TOWN,
      D2LevelIds.LEVEL_BLOODYFOOTHILLS,
      D2LevelIds.LEVEL_FRIGIDHIGHLANDS,
      D2LevelIds.LEVEL_ARREATPLATEAU,
      D2LevelIds.LEVEL_CRYSTALLINEPASSAGE,
      D2LevelIds.LEVEL_FROZENRIVER,
      D2LevelIds.LEVEL_GLACIALTRAIL,
      D2LevelIds.LEVEL_FROZENTUNDRA,
      D2LevelIds.LEVEL_ANCIENTSWAY,
      D2LevelIds.LEVEL_ARREATSUMMIT,
      D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV1,
      D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV2,
      D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV3,
      D2LevelIds.LEVEL_THRONEOFDESTRUCTION,
      D2LevelIds.LEVEL_WORLDSTONECHAMBER,
      D2LevelIds.LEVEL_NIHLATHAKSTEMPLE,
      D2LevelIds.LEVEL_HALLSOFANGUISH,
      D2LevelIds.LEVEL_HALLSOFPAIN,
      D2LevelIds.LEVEL_HALLSOFVAUGHT,
      D2LevelIds.LEVEL_DRIFTERSCAVERN,
      D2LevelIds.LEVEL_ICYCELLAR,
      D2LevelIds.LEVEL_ABADDON,
      D2LevelIds.LEVEL_PITOFACHERON,
      D2LevelIds.LEVEL_INFERNALPIT
  };

  private Act5D2MOOLayoutBridge() {}

  /**
   * Projects native RoomEx/TileGrid data into already-created Act V Zones,
   * including the independent Nihlathak quest branch.
   * A missing or partial archive leaves the compatibility builder untouched.
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
      act.setAct(D2C_Acts.ACT_V);
      act.setTownId(TOWN);
      act.setPMemPool(new D2MemoryPool());
      drlg = DrlgDrlg.allocDrlg(act, D2C_Acts.ACT_V, archive, gameSeed,
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
        D2Log.debug("ACT5_NATIVE_LEVEL_PROBE id=%d allocated=%s drlgType=%d levelType=%d",
            levelId, level != null, level == null ? -1 : level.getDrlgType(),
            level == null ? -1 : level.getLevelType());
        if (zone == null || level == null) continue;
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
          if (Gdx.app != null) Gdx.app.log(TAG, "A5 native level=" + levelId
              + " has no exported floor; keeping compatibility Zone");
          continue;
        }
        projectRooms(level, zone);
        com.riiablo.map.NativeMonsterSpawnExclusion.capture(level, zone);
        zone.setNativeTileGrid(grid, dt1Mask);
        if (zone.isTown()) NativeTownPortalAnchor.capture(level, zone);
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
                if (spawned || x < 0 || y < 0
                    || x >= width * com.riiablo.map.DT1.Tile.SUBTILE_SIZE
                    || y >= height * com.riiablo.map.DT1.Tile.SUBTILE_SIZE) {
                  if (x < 0 || y < 0
                      || x >= width * com.riiablo.map.DT1.Tile.SUBTILE_SIZE
                      || y >= height * com.riiablo.map.DT1.Tile.SUBTILE_SIZE) {
                    monsterCounts[1]++;
                  }
                  return;
                }
                if (queueNormalGroupMarker(zone, exportedMonsters, index, ds1Raw,
                    roomId, mode, x, y)) return;
                NativeMonsterResolution resolved = resolveNativeMonster(index, ds1Raw);
                if (resolved.monsterId < 0) {
                  monsterCounts[1]++;
                  return;
                }
                exportedMonsters.add(new Map.NativeMonster(roomId, resolved.monsterId, mode,
                    x, y, resolved.superUniqueId, resolved.superUniqueKey, false));
                return;
              }
              if (unitType == D2UnitTypes.UNIT_OBJECT) objectCounts[0]++;
              if (unitType == D2UnitTypes.UNIT_OBJECT && x >= 0 && y >= 0
                  && x < width * com.riiablo.map.DT1.Tile.SUBTILE_SIZE
                  && y < height * com.riiablo.map.DT1.Tile.SUBTILE_SIZE) {
                zone.getNativeObjects().add(new Map.NativeObject(roomId, index, mode, x, y,
                    ds1Raw, spawned, sourceFile, externalEntity));
              } else if (unitType == D2UnitTypes.UNIT_OBJECT) objectCounts[1]++;
            });
        exportedLevels++;
        if (Gdx.app != null) Gdx.app.log(TAG, String.format(
            "A5 native level=%d rooms=%d tiles=%dx%d floors=%d walls=%d shadows=%d dt1Mask=0x%X objects=%d/%d invalid=%d",
            levelId, level.getRooms(), width, height, floors,
            applier.getExportedWallCount(), applier.getExportedShadowCount(), dt1Mask,
            presetUnits, objectCounts[0], objectCounts[1])
            + String.format(" unitTileWarp=%d/%d monsters=%d/%d", nativeWarpUnits[0],
                nativeWarpUnits[1], monsterCounts[0], monsterCounts[1]));
        queueNativeMonsters(zone, exportedMonsters);
        zone.getNativeMonsters().addAll(exportedMonsters);
        if (zone.isTown() && !exportedMonsters.isEmpty()
            && townMonsterSourcesMatch[0] && townMonsterSource[0] != null
            && sameSource(zone.presetSourceFile(), townMonsterSource[0])) {
          zone.replaceNativeTownMonsters(townMonsterSource[0], exportedMonsters);
        }
        D2Log.debug("ACT5_BRIDGE exported level=%d floors=%d rooms=%d",
            levelId, floors, level.getRooms());
        applier.resetLastExportedFloorCount();
      }
      NativeTownExit.capture(map, drlg, TOWN);
      NativeLevelSeamAnchor.captureWalkEdges(map, drlg);
      if (Gdx.app != null) Gdx.app.log(TAG, "A5 native main-chain summary levels="
          + exportedLevels + "/" + LEVELS.length);
      return exportedLevels > 0;
    } catch (Throwable t) {
      if (Gdx.app != null) Gdx.app.error(TAG,
          "Native Act V export unavailable; keeping compatibility Zones", t);
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

  static int queueNativeMonsters(Zone zone,
      com.badlogic.gdx.utils.Array<Map.NativeMonster> monsters) {
    if (zone == null || zone.isTown() || monsters == null) return 0;
    int queued = 0;
    for (Map.NativeMonster monster : monsters) {
      if (monster == null || monster.spawned || monster.monsterId < 0 || monster.populationGroupSize > 0) continue;
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

  /** Resolves Act V MonPreset indices and encoded SuperUnique ids. */
  static boolean queueNormalGroupMarker(Zone zone,
      com.badlogic.gdx.utils.Array<Map.NativeMonster> output, int index, boolean ds1Raw,
      int roomId, int mode, int x, int y) {
    if (!ds1Raw || Riiablo.files.MonPreset == null) return false;
    String place;
    try { place = Riiablo.files.MonPreset.getPlace(5, index); }
    catch (RuntimeException ignored) { return false; }
    if (!"place_impgroup".equals(place) && !"place_miniongroup".equals(place)) return false;
    // MonsterRegion.cpp MonPlace 24/26: these groups are normal-only.
    if (zone.diff != 0) return true;
    String id = "place_miniongroup".equals(place) ? "minion1"
        : zone.levelId() == D2LevelIds.LEVEL_BLOODYFOOTHILLS ? "deathmauler1" : "imp1";
    MonStats.Entry monster = Riiablo.files.monstats.get(id);
    if (monster == null || monster.MinGrp <= 0 || monster.MaxGrp < monster.MinGrp) return true;
    java.util.Random random = new java.util.Random(com.riiablo.map.NativeLevelSeed.forLevel(
        zone.map.seed(), zone.levelId()) ^ (roomId * 31L + x * 17L + y));
    int count = monster.MinGrp + random.nextInt(monster.MaxGrp - monster.MinGrp + 1);
    output.add(new Map.NativeMonster(roomId, monster.hcIdx, mode, x, y, -1, null, false,
        com.riiablo.engine.server.monster.MonsterRank.NORMAL, count));
    return true;
  }

  static NativeMonsterResolution resolveNativeMonster(int index, boolean ds1Raw) {
    if (Riiablo.files == null || Riiablo.files.monstats == null) {
      return new NativeMonsterResolution(index, -1, null);
    }
    String placement = null;
    if (ds1Raw && Riiablo.files.MonPreset != null) {
      try {
        placement = Riiablo.files.MonPreset.getPlace(5, index);
      } catch (RuntimeException ignored) {}
    }
    // A MonPlace token is an instruction, never a MonStats row number.
    // Unhandled tokens must not silently become unrelated low-id monsters.
    if (placement != null && placement.startsWith("place_"))
      return new NativeMonsterResolution(-1, -1, null);
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
