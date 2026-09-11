package com.riiablo.map;

import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlPrest;
import com.riiablo.engine.server.NativeDataTables;
import com.riiablo.engine.EntityFactory;
import com.riiablo.map.Map.Preset;
import com.riiablo.map.Map.Zone;
import com.riiablo.map.d2moo.Act3D2MOOLayoutBridge;
import com.d2moo.common.drlg.D2LevelIds;

/**
 * Act3 地图生成器 - 完全复刻 D2MOD 实现
 * 
 * 参考 D2MOD:
 * - source/D2Common/src/Drlg/DrlgOutPlace.cpp (DRLGOUTPLACE_InitAct3OutdoorLevel)
 * - source/D2Common/src/Drlg/DrlgOutJung.cpp (DRLGOUTJUNG_BuildJungle, DRLGOUTPLACE_BuildKurast)
 */
public enum Act3MapBuilderD2MOD implements MapBuilder {
  INSTANCE;

  private static final String TAG = "Act3MapBuilderD2MOD";
  private static final boolean DEBUG = true;
  private static final boolean DEBUG_BUILD = DEBUG && true;

  // Act3 区域定义
  // Keep these ids sourced from the same table used by the native D2MOO
  // bridge.  Arcane Sanctuary is 75; Act III starts at 76 in 1.10f.
  // The previous literals were all one lower and caused level 75 (Arcane
  // Sanctuary) to be generated as Kurast Docks.
  static final int LEVEL_KURASTDOCKTOWN = D2LevelIds.LEVEL_KURASTDOCKTOWN;
  static final int LEVEL_SPIDERFOREST = D2LevelIds.LEVEL_SPIDERFOREST;
  static final int LEVEL_GREATMARSH = D2LevelIds.LEVEL_GREATMARSH;
  static final int LEVEL_FLAYERJUNGLE = D2LevelIds.LEVEL_FLAYERJUNGLE;
  static final int LEVEL_LOWERKURAST = D2LevelIds.LEVEL_LOWERKURAST;
  static final int LEVEL_KURASTBAZAAR = D2LevelIds.LEVEL_KURASTBAZAAR;
  static final int LEVEL_UPPERKURAST = D2LevelIds.LEVEL_UPPERKURAST;
  static final int LEVEL_KURASTCAUSEWAY = D2LevelIds.LEVEL_KURASTCAUSEWAY;
  static final int LEVEL_TRAVINCAL = D2LevelIds.LEVEL_TRAVINCAL;

  /** Native outdoor build order used by DRLG_LINKS for Act III. */
  static final int[] ACT3_OUTDOOR_CHAIN = {
      LEVEL_KURASTDOCKTOWN,
      LEVEL_SPIDERFOREST,
      LEVEL_GREATMARSH,
      LEVEL_FLAYERJUNGLE,
      LEVEL_LOWERKURAST,
      LEVEL_KURASTBAZAAR,
      LEVEL_UPPERKURAST,
      LEVEL_KURASTCAUSEWAY,
      LEVEL_TRAVINCAL
  };

  /** Adjacent outdoor links installed by D2Common's DRLG_SetWarpId path. */
  static final int[][] ACT3_OUTDOOR_LINKS = {
      {LEVEL_KURASTDOCKTOWN, LEVEL_SPIDERFOREST},
      {LEVEL_SPIDERFOREST, LEVEL_GREATMARSH},
      {LEVEL_GREATMARSH, LEVEL_FLAYERJUNGLE},
      {LEVEL_FLAYERJUNGLE, LEVEL_LOWERKURAST},
      {LEVEL_LOWERKURAST, LEVEL_KURASTBAZAAR},
      {LEVEL_KURASTBAZAAR, LEVEL_UPPERKURAST},
      {LEVEL_UPPERKURAST, LEVEL_KURASTCAUSEWAY},
      {LEVEL_KURASTCAUSEWAY, LEVEL_TRAVINCAL}
  };

  @Wire(name = "factory")
  protected EntityFactory factory;

  @Wire(name = "client.socket", failOnNull = false)
  protected com.badlogic.gdx.net.Socket socket;

