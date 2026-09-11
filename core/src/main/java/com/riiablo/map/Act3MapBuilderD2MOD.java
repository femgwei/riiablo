package com.riiablo.map;

import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlPrest;
import com.riiablo.codec.excel.LvlWarp;
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

  /** First Act III side-area pair.  These are reached from the jungle by
   * native LvlWarp entries, not by the linear outdoor chain. */
  static final int[] ACT3_UNDERGROUND_PRIMARY = {
      D2LevelIds.LEVEL_SPIDERCAVE,
      D2LevelIds.LEVEL_SPIDERCAVERN
  };

  /** Remaining Act III side dungeons and temple/sewer levels. */
  static final int[] ACT3_UNDERGROUND_SECONDARY = {
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
      D2LevelIds.LEVEL_DISUSEDRELIQUARY
  };

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

    // Spider Cave and Spider Cavern are generated by the native DRLG as
    // detached maze levels.  Allocate compatibility zones before the native
    // bridge runs so a warp target is always addressable even when an MPQ set
    // is incomplete; the bridge replaces their terrain/topology when the
    // 1.10f resources are available.
    int undergroundY = posY - 32;
    for (int levelId : ACT3_UNDERGROUND_PRIMARY) {
      Levels.Entry level = Riiablo.files.Levels.get(levelId);
      if (level == null || findZone(map, levelId) != null) continue;
      int sizeX = NativeDataTables.levelSizeX(level, diff, 1);
      int sizeY = NativeDataTables.levelSizeY(level, diff, 1);
      int posX = (townZone.width / 2 + townZone.x) - (sizeX * 5 / 2);
      undergroundY -= sizeY * 5;
      Zone zone = base.createZoneWithGenerator(map, level, diff, posX,
          undergroundY + townZone.y);
      zone.generator = base.createMonsterGenerator(socket);
      if (DEBUG_BUILD) {
        Gdx.app.debug(TAG, String.format(
            "Placed Act3 underground fallback %s (id=%d) at (%d, %d)",
            level.LevelName, levelId, posX, undergroundY + townZone.y));
      }
    }
    for (int levelId : ACT3_UNDERGROUND_SECONDARY) {
      Levels.Entry level = Riiablo.files.Levels.get(levelId);
      if (level == null || findZone(map, levelId) != null) continue;
      int sizeX = NativeDataTables.levelSizeX(level, diff, 1);
      int sizeY = NativeDataTables.levelSizeY(level, diff, 1);
      int posX = (townZone.width / 2 + townZone.x) - (sizeX * 5 / 2);
      undergroundY -= sizeY * 5;
      Zone zone = base.createZoneWithGenerator(map, level, diff, posX,
          undergroundY + townZone.y);
      zone.generator = base.createMonsterGenerator(socket);
      if (DEBUG_BUILD) {
        Gdx.app.debug(TAG, String.format(
            "Placed Act3 underground fallback %s (id=%d) at (%d, %d)",
            level.LevelName, levelId, posX, undergroundY + townZone.y));
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
    configureAct3UndergroundWarps(map);
    Gdx.app.log(TAG, String.format("Act3 native warp table configured: links=%d/%d",
        configured, ACT3_OUTDOOR_LINKS.length));
  }

  /**
   * Replays D2Common's dynamic DRLG_SetWarpId calls for detached Act III
   * dungeons.  Levels.txt contains the native source Vis/Warp rows, but the
   * outdoor destination often receives its return slot only while the maze
   * is linked.  Keep those additions as per-map overrides and materialize a
   * logical marker for every endpoint so floor exits are interactable too.
   */
  private void configureAct3UndergroundWarps(Map map) {
    if (map == null || Riiablo.files == null || Riiablo.files.Levels == null) return;
    IntMap<RuntimeWarpState> states = new IntMap<>();
    for (int levelId : ACT3_UNDERGROUND_SECONDARY) {
      Levels.Entry level = Riiablo.files.Levels.get(levelId);
      if (level != null) states.put(levelId, new RuntimeWarpState(level.Vis, level.Warp));
    }
    for (int levelId : ACT3_UNDERGROUND_SECONDARY) {
      Levels.Entry source = Riiablo.files.Levels.get(levelId);
      RuntimeWarpState sourceState = states.get(levelId);
      if (source == null || sourceState == null || source.Vis == null || source.Warp == null) continue;
      int count = Math.min(8, Math.min(source.Vis.length, source.Warp.length));
      for (int slot = 0; slot < count; slot++) {
        int destinationId = source.Vis[slot];
        if (destinationId <= 0 || source.Warp[slot] < 0) continue;
        Levels.Entry destination = Riiablo.files.Levels.get(destinationId);
        if (destination == null) continue;
        RuntimeWarpState destinationState = states.get(destinationId);
        if (destinationState == null) {
          destinationState = new RuntimeWarpState(destination.Vis, destination.Warp);
          states.put(destinationId, destinationState);
        }
        int reverseSlot = destinationState.ensureDestination(levelId);
        if (reverseSlot < 0) {
          Gdx.app.error(TAG, String.format(
              "Act3 underground warp slot exhausted: %d[%d] -> %d",
              levelId, slot, destinationId));
          continue;
        }
        map.addWarpDestinationOverride(levelId, slot, destinationId);
        map.addWarpDestinationOverride(destinationId, reverseSlot, levelId);
        ensureProgressionWarpMarker(map, levelId, slot);
        ensureProgressionWarpMarker(map, destinationId, reverseSlot);
      }
    }
  }

  /** Pairs emitted special-wall cells after all Act III zones are generated. */
  void linkNativeWarpSpecials(Map map) {
    if (map == null) return;
    // Outdoor border generation can rebuild the Spider Forest special-cell
    // map, so install the two native side-area return markers at the same
    // post-generation point where MapManager will consume them.
    ensureProgressionWarpMarker(map, LEVEL_SPIDERFOREST, 0);
    ensureProgressionWarpMarker(map, LEVEL_SPIDERFOREST, 1);
    map.addWarpDestinationOverride(LEVEL_SPIDERFOREST, 1,
        D2LevelIds.LEVEL_SPIDERCAVE);
    map.addWarpDestinationOverride(LEVEL_SPIDERFOREST, 0,
        D2LevelIds.LEVEL_SPIDERCAVERN);
    relocateAct3WarpMarkers(map);
    int linked = 0;
    int missingZone = 0;
    int missingReverse = 0;
    for (Zone source : new Array.ArrayIterator<>(map.zones)) {
      if (source == null || source.level == null || source.specials == null) continue;
      if (isAct3SecondaryUnderground(source.level.Id)) {
        StringBuilder warpCells = new StringBuilder();
        for (IntMap.Entry<DS1.Cell> special : source.specials.entries()) {
          DS1.Cell cell = special.value;
          if (cell != null && Map.ID.WARPS.contains(cell.id)) {
            if (warpCells.length() > 0) warpCells.append(',');
            warpCells.append(cell.mainIndex).append('/').append(cell.subIndex);
          }
        }
        Gdx.app.log(TAG, String.format("Act3 native warp cells: level=%d count=%d cells=%s",
            source.level.Id, warpCells.length() == 0 ? 0 : warpCells.toString().split(",").length,
            warpCells));
      }
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
          if (isAct3SecondaryUnderground(source.level.Id)
              || isAct3SecondaryUnderground(destination.level.Id)
              || source.level.Id == D2LevelIds.LEVEL_SPIDERCAVE
              || source.level.Id == D2LevelIds.LEVEL_SPIDERCAVERN
              || destination.level.Id == D2LevelIds.LEVEL_SPIDERFOREST) {
            Gdx.app.log(TAG, String.format(
                "Act3 native warp missing reverse: source=%d mainIndex=%d target=%d destination=%d",
                source.level.Id, (int) sourceCell.mainIndex, destinationLevelId,
                destination.level.Id));
          }
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

  private static boolean isAct3SecondaryUnderground(int levelId) {
    for (int candidate : ACT3_UNDERGROUND_SECONDARY) {
      if (candidate == levelId) return true;
    }
    return false;
  }

  private static void relocateAct3WarpMarkers(Map map) {
    for (Zone zone : new Array.ArrayIterator<>(map.zones)) {
      if (zone == null || zone.level == null || zone.specials == null) continue;
      if (zone.level.Id < LEVEL_KURASTDOCKTOWN || zone.level.Id > 102) continue;
      IntMap<Integer> keys = new IntMap<>();
      for (IntMap.Entry<DS1.Cell> entry : zone.specials.entries()) {
        if (entry.value != null && Map.ID.WARPS.contains(entry.value.id)) {
          keys.put(entry.key, entry.key);
        }
      }
      for (IntMap.Entry<Integer> entry : keys.entries()) {
        DS1.Cell cell = zone.specials.get(entry.value);
        if (cell != null) relocateWarpMarkerIfBlocked(zone, entry.value, cell);
      }
    }
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
          && existing.mainIndex == mainIndex) {
        relocateWarpMarkerIfBlocked(zone, entry.key, existing);
        return;
      }
    }
    if (zone.specials == Zone.EMPTY_INT_CELL_MAP) zone.specials = new IntMap<>();
    // Keep synthetic slots at distinct tile hashes.  IntMap is keyed by
    // (layer,x,y), so placing both Spider side-area markers at the exact
    // center would overwrite the first marker and leave one-way topology.
    int tx = Math.max(0, Math.min(zone.tilesX - 1,
        zone.tilesX / 2 + (mainIndex - 1) * 2));
    int ty = Math.max(0, Math.min(zone.tilesY - 1, zone.tilesY / 2));
    com.riiablo.drlg.TileGrid nativeGrid = zone.nativeTileGrid();
    if (nativeGrid != null) {
      int bestDistance = Integer.MAX_VALUE;
      int bestX = tx;
      int bestY = ty;
      for (int candidateY = 0; candidateY < nativeGrid.height; candidateY++) {
        for (int candidateX = 0; candidateX < nativeGrid.width; candidateX++) {
          if (nativeGrid.floorIds[candidateY][candidateX] < 0) continue;
          int distance = Math.abs(candidateX - tx) + Math.abs(candidateY - ty);
          if (distance < bestDistance
              && !zone.specials.containsKey(
                  Zone.tileHashCode(Map.WALL_OFFSET, candidateX, candidateY))) {
            bestDistance = distance;
            bestX = candidateX;
            bestY = candidateY;
          }
        }
      }
      tx = bestX;
      ty = bestY;
    }
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

  private static void relocateWarpMarkerIfBlocked(Zone zone, int oldKey, DS1.Cell cell) {
    if (zone == null || zone.specials == null || zone.level == null) return;
    int oldTx = Zone.tileHashX(oldKey);
    int oldTy = Zone.tileHashY(oldKey);
    int offsetX = warpOffsetX(zone, cell.mainIndex);
    int offsetY = warpOffsetY(zone, cell.mainIndex);
    Vector2 landing = new Vector2();
    int worldX = zone.x() + oldTx * DT1.Tile.SUBTILE_SIZE + offsetX;
    int worldY = zone.y() + oldTy * DT1.Tile.SUBTILE_SIZE + offsetY;
    if (zone.findFreeCoordinates(new Vector2(worldX, worldY), 1, 8, true, landing)) return;
    int bestTx = oldTx;
    int bestTy = oldTy;
    int bestDistance = Integer.MAX_VALUE;
    for (int ty = 0; ty < zone.tilesY; ty++) {
      for (int tx = 0; tx < zone.tilesX; tx++) {
        if (zone.nativeTileGrid() != null
            && (tx >= zone.nativeTileGrid().width || ty >= zone.nativeTileGrid().height
                || zone.nativeTileGrid().floorIds[ty][tx] < 0)) continue;
        int key = Zone.tileHashCode(Map.WALL_OFFSET, tx, ty);
        if (key != oldKey && zone.specials.containsKey(key)) continue;
        int candidateX = zone.x() + tx * DT1.Tile.SUBTILE_SIZE + offsetX;
        int candidateY = zone.y() + ty * DT1.Tile.SUBTILE_SIZE + offsetY;
        if (!zone.findFreeCoordinates(new Vector2(candidateX, candidateY), 1, 8, true, landing)) continue;
        int distance = Math.abs(tx - oldTx) + Math.abs(ty - oldTy);
        if (distance < bestDistance) {
          bestDistance = distance;
          bestTx = tx;
          bestTy = ty;
        }
      }
    }
    if (bestDistance == Integer.MAX_VALUE || (bestTx == oldTx && bestTy == oldTy)) return;
    zone.specials.remove(oldKey);
    zone.putCell(Map.WALL_OFFSET, bestTx, bestTy, cell);
    Gdx.app.log(TAG, String.format(
        "Act3 warp marker relocated to walkable tile: level=%d mainIndex=%d from=(%d,%d) to=(%d,%d)",
        zone.level.Id, (int) cell.mainIndex, oldTx, oldTy, bestTx, bestTy));
  }

  private static int warpOffsetX(Zone zone, int mainIndex) {
    if (zone == null || zone.level == null || zone.level.Warp == null
        || mainIndex < 0 || mainIndex >= zone.level.Warp.length
        || zone.level.Warp[mainIndex] < 0 || Riiablo.files == null || Riiablo.files.LvlWarp == null) return 0;
    LvlWarp.Entry entry = Riiablo.files.LvlWarp.get(zone.level.Warp[mainIndex]);
    return entry == null ? 0 : entry.OffsetX;
  }

  private static int warpOffsetY(Zone zone, int mainIndex) {
    if (zone == null || zone.level == null || zone.level.Warp == null
        || mainIndex < 0 || mainIndex >= zone.level.Warp.length
        || zone.level.Warp[mainIndex] < 0 || Riiablo.files == null || Riiablo.files.LvlWarp == null) return 0;
    LvlWarp.Entry entry = Riiablo.files.LvlWarp.get(zone.level.Warp[mainIndex]);
    return entry == null ? 0 : entry.OffsetY;
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
      int target = map.getWarpDestinationOverride(destination.level.Id, cell.mainIndex);
      if (target <= 0) {
        if (destination.level.Warp == null || cell.mainIndex < 0
            || cell.mainIndex >= destination.level.Warp.length
            || destination.level.Warp[cell.mainIndex] < 0) continue;
        if (destination.level.Vis != null && cell.mainIndex < destination.level.Vis.length) {
          target = destination.level.Vis[cell.mainIndex];
        }
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
