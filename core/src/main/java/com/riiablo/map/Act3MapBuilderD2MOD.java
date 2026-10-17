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
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;

/**
 * Act3 地图生成器 - 完全复刻 D2MOD 实现
 * 
 * 参考 D2MOD:
 * - source/D2Common/src/Drlg/DrlgOutPlace.cpp (DRLGOUTPLACE_InitAct3OutdoorLevel)
 * - source/D2Common/src/Drlg/DrlgOutJung.cpp (DRLGOUTJUNG_BuildJungle, DRLGOUTPLACE_BuildKurast)
 */
public final class Act3MapBuilderD2MOD extends MapBuilderResources implements MapBuilder {

  public Act3MapBuilderD2MOD() { super(); }
  public Act3MapBuilderD2MOD(MapResources resources) { super(resources); }

  private static final String TAG = "Act3MapBuilderD2MOD";
  private static final boolean DEBUG = true;
  private static final boolean DEBUG_BUILD = DEBUG && true;

  // Act3 区域定义
  // Keep these ids sourced from the same table used by the native D2MOO
  // bridge: Arcane Sanctuary is 74 and Act III starts at Kurast Docks (75).
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
    Levels.Entry townLevel = files().Levels.get(LEVEL_KURASTDOCKTOWN);
    if (townLevel == null) {
      Gdx.app.error(TAG, "Town level not found: " + LEVEL_KURASTDOCKTOWN);
      return;
    }

    LvlPrest.Entry townPreset = null;
    for (LvlPrest.Entry p : files().LvlPrest) {
      if (p.LevelId == LEVEL_KURASTDOCKTOWN) {
        townPreset = p;
        break;
      }
    }

    if (townPreset == null) {
      townPreset = files().LvlPrest.get(Map.ACT_DEF[2]);
    }

    int fileId[] = new int[6];
    int numFiles = Preset.getPresets(townPreset, fileId);
    if (numFiles == 0) {
      Gdx.app.error(TAG, "No valid presets found for town");
      return;
    }
    int selectIndex = MathUtils.random(numFiles - 1);
    int select = fileId[selectIndex];

    BaseMapBuilderD2MOD base = new BaseMapBuilderD2MOD(resources) {};
    base.factory = factory;
    base.socket = socket;
    Zone townZone = base.createZoneWithPreset(map, townLevel, townPreset, select, 
        townLevel.OffsetX, townLevel.OffsetY, true);

    // 2. 创建丛林区域（SPIDERFOREST, GREATMARSH, FLAYERJUNGLE）
    // 参考 DRLGOUTJUNG_BuildJungle
    int[] jungleLevels = {LEVEL_SPIDERFOREST, LEVEL_GREATMARSH, LEVEL_FLAYERJUNGLE};
    int posY = 0;
    