  @Override
  public void generate(Map map, int seed, int diff) {
    // 重要：设置随机种子，确保多人游戏中所有客户端生成相同的地图
    MathUtils.random.setSeed(seed);

    // D2MOD: Act3 的地图生成逻辑
    // 1. 首先创建城镇（KURASTDOCKTOWN）
    Levels.Entry townLevel = Riiablo.files.Levels.get(LEVEL_KURASTDOCKTOWN);
    if (townLevel == null) {
      Gdx.app.error(TAG, "Town level not found: " + LEVEL_KURASTDOCKTOWN);
      return;
    }

    LvlPrest.Entry townPreset = null;
    for (LvlPrest.Entry p : Riiablo.files.LvlPrest) {
      if (p.LevelId == LEVEL_KURASTDOCKTOWN) {
        townPreset = p;
        break;
      }
    }

    if (townPreset == null) {
      townPreset = Riiablo.files.LvlPrest.get(Map.ACT_DEF[2]);
    }

    int fileId[] = new int[6];
    int numFiles = Preset.getPresets(townPreset, fileId);
    if (numFiles == 0) {
      Gdx.app.error(TAG, "No valid presets found for town");
      return;
    }
    int selectIndex = MathUtils.random(numFiles - 1);
    int select = fileId[selectIndex];

    BaseMapBuilderD2MOD base = new BaseMapBuilderD2MOD() {};
    base.factory = factory;
    base.socket = socket;
    Zone townZone = base.createZoneWithPreset(map, townLevel, townPreset, select, 
        townLevel.OffsetX, townLevel.OffsetY, true);

    // 2. 创建丛林区域（SPIDERFOREST, GREATMARSH, FLAYERJUNGLE）
    // 参考 DRLGOUTJUNG_BuildJungle
    int[] jungleLevels = {LEVEL_SPIDERFOREST, LEVEL_GREATMARSH, LEVEL_FLAYERJUNGLE};
    int posY = 0;
    
    for (int levelId : jungleLevels) {
      Levels.Entry level = Riiablo.files.Levels.get(levelId);
      if (level == null) continue;

    int sizeX = NativeDataTables.levelSizeX(level, diff, 1);
    int sizeY = NativeDataTables.levelSizeY(level, diff, 1);
      posY -= sizeY * 5; // tile to sub-tile

      int posX = (townZone.width / 2 + townZone.x) - (sizeX * 5 / 2);
      Zone zone = base.createZoneWithGenerator(map, level, diff, posX, posY + townZone.y);
      zone.generator = base.createMonsterGenerator(socket);
    }

    // 3. 创建 Kurast 区域（LOWERKURAST, KURASTBAZAAR, UPPERKURAST）
    // 参考 DRLGOUTPLACE_BuildKurast
    int[] kurastLevels = {LEVEL_LOWERKURAST, LEVEL_KURASTBAZAAR, LEVEL_UPPERKURAST};
    for (int levelId : kurastLevels) {
      Levels.Entry level = Riiablo.files.Levels.get(levelId);
      if (level == null) continue;

      LvlPrest.Entry preset = null;
      for (LvlPrest.Entry p : Riiablo.files.LvlPrest) {
        if (p.LevelId == levelId) {
          preset = p;
          break;
        }
      }

      final int drlgType = level.DrlgType;
      final boolean isPresetLevel = drlgType == 2; // 2 == DRLGTYPE_PRESET

      if (preset != null && isPresetLevel) {
        int presetFileId[] = new int[6];
        int presetNumFiles = Preset.getPresets(preset, presetFileId);
        if (presetNumFiles > 0) {
          int presetSelectIndex = MathUtils.random(presetNumFiles - 1);
          int presetSelect = presetFileId[presetSelectIndex];
          base.createZoneWithPreset(map, level, preset, presetSelect, 
              level.OffsetX, level.OffsetY, false);
        }
      } else {
        int sizeX = NativeDataTables.levelSizeX(level, diff, 1);
        int sizeY = NativeDataTables.levelSizeY(level, diff, 1);
        int posX = (townZone.width / 2 + townZone.x) - (sizeX * 5 / 2);
        posY -= sizeY * 5;
        Zone zone = base.createZoneWithGenerator(map, level, diff, posX, posY + townZone.y);
        zone.generator = base.createMonsterGenerator(socket);
      }
    }

    // 3b. The native chain continues through Kurast Causeway before entering
    // Travincal.  The old compatibility builder stopped at Upper Kurast, so
    // the D2MOO bridge had no Zone to receive the native RoomEx/TileGrid for
    // levels 82 and 83.  Always allocate lightweight fallback zones here;
    // Act3D2MOOLayoutBridge will replace their terrain with the native export
    // when the 1.10f resources are available.
    int[] tailLevels = {
        LEVEL_KURASTCAUSEWAY,
        LEVEL_TRAVINCAL,
        D2LevelIds.LEVEL_DURANCEOFHATELEVEL1,
        D2LevelIds.LEVEL_DURANCEOFHATELEVEL2,
        D2LevelIds.LEVEL_DURANCEOFHATELEVEL3
    };
    for (int levelId : tailLevels) {
      Levels.Entry level = Riiablo.files.Levels.get(levelId);
      if (level == null || findZone(map, levelId) != null) continue;
      int sizeX = NativeDataTables.levelSizeX(level, diff, 1);
      int sizeY = NativeDataTables.levelSizeY(level, diff, 1);
      int posX = (townZone.width / 2 + townZone.x) - (sizeX * 5 / 2);
      posY -= sizeY * 5;
      Zone zone = base.createZoneWithGenerator(map, level, diff, posX,
          posY + townZone.y);
      zone.generator = base.createMonsterGenerator(socket);
      if (DEBUG_BUILD) {
        Gdx.app.debug(TAG, String.format(
            "Placed native tail fallback %s (id=%d) at (%d, %d)",
            level.LevelName, levelId, posX, posY + townZone.y));
      }
    }

    // 4. 创建 TRAVINCAL（特殊区域）
    Levels.Entry travincalLevel = Riiablo.files.Levels.get(LEVEL_TRAVINCAL);
    if (travincalLevel != null) {
      LvlPrest.Entry travincalPreset = null;
      for (LvlPrest.Entry p : Riiablo.files.LvlPrest) {
        if (p.LevelId == LEVEL_TRAVINCAL) {
          travincalPreset = p;
          break;
        }
      }

      if (travincalPreset != null) {
        int travincalFileId[] = new int[6];
        int travincalNumFiles = Preset.getPresets(travincalPreset, travincalFileId);
        if (travincalNumFiles > 0) {
          int travincalSelectIndex = MathUtils.random(travincalNumFiles - 1);
          int travincalSelect = travincalFileId[travincalSelectIndex];
          base.createZoneWithPreset(map, travincalLevel, travincalPreset, travincalSelect, 
              travincalLevel.OffsetX, travincalLevel.OffsetY, false);
        }
      }
    }

    // When 1.10f MPQs are available, replace the compatibility outdoor
    // terrain with D2MOO's actual RoomEx/DT1 export.  The bridge is gated and
    // failure-safe, so headless/resource-light environments keep this builder.
    Act3D2MOOLayoutBridge.populateZones(seed, diff, map);
    // Travincal's native outdoor link is represented by an edge link rather
    // than a preset wall cell.  Seed one logical VIS_0_00 marker so the
    // existing MapManager/WarpInteractor path exposes the Durance entrance;
    // the destination is resolved by the native Act III warp table below.
    ensureProgressionWarpMarker(map, LEVEL_TRAVINCAL, 0);

    // 添加高级功能：边界、路径、传送点、神殿等
    // 参考 D2MOD: DRLGOUTPLACE_InitAct3OutdoorLevel
    for (Zone zone : map.zones) {
      if (!zone.town) {
        // 放置边界
        OutdoorFeatures.placeBorders(zone, seed, 2);
        
        // Act3 的特殊处理（Kurast 区域已有预设，不需要额外功能）
        // 其他丛林区域可以放置传送点和神殿
        if (zone.level.Id >= LEVEL_SPIDERFOREST && zone.level.Id <= LEVEL_FLAYERJUNGLE) {
          OutdoorFeatures.placeWaypoint(zone, seed);
          OutdoorFeatures.placeShrines(zone, seed, 3);
        }
      }
    }
  }

