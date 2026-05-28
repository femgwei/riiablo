package com.riiablo.map;

import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlPrest;
import com.riiablo.engine.EntityFactory;
import com.riiablo.map.Map.Preset;
import com.riiablo.map.Map.Zone;

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
  private static final int LEVEL_KURASTDOCKTOWN = 75;
  private static final int LEVEL_SPIDERFOREST = 76;
  private static final int LEVEL_GREATMARSH = 77;
  private static final int LEVEL_FLAYERJUNGLE = 78;
  private static final int LEVEL_LOWERKURAST = 79;
  private static final int LEVEL_KURASTBAZAAR = 80;
  private static final int LEVEL_UPPERKURAST = 81;
  private static final int LEVEL_KURASTCAUSEWAY = 82;
  private static final int LEVEL_TRAVINCAL = 83;

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

      int sizeX = level.SizeX[diff];
      int sizeY = level.SizeY[diff];
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
        int sizeX = level.SizeX[diff];
        int sizeY = level.SizeY[diff];
        int posX = (townZone.width / 2 + townZone.x) - (sizeX * 5 / 2);
        posY -= sizeY * 5;
        Zone zone = base.createZoneWithGenerator(map, level, diff, posX, posY + townZone.y);
        zone.generator = base.createMonsterGenerator(socket);
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
}