    for (int levelId : jungleLevels) {
      Levels.Entry level = files().Levels.get(levelId);
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
      Levels.Entry level = files().Levels.get(levelId);
      if (level == null) continue;

      LvlPrest.Entry preset = null;
      for (LvlPrest.Entry p : files().LvlPrest) {
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
      Levels.Entry level = files().Levels.get(levelId);
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
      Levels.Entry level = files().Levels.get(levelId);
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
      Levels.Entry level = files().Levels.get(levelId);
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
    Levels.Entry travincalLevel = files().Levels.get(LEVEL_TRAVINCAL);
    if (travincalLevel != null) {
      LvlPrest.Entry travincalPreset = null;
      for (LvlPrest.Entry p : files().LvlPrest) {
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
    Act3D2MOOLayoutBridge.populateZones(map.d2mooContext(), seed, diff, map);
    // Travincal's native outdoor link is represented by an edge link rather
    // than a preset wall cell.  Seed one logical VIS_0_00 marker so the
    // existing MapManager/WarpInteractor path exposes the Durance entrance;
    // the destination is resolved by the native Act III warp table below.
    ensureProgressionWarpMarker(map, LEVEL_TRAVINCAL, 0);

    // 添加高级功能：边界、路径、传送点、神殿等
    // 参考 D2MOD: DRLGOUTPLACE_InitAct3OutdoorLevel
    for (Zone zone : map.zones) {
      if (!zone.town) {
        placeNativeJungleTransition(zone);
        placeNativeKurastBorders(zone, jungleInterlink(seed));
        placeNativeKurastSpecials(zone);
        placeNativeKurastFills(zone);
        placeNativeCausewayBridge(zone);
        placeNativeTravincalEdges(zone);
        // D2MOO's Causeway/Travincal use dedicated Bridge/edge presets;
        // generic outdoor borders apply only to jungle and Kurast districts.
        if (usesGenericOutdoorBorders(zone.level.Id)) {
          OutdoorFeatures.placeBorders(zone, seed, 2);
        }
        
        // Act III waypoint substitutions are generated by the native bridge.
        // Do not run the old LvlPrest filename heuristic here: it cannot see
        // Waypoint.ds1/WaySmall.ds1 substitutions and would both duplicate
        // native objects and report false failures for Great Marsh.  Shrines
        // remain compatibility features for the outdoor fallback.
        if (zone.level.Id >= LEVEL_SPIDERFOREST && zone.level.Id <= LEVEL_FLAYERJUNGLE) {
          OutdoorFeatures.placeShrines(zone, seed, 3);
        }
      }
    }
  }

  static void placeNativeKurastBorders(Zone zone) {
    placeNativeKurastBorders(zone, 0);
  }

  static void placeNativeKurastBorders(Zone zone, int jungleInterlink) {
    if (zone.map.files() == null || zone.map.files().LvlPrest == null) return;
    placeNativeKurastBorders(zone, zone.map.files().LvlPrest::get, jungleInterlink);
  }

  static boolean usesGenericOutdoorBorders(int levelId) {
    return levelId >= LEVEL_SPIDERFOREST
        && levelId <= D2LevelIds.LEVEL_UPPERKURAST;
  }

  /** Compatibility border pass; native exports already own their gates and borders. */
  static void placeNativeKurastBorders(Zone zone,
      java.util.function.IntFunction<LvlPrest.Entry> presets) {
    placeNativeKurastBorders(zone, presets, 0);
  }

  /**
   * Compatibility equivalent of D2MOO's {@code bJungleInterlink}-dependent
   * Kurast gate placement.  The native bridge owns this choice when a full
   * MPQ export is available; this overload keeps reduced-resource maps seed
   * deterministic as well.
   */
  static void placeNativeKurastBorders(Zone zone,
      java.util.function.IntFunction<LvlPrest.Entry> presets, int jungleInterlink) {
    if (zone == null || zone.presets == null || zone.gridsX < 3 || zone.gridsY < 3
        || zone.level == null || zone.nativeTileGrid() != null || presets == null) return;
    int base;
    if (zone.level.Id == D2LevelIds.LEVEL_LOWERKURAST) {
      base = com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SLUMS_BORDER_N;
    } else if (zone.level.Id == D2LevelIds.LEVEL_KURASTBAZAAR) {
      base = com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_BURBS_BORDER_N;
    } else if (zone.level.Id == D2LevelIds.LEVEL_UPPERKURAST) {
      base = com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_METRO_BORDER_N;
    }
    else return;
    // DrlgOutJung.cpp: N, S, E, W, NE, NW, SE, SW are consecutive Defs.
    placeKurastBorder(zone, presets, base + 5, 0, 0);
    placeKurastBorder(zone, presets, base + 4, zone.gridsX - 1, 0);
    placeKurastBorder(zone, presets, base + 7, 0, zone.gridsY - 1);
    placeKurastBorder(zone, presets, base + 6, zone.gridsX - 1, zone.gridsY - 1);
    int lastX = zone.gridsX - 1;
    int gateNorthX;
    int gateSouthX;
    if (zone.level.Id == D2LevelIds.LEVEL_LOWERKURAST) {
      // Native lower Kurast: north gate follows the interlink, south gate is
      // centered (v10 = 1/last-1, v5 = nWidth/2).
      gateNorthX = jungleInterlink != 0 ? 1 : lastX - 1;
      gateSouthX = lastX / 2;
    } else if (zone.level.Id == D2LevelIds.LEVEL_KURASTBAZAAR) {
      // Native bazaar: the two gates are opposite sides of the interlink.
      gateNorthX = jungleInterlink != 0 ? lastX - 1 : 1;
      gateSouthX = jungleInterlink != 0 ? 1 : lastX - 1;
    } else {
      // Native upper Kurast: north gate is centered, south gate follows the
      // interlink (v8 = last-1/1).
      gateNorthX = lastX / 2;
      gateSouthX = jungleInterlink != 0 ? lastX - 1 : 1;
    }
    int gateNorth = base + 8;
    int gateSouth = base + 9;
    // Upper Kurast uses the same double-width advance on its north gate.
    for (int x = 1; x < zone.gridsX - 1;
        x += zone.level.Id == D2LevelIds.LEVEL_UPPERKURAST && x == gateNorthX ? 2 : 1) {
      placeKurastBorder(zone, presets, x == gateNorthX ? gateNorth : base, x, 0);
    }
    // D2MOO advances one extra grid after the Lower Kurast south gate; the
    // 16x8 gate owns that adjacent cell and must not be filled by a border.
    for (int x = 1; x < zone.gridsX - 1;
        x += zone.level.Id == D2LevelIds.LEVEL_LOWERKURAST && x == gateSouthX ? 2 : 1) {
      placeKurastBorder(zone, presets, x == gateSouthX ? gateSouth : base + 1,
          x, zone.gridsY - 1);
    }
    for (int y = 1; y < zone.gridsY - 1; y++) {
      placeKurastBorder(zone, presets, base + 3, 0, y);
      placeKurastBorder(zone, presets, base + 2, zone.gridsX - 1, y);
    }
    // Compatibility maps do not expose the native room graph, so only gate
    // coordinates are
    // mirrored; native TileGrid exports remain authoritative when present.
  }

  /** Mirrors D2MOO's two initial DRLG rolls before bJungleInterlink. */
  static int jungleInterlink(int gameSeed) {
    D2Seed seed = new D2Seed();
    Seed.initLowSeed(seed, gameSeed);
    Seed.rollRandomNumber(seed); // drlg.startSeed
    return (int) (Seed.rollRandomNumber(seed) & 1L);
  }

  private static void placeKurastBorder(Zone zone,
      java.util.function.IntFunction<LvlPrest.Entry> presets, int def, int gx, int gy) {
    if (zone.presets[gx][gy] != null) return;
    LvlPrest.Entry p = presets.apply(def);
    if (p == null || p.File == null) return;
    int[] files = new int[6];
    int count = Preset.getPresets(p, files);
    if (count > 0) zone.presets[gx][gy] = Preset.of(p, files[0]);
  }

  /** Compatibility anchors for Kurast sewer/temple/waypoint presets. */
  static void placeNativeKurastSpecials(Zone zone) {
    if (zone == null || zone.presets == null || zone.level == null
        || zone.nativeTileGrid() != null || zone.map.files() == null
        || zone.map.files().LvlPrest == null || zone.gridsX < 5 || zone.gridsY < 5) return;
    int sewer = -1;
    int temple = -1;
    int waypoint = com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_BURBS_WAYPOINT;
    int sx = 3, sy = 3;
    if (zone.level.Id == D2LevelIds.LEVEL_KURASTBAZAAR) {
      sewer = com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_BURBS_SEWER;
      temple = com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_BURBS_TEMPLE;
    } else if (zone.level.Id == D2LevelIds.LEVEL_UPPERKURAST) {
      sewer = com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_METRO_SEWER;
      temple = com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_METROTEMPLE;
      sy = zone.gridsY - 4;
    } else if (zone.level.Id != D2LevelIds.LEVEL_LOWERKURAST) {
      return;
    }
    int cx = zone.gridsX / 2, cy = zone.gridsY / 2;
    if (sewer >= 0) placeKurastSpecial(zone, sewer, sx, sy);
    if (temple >= 0) placeKurastSpecial(zone, temple, cx, cy);
    placeKurastSpecial(zone, waypoint, Math.max(1, cx - 1), Math.max(1, cy - 1));
  }

  static void placeNativeCausewayBridge(Zone zone) {
    if (zone == null || zone.level == null
        || zone.level.Id != LEVEL_KURASTCAUSEWAY || zone.nativeTileGrid() != null
        || zone.presets == null || zone.map.files() == null || zone.map.files().LvlPrest == null) return;
    placeKurastSpecial(zone, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_BRIDGE, 0, 0);
  }

  static void placeNativeTravincalEdges(Zone zone) {
    if (zone == null || zone.level == null || zone.level.Id != LEVEL_TRAVINCAL
        || zone.nativeTileGrid() != null || zone.presets == null
        || zone.map.files() == null || zone.map.files().LvlPrest == null) return;
    int[][] entries = {
        {795, 0, 0}, {796, 2, 0}, {797, 6, 0},
        {798, 0, 4}, {799, 2, 4}, {800, 6, 4}
    };
    for (int[] entry : entries) {
      if (entry[1] < zone.gridsX && entry[2] < zone.gridsY) {
        placeKurastSpecial(zone, entry[0], entry[1], entry[2]);
      }
    }
  }

  private static void placeKurastSpecial(Zone zone, int def, int gx, int gy) {
    if (gx < 0 || gy < 0 || gx >= zone.gridsX || gy >= zone.gridsY
        || zone.presets[gx][gy] != null) return;
    LvlPrest.Entry p = zone.map.files().LvlPrest.get(def);
    if (p == null || p.File == null) return;
    int[] files = new int[6];
    int count = Preset.getPresets(p, files);
    if (count > 0) zone.presets[gx][gy] = Preset.of(p, files[0]);
  }

  /** Stable compatibility fallback for D2MOO's four 16x16 Kurast fills. */
  static void placeNativeKurastFills(Zone zone) {
    if (zone == null || zone.level == null || zone.presets == null
        || zone.nativeTileGrid() != null || zone.gridsX < 5 || zone.gridsY < 5
        || zone.map.files() == null || zone.map.files().LvlPrest == null) return;
    int def;
    if (zone.level.Id == D2LevelIds.LEVEL_LOWERKURAST) def = 760;
    else if (zone.level.Id == D2LevelIds.LEVEL_KURASTBAZAAR) def = 777;
    else if (zone.level.Id == D2LevelIds.LEVEL_UPPERKURAST) def = 793;
    else return;
    int placed = 0;
    boolean[][] occupied = new boolean[zone.gridsX][zone.gridsY];
    for (int gy = 1; gy < zone.gridsY - 1 && placed < 4; gy++) {
      for (int gx = 1; gx < zone.gridsX - 1 && placed < 4; gx++) {
        if (occupied[gx][gy] || zone.presets[gx][gy] != null
            || gx + 1 >= zone.gridsX || gy + 1 >= zone.gridsY
            || zone.presets[gx + 1][gy] != null || zone.presets[gx][gy + 1] != null
            || zone.presets[gx + 1][gy + 1] != null) continue;
        LvlPrest.Entry p = zone.map.files().LvlPrest.get(def);
        if (p == null || p.File == null) return;
        int[] files = new int[6];
        int count = Preset.getPresets(p, files);
        if (count == 0) return;
        zone.presets[gx][gy] = Preset.of(p, files[0]);
        occupied[gx][gy] = occupied[gx + 1][gy] =
            occupied[gx][gy + 1] = occupied[gx + 1][gy + 1] = true;
        placed++;
      }
    }
    int smallA = def - 2; // 08x16
    int smallB = def - 1; // 16x08
    int smallC = def - 3; // 08x08
    placeKurastFill(zone, smallA, 2, 1);
    placeKurastFill(zone, smallB, 1, 2);
    placeKurastFill(zone, smallC, 1, 1);
    placeKurastFill(zone, smallC, 1, 1);
  }

  private static void placeKurastFill(Zone zone, int def, int width, int height) {
    LvlPrest.Entry p = zone.map.files().LvlPrest.get(def);
    if (p == null || p.File == null || Preset.getPresets(p, new int[6]) == 0) return;
    int[] files = new int[6];
    int count = Preset.getPresets(p, files);
    for (int gy = 1; gy + height <= zone.gridsY - 1; gy++) {
      for (int gx = 1; gx + width <= zone.gridsX - 1; gx++) {
        boolean free = true;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
          if (zone.presets[gx + x][gy + y] != null) free = false;
        }
        if (!free) continue;
        zone.presets[gx][gy] = Preset.of(p, files[0]);
        return;
      }
    }
  }

  /** Places the native jungle chain's head/tail transition preset anchors. */
  static void placeNativeJungleTransition(Zone zone) {
    if (zone == null || zone.presets == null || zone.level == null
        || zone.nativeTileGrid() != null || zone.gridsX < 1 || zone.gridsY < 1) return;
    int def;
    int gx;
    int gy;
    if (zone.level.Id == LEVEL_SPIDERFOREST) {
      def = 715;
      gx = 0;
      gy = zone.gridsY - 1;
    } else if (zone.level.Id == LEVEL_FLAYERJUNGLE) {
      def = 716;
      gx = 0;
      gy = 0;
    } else {
      return;
    }
    if (zone.presets[gx][gy] != null || zone.map.files() == null || zone.map.files().LvlPrest == null) return;
    LvlPrest.Entry p = zone.map.files().LvlPrest.get(def);
    if (p == null || p.File == null) return;
    int[] files = new int[6];
    int count = Preset.getPresets(p, files);
    if (count > 0) zone.presets[gx][gy] = Preset.of(p, files[0]);
  }

  /**
   * Mirrors the runtime half of D2Common's Act III outdoor linking.  The
   * native generator appends the adjacent level to the first empty LvlWarp
   * slot instead of replacing Levels.txt, so preserve that slot behaviour.
   */
  void configureAct3OutdoorWarps(Map map) {
    if (map == null || files() == null || files().Levels == null) return;
    IntMap<RuntimeWarpState> states = new IntMap<>();
    for (int[] link : ACT3_OUTDOOR_LINKS) {
      for (int levelId : link) {
        if (states.containsKey(levelId)) continue;
        Levels.Entry level = files().Levels.get(levelId);
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
    // Durance levels use native edge links rather than a visible DS1 wall in
    // every seed.  Materialize the logical slots so both entry and return
    // endpoints are available to MapManager/WarpInteractor.
    ensureProgressionWarpMarker(map, D2LevelIds.LEVEL_DURANCEOFHATELEVEL1, 0);
    ensureProgressionWarpMarker(map, D2LevelIds.LEVEL_DURANCEOFHATELEVEL1, 1);
    ensureProgressionWarpMarker(map, D2LevelIds.LEVEL_DURANCEOFHATELEVEL2, 0);
    ensureProgressionWarpMarker(map, D2LevelIds.LEVEL_DURANCEOFHATELEVEL2, 1);
    ensureProgressionWarpMarker(map, D2LevelIds.LEVEL_DURANCEOFHATELEVEL3, 3);
    configureAct3UndergroundWarps(map);
    configureAct3DungeonGraphWarps(map);
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
    if (map == null || files() == null || files().Levels == null) return;
    IntMap<RuntimeWarpState> states = new IntMap<>();
    for (int levelId : ACT3_UNDERGROUND_SECONDARY) {
      Levels.Entry level = files().Levels.get(levelId);
      if (level != null) states.put(levelId, new RuntimeWarpState(level.Vis, level.Warp));
    }
    for (int levelId : ACT3_UNDERGROUND_SECONDARY) {
      Levels.Entry source = files().Levels.get(levelId);
      RuntimeWarpState sourceState = states.get(levelId);
      if (source == null || sourceState == null || source.Vis == null || source.Warp == null) continue;
      int count = Math.min(8, Math.min(source.Vis.length, source.Warp.length));
      for (int slot = 0; slot < count; slot++) {
        int destinationId = source.Vis[slot];
        if (destinationId <= 0 || source.Warp[slot] < 0) continue;
        Levels.Entry destination = files().Levels.get(destinationId);
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

  /**
   * Completes all static Act III dungeon edges, including the outdoor→cave
   * entrances that are not represented by a secondary dungeon row.  Some
   * reduced DS1 exports contain the Levels.txt graph but omit one SPECIAL_10
   * cell; materializing the marker here keeps the authoritative Warp entity
   * available without changing the native level table.
   */
  private void configureAct3DungeonGraphWarps(Map map) {
    if (map == null || files() == null || files().Levels == null) return;
    IntMap<RuntimeWarpState> states = new IntMap<>();
    for (Levels.Entry level : files().Levels) {
      if (level != null && level.Id >= LEVEL_SPIDERFOREST && level.Id <= 102) {
        states.put(level.Id, new RuntimeWarpState(level.Vis, level.Warp));
      }
    }
    int configured = 0;
    for (Levels.Entry source : files().Levels) {
      if (source == null || source.Id < LEVEL_SPIDERFOREST || source.Id > 102
          || source.Vis == null || source.Warp == null) continue;
      int count = Math.min(8, Math.min(source.Vis.length, source.Warp.length));
      for (int slot = 0; slot < count; slot++) {
        int destinationId = source.Vis[slot];
        if (source.Warp[slot] < 0 || destinationId < LEVEL_SPIDERFOREST
            || destinationId > 102 || !isStaticAct3DungeonEdge(source.Id, destinationId)) {
          continue;
        }
        Levels.Entry destination = files().Levels.get(destinationId);
        if (destination == null || findZone(map, source.Id) == null
            || findZone(map, destinationId) == null) continue;
        RuntimeWarpState destinationState = states.get(destinationId);
        if (destinationState == null) continue;
        int reverseSlot = findRuntimeWarpSlot(destination.Vis, destination.Warp, source.Id);
        if (reverseSlot < 0) reverseSlot = destinationState.ensureDestination(source.Id);
        if (reverseSlot < 0) continue;
        map.addWarpDestinationOverride(source.Id, slot, destinationId);
        map.addWarpDestinationOverride(destinationId, reverseSlot, source.Id);
        ensureProgressionWarpMarker(map, source.Id, slot);
        ensureProgressionWarpMarker(map, destinationId, reverseSlot);
        configured++;
      }
    }
    // A few 1.10f exports leave the outdoor side-area Vis/Warp row empty;
    // D2Common injects these links while building the jungle.  Keep explicit
    // fallbacks for those native entrances, using the first available slot
    // and the same collision-safe marker path as the table-driven edges.
    ensureAct3SideDungeonFallback(map, LEVEL_GREATMARSH,
        D2LevelIds.LEVEL_SWAMPYPITLVL1);
    ensureAct3SideDungeonFallback(map, LEVEL_FLAYERJUNGLE,
        D2LevelIds.LEVEL_FLAYERDUNGEONLVL1);
    ensureAct3SideDungeonFallback(map, D2LevelIds.LEVEL_KURASTBAZAAR,
        D2LevelIds.LEVEL_SEWERSA3LEV1);
    Gdx.app.log(TAG, "Act3 dungeon graph warps configured=" + configured);
  }

  private static void ensureAct3SideDungeonFallback(Map map, int sourceLevelId,
      int destinationLevelId) {
    Zone source = findZone(map, sourceLevelId);
    Zone destination = findZone(map, destinationLevelId);
    Levels.Entry sourceLevel = map.files().Levels.get(sourceLevelId);
    Levels.Entry destinationLevel = map.files().Levels.get(destinationLevelId);
    if (source == null || destination == null || sourceLevel == null || destinationLevel == null) return;
    int sourceSlot = findRuntimeWarpSlot(sourceLevel.Vis, sourceLevel.Warp, destinationLevelId);
    if (sourceSlot < 0) sourceSlot = firstFreeWarpSlot(source);
    int reverseSlot = findRuntimeWarpSlot(destinationLevel.Vis, destinationLevel.Warp, sourceLevelId);
    if (reverseSlot < 0) reverseSlot = firstFreeWarpSlot(destination);
    if (sourceSlot < 0 || reverseSlot < 0) {
      Gdx.app.error(TAG, "Act3 side dungeon fallback slot unavailable: "
          + sourceLevelId + "->" + destinationLevelId);
      return;
    }
    map.addWarpDestinationOverride(sourceLevelId, sourceSlot, destinationLevelId);
    map.addWarpDestinationOverride(destinationLevelId, reverseSlot, sourceLevelId);
    ensureProgressionWarpMarker(map, sourceLevelId, sourceSlot);
    ensureProgressionWarpMarker(map, destinationLevelId, reverseSlot);
  }

  private static int firstFreeWarpSlot(Zone zone) {
    if (zone == null || zone.specials == null) return 0;
    boolean[] used = new boolean[8];
    for (IntMap.Entry<DS1.Cell> entry : zone.specials.entries()) {
      DS1.Cell cell = entry.value;
      if (cell != null && Map.ID.WARPS.contains(cell.id)
          && cell.mainIndex >= 0 && cell.mainIndex < used.length) used[cell.mainIndex] = true;
    }
    for (int i = 0; i < used.length; i++) if (!used[i]) return i;
    return -1;
  }

  static boolean isStaticAct3DungeonEdge(int sourceLevelId, int destinationLevelId) {
    if (sourceLevelId < LEVEL_SPIDERFOREST || destinationLevelId < LEVEL_SPIDERFOREST
        || sourceLevelId > 102 || destinationLevelId > 102) return false;
    // Linear outdoor progression and Durance are handled by their dedicated
    // native-link paths; do not allocate duplicate slots here.
    boolean sourceOutdoor = isAct3Outdoor(sourceLevelId);
    boolean destinationOutdoor = isAct3Outdoor(destinationLevelId);
    if (sourceOutdoor && destinationOutdoor) return false;
    if (sourceLevelId >= D2LevelIds.LEVEL_DURANCEOFHATELEVEL1
        || destinationLevelId >= D2LevelIds.LEVEL_DURANCEOFHATELEVEL1) return false;
    return true;
  }

  private static boolean isAct3Outdoor(int levelId) {
    for (int candidate : ACT3_OUTDOOR_CHAIN) if (candidate == levelId) return true;
    return false;
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
        int destinationLevelId = map.getWarpDestinationOverride(
            source.level.Id, sourceCell.mainIndex);
        boolean nativeSlotValid = source.level.Warp != null && sourceCell.mainIndex >= 0
            && sourceCell.mainIndex < source.level.Warp.length
            && source.level.Warp[sourceCell.mainIndex] >= 0;
        // A reduced export can omit the native LvlWarp row entirely.  An
        // explicit runtime override plus a synthetic marker is authoritative
        // and must still be paired into a Warp entity.
        if (!nativeSlotValid && destinationLevelId <= 0) continue;
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
          if ((source.level.Id >= LEVEL_KURASTDOCKTOWN && source.level.Id <= 102)
              || (destination.level.Id >= LEVEL_KURASTDOCKTOWN && destination.level.Id <= 102)) {
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
    int worldX = zone.x() + oldTx * DT1.Tile.SUBTILE_SIZE + offsetX;
    int worldY = zone.y() + oldTy * DT1.Tile.SUBTILE_SIZE + offsetY;
    if (hasPlayerWarpApproach(zone, worldX, worldY)) return;
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
        if (!hasPlayerWarpApproach(zone, candidateX, candidateY)) continue;
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

  static boolean hasPlayerWarpApproach(Zone zone, int anchorX, int anchorY) {
    Vector2 free = new Vector2();
    int mask = com.riiablo.engine.server.NativeGroundPathProfile.PLAYER.terrainFlags;
    for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
      // Warp range is 3+2. Leave 1.5 tiles for the real network stop-click
      // allowance; a size-one tile eight cells away is not an approach.
      if (dx * dx + dy * dy > 3.5f * 3.5f) continue;
      if (zone.findFreeCoordinates(new Vector2(anchorX + dx, anchorY + dy),
          2, 0, mask, true, free)) return true;
    }
    return false;
  }

  private static int warpOffsetX(Zone zone, int mainIndex) {
    if (zone == null || zone.level == null || zone.level.Warp == null
        || mainIndex < 0 || mainIndex >= zone.level.Warp.length
        || zone.level.Warp[mainIndex] < 0 || zone.map.files() == null || zone.map.files().LvlWarp == null) return 0;
    LvlWarp.Entry entry = zone.map.files().LvlWarp.get(zone.level.Warp[mainIndex]);
    return entry == null ? 0 : entry.OffsetX;
  }

  private static int warpOffsetY(Zone zone, int mainIndex) {
    if (zone == null || zone.level == null || zone.level.Warp == null
        || mainIndex < 0 || mainIndex >= zone.level.Warp.length
        || zone.level.Warp[mainIndex] < 0 || zone.map.files() == null || zone.map.files().LvlWarp == null) return 0;
    LvlWarp.Entry entry = zone.map.files().LvlWarp.get(zone.level.Warp[mainIndex]);
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