  /**
   * Mirrors the runtime half of D2Common's Act III outdoor linking.  The
   * native generator appends the adjacent level to the first empty LvlWarp
   * slot instead of replacing Levels.txt, so preserve that slot behaviour.
   */
  void configureAct3OutdoorWarps(Map map) {
    if (map == null || Riiablo.files == null || Riiablo.files.Levels == null) return;
    IntMap<RuntimeWarpState> states = new IntMap<>();
    for (int[] link : ACT3_OUTDOOR_LINKS) {
      for (int levelId : link) {
        if (states.containsKey(levelId)) continue;
        Levels.Entry level = Riiablo.files.Levels.get(levelId);
        if (level != null) states.put(levelId, new RuntimeWarpState(level.Vis, level.Warp));
      }
    }

    int configured = 0;
    for (int[] link : ACT3_OUTDOOR_LINKS) {
      RuntimeWarpState source = states.get(link[0]);
      RuntimeWarpState destination = states.get(link[1]);
      if (source == null || destination == null) continue;
      int sourceSlot = source.ensureDestination(link[1]);
      int destinationSlot = destination.ensureDestination(link[0]);
      if (sourceSlot < 0 || destinationSlot < 0) {
        Gdx.app.error(TAG, String.format(
            "Act3 warp slot exhausted: %d->%d sourceSlot=%d destinationSlot=%d",
            link[0], link[1], sourceSlot, destinationSlot));
        continue;
      }
      map.addWarpDestinationOverride(link[0], sourceSlot, link[1]);
      map.addWarpDestinationOverride(link[1], destinationSlot, link[0]);
      configured++;
    }
    // Levels.txt Vis entries for the Durance are duplicated in a few 1.10f
    // table exports (both slots may point to the next level).  D2Common uses
    // the actual progression edges: Travincal -> Durance 1 -> Durance 2 ->
    // Durance 3, with one return edge at each boundary.  Keep these overrides
    // local to Act III instead of mutating the shared Levels table.
    map.addWarpDestinationOverride(D2LevelIds.LEVEL_DURANCEOFHATELEVEL1, 0,
        LEVEL_TRAVINCAL);
    map.addWarpDestinationOverride(D2LevelIds.LEVEL_DURANCEOFHATELEVEL1, 1,
        D2LevelIds.LEVEL_DURANCEOFHATELEVEL2);
    map.addWarpDestinationOverride(D2LevelIds.LEVEL_DURANCEOFHATELEVEL2, 0,
        D2LevelIds.LEVEL_DURANCEOFHATELEVEL1);
    map.addWarpDestinationOverride(D2LevelIds.LEVEL_DURANCEOFHATELEVEL2, 1,
        D2LevelIds.LEVEL_DURANCEOFHATELEVEL3);
    map.addWarpDestinationOverride(D2LevelIds.LEVEL_DURANCEOFHATELEVEL3, 3,
        D2LevelIds.LEVEL_DURANCEOFHATELEVEL2);
    Gdx.app.log(TAG, String.format("Act3 native warp table configured: links=%d/%d",
        configured, ACT3_OUTDOOR_LINKS.length));
  }

  /** Pairs emitted special-wall cells after all Act III zones are generated. */
  void linkNativeWarpSpecials(Map map) {
    if (map == null) return;
    int linked = 0;
    int missingZone = 0;
    int missingReverse = 0;
    for (Zone source : new Array.ArrayIterator<>(map.zones)) {
      if (source == null || source.level == null || source.specials == null) continue;
      for (IntMap.Entry<DS1.Cell> entry : source.specials.entries()) {
        DS1.Cell sourceCell = entry.value;
        if (sourceCell == null || !Map.ID.WARPS.contains(sourceCell.id)) continue;
        if (source.level.Warp == null || sourceCell.mainIndex < 0
            || sourceCell.mainIndex >= source.level.Warp.length
            || source.level.Warp[sourceCell.mainIndex] < 0) continue;
        int destinationLevelId = map.getWarpDestinationOverride(
            source.level.Id, sourceCell.mainIndex);
        if (destinationLevelId <= 0 && source.level.Vis != null
            && sourceCell.mainIndex < source.level.Vis.length) {
          destinationLevelId = source.level.Vis[sourceCell.mainIndex];
        }
        Zone destination = findZoneByLevelId(map, destinationLevelId);
        if (destination == null) {
          missingZone++;
          continue;
        }
        DS1.Cell destinationCell = findReverseWarp(map, destination, source.level.Id);
        if (destinationCell == null) {
          missingReverse++;
          continue;
        }
        source.setWarp(sourceCell.id, destinationCell.id);
        linked++;
      }
    }
    Gdx.app.log(TAG, String.format(
        "Act3 native warp special summary: linked=%d missingZone=%d missingReverse=%d",
        linked, missingZone, missingReverse));
  }

  private static Zone findZone(Map map, int levelId) {
    if (map == null) return null;
    for (Zone zone : map.zones) {
      if (zone != null && zone.level != null && zone.level.Id == levelId) return zone;
    }
    return null;
  }

  private static void ensureProgressionWarpMarker(Map map, int levelId, int mainIndex) {
    Zone zone = findZone(map, levelId);
    if (zone == null || zone.level == null) return;
    for (IntMap.Entry<DS1.Cell> entry : zone.specials.entries()) {
      DS1.Cell existing = entry.value;
      if (existing != null && Map.ID.WARPS.contains(existing.id)
          && existing.mainIndex == mainIndex) return;
    }
    if (zone.specials == Zone.EMPTY_INT_CELL_MAP) zone.specials = new IntMap<>();
    int tx = Math.max(0, Math.min(zone.tilesX - 1, zone.tilesX / 2));
    int ty = Math.max(0, Math.min(zone.tilesY - 1, zone.tilesY / 2));
    DS1.Cell cell = new DS1.Cell();
    cell.id = DT1.Tile.Index.create(Orientation.SPECIAL_10, mainIndex, 0);
    cell.mainIndex = (short) mainIndex;
    cell.subIndex = 0;
    cell.orientation = (short) Orientation.SPECIAL_10;
    cell.value = ((mainIndex & 0x3F) << 20);
    zone.putCell(Map.WALL_OFFSET, tx, ty, cell);
    Gdx.app.log(TAG, String.format(
        "Act3 synthetic progression warp: level=%d mainIndex=%d tile=(%d,%d)",
        levelId, mainIndex, tx, ty));
  }

  private static Zone findZoneByLevelId(Map map, int levelId) {
    for (Zone zone : map.zones) {
      if (zone != null && zone.level != null && zone.level.Id == levelId) return zone;
    }
    return null;
  }

  private static DS1.Cell findReverseWarp(Map map, Zone destination, int sourceLevelId) {
    if (destination.specials == null) return null;
    for (IntMap.Entry<DS1.Cell> entry : destination.specials.entries()) {
      DS1.Cell cell = entry.value;
      if (cell == null || !Map.ID.WARPS.contains(cell.id)) continue;
      if (destination.level.Warp == null || cell.mainIndex < 0
          || cell.mainIndex >= destination.level.Warp.length
          || destination.level.Warp[cell.mainIndex] < 0) continue;
      int target = map.getWarpDestinationOverride(destination.level.Id, cell.mainIndex);
      if (target <= 0 && destination.level.Vis != null
          && cell.mainIndex < destination.level.Vis.length) {
        target = destination.level.Vis[cell.mainIndex];
      }
      if (target == sourceLevelId) return cell;
    }
    return null;
  }

  static int findRuntimeWarpSlot(int[] vis, int[] warp, int destinationLevelId) {
    if (vis == null || warp == null) return -1;
    int count = Math.min(8, Math.min(vis.length, warp.length));
    for (int i = 0; i < count; i++) if (vis[i] == destinationLevelId) return i;
    for (int i = 0; i < count; i++) {
      if (vis[i] == 0 && warp[i] == -1) return i;
    }
    return -1;
  }

  private static final class RuntimeWarpState {
    final int[] vis = new int[8];
    final int[] warp = new int[8];

    RuntimeWarpState(int[] sourceVis, int[] sourceWarp) {
      for (int i = 0; i < vis.length; i++) {
        vis[i] = sourceVis != null && i < sourceVis.length ? sourceVis[i] : 0;
        warp[i] = sourceWarp != null && i < sourceWarp.length ? sourceWarp[i] : -1;
      }
    }

    int ensureDestination(int destinationLevelId) {
      int slot = findRuntimeWarpSlot(vis, warp, destinationLevelId);
      if (slot >= 0) vis[slot] = destinationLevelId;
      return slot;
    }
  }
}
