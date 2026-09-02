package com.riiablo.map;

import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlPrest;
import com.riiablo.engine.server.NativeDataTables;
import com.riiablo.engine.EntityFactory;
import com.riiablo.map.Map.Preset;
import com.riiablo.map.Map.Zone;

/**
 * Act5 地图生成器 - 完全复刻 D2MOD 实现
 * 
 * 参考 D2MOD:
 * - source/D2Common/src/Drlg/DrlgOutPlace.cpp (gAct5OutdoorDrlgLink, gAct5TundraDrlgLink)
 * - source/D2Common/src/Drlg/DrlgOutSiege.cpp (DRLGOUTSIEGE_InitAct5OutdoorLevel)
 */
public enum Act5MapBuilderD2MOD implements MapBuilder {
  INSTANCE;

  private static final String TAG = "Act5MapBuilderD2MOD";
  private static final boolean DEBUG = true;
  private static final boolean DEBUG_BUILD = DEBUG && true;

  // D2MOD: gAct5OutdoorDrlgLink 数组
  private static final int LEVEL_HARROGATH = 109;
  private static final int LEVEL_BLOODYFOOTHILLS = 110;
  private static final int LEVEL_ID_ACT5_BARRICADE_1 = 111;
  private static final int LEVEL_ARREATPLATEAU = 112;
  private static final int LEVEL_TUNDRAWASTELANDS = 113;

  @Wire(name = "factory")
  protected EntityFactory factory;

  @Wire(name = "client.socket", failOnNull = false)
  protected com.badlogic.gdx.net.Socket socket;

  @Override
  public void generate(Map map, int seed, int diff) {
    // 重要：设置随机种子，确保多人游戏中所有客户端生成相同的地图
    MathUtils.random.setSeed(seed);

    // D2MOD: gAct5OutdoorDrlgLink 数组
    BaseMapBuilderD2MOD.LevelLink[] act5Links = new BaseMapBuilderD2MOD.LevelLink[] {
      new BaseMapBuilderD2MOD.LevelLink(LEVEL_HARROGATH, -1, -1),              // 0: 城镇
      new BaseMapBuilderD2MOD.LevelLink(LEVEL_BLOODYFOOTHILLS, 0, -1),        // 1
      new BaseMapBuilderD2MOD.LevelLink(LEVEL_ID_ACT5_BARRICADE_1, 1, -1),   // 2
      new BaseMapBuilderD2MOD.LevelLink(LEVEL_ARREATPLATEAU, 2, -1),         // 3
      null // 结束标记
    };

    BaseMapBuilderD2MOD.LevelLinkData linkData = new BaseMapBuilderD2MOD.LevelLinkData();

    // 初始化区域尺寸
    for (int i = 0; i < act5Links.length && act5Links[i] != null; i++) {
      linkData.links[i] = act5Links[i];
      Levels.Entry level = Riiablo.files.Levels.get(act5Links[i].level);
      if (level == null) {
        Gdx.app.error(TAG, "Level not found: " + act5Links[i].level);
        continue;
      }
      int sizeX = NativeDataTables.levelSizeX(level, diff, 1);
      int sizeY = NativeDataTables.levelSizeY(level, diff, 1);
      linkData.coords[i].width = sizeX * 5;
      linkData.coords[i].height = sizeY * 5;
    }

    // D2MOD: sub_6FD823C0 - 主要链接循环
    int counter = 0;
    while (counter < act5Links.length && act5Links[counter] != null) {
      boolean success = false;
      Levels.Entry level = Riiablo.files.Levels.get(act5Links[counter].level);
      if (level == null) {
        Gdx.app.error(TAG, "Level not found: " + act5Links[counter].level);
        break;
      }
      
      BaseMapBuilderD2MOD base = new BaseMapBuilderD2MOD() {};
      
      if (act5Links[counter].level == LEVEL_HARROGATH || 
          act5Links[counter].level == LEVEL_BLOODYFOOTHILLS) {
        // sub_6FD81330 - 城镇和 BLOODYFOOTHILLS
        success = base.placeFirstLevel(linkData, counter, level);
      } else if (act5Links[counter].level == LEVEL_ID_ACT5_BARRICADE_1) {
        // DRLGOUTROOM_LinkLevelsByLevelCoords - 使用坐标连接
        if (counter > 0 && act5Links[counter].levelLink >= 0) {
          int levelLink = act5Links[counter].levelLink;
          linkData.coords[counter].x = linkData.coords[levelLink].x + linkData.coords[levelLink].width;
          linkData.coords[counter].y = linkData.coords[levelLink].y;
          success = true;
        } else {
          linkData.coords[counter].x = level.OffsetX;
          linkData.coords[counter].y = level.OffsetY;
          success = true;
        }
      } else if (act5Links[counter].level == LEVEL_ARREATPLATEAU) {
        // DRLGOUTROOM_LinkLevelsByOffsetCoords - 使用偏移连接
        if (counter > 0 && act5Links[counter].levelLink >= 0) {
          int levelLink = act5Links[counter].levelLink;
          linkData.coords[counter].x = linkData.coords[levelLink].x + linkData.coords[levelLink].width;
          linkData.coords[counter].y = linkData.coords[levelLink].y;
          success = true;
        } else {
          linkData.coords[counter].x = level.OffsetX;
          linkData.coords[counter].y = level.OffsetY;
          success = true;
        }
      } else {
        // 默认逻辑
        if (act5Links[counter].levelLink >= 0) {
          int levelLink = act5Links[counter].levelLink;
          linkData.coords[counter].x = linkData.coords[levelLink].x + linkData.coords[levelLink].width;
          linkData.coords[counter].y = linkData.coords[levelLink].y;
          success = true;
        } else {
          linkData.coords[counter].x = level.OffsetX;
          linkData.coords[counter].y = level.OffsetY;
          success = true;
        }
      }

      // 检查重叠
      if (success && base.checkNotOverlapping(linkData, counter, linkData.links[counter].levelLink)) {
        counter++;
      } else {
        // 回溯
        linkData.rand[0][counter] = -1;
        linkData.rand[1][counter] = -1;
        linkData.rand[2][counter] = -1;
        linkData.rand[3][counter] = -1;
        counter--;
        if (counter < 0) {
          Gdx.app.error(TAG, "Failed to generate map after backtracking");
          return;
        }
      }
    }

    // 计算坐标偏移
    int townIndex = 0;
    int offsetX = -linkData.coords[townIndex].x;
    int offsetY = -linkData.coords[townIndex].y;
    
    // 创建区域
    BaseMapBuilderD2MOD base = new BaseMapBuilderD2MOD() {};
    base.factory = factory;
    base.socket = socket;
    
    for (int i = 0; i < act5Links.length && act5Links[i] != null; i++) {
      Levels.Entry level = Riiablo.files.Levels.get(act5Links[i].level);
      if (level == null) continue;
      
      LvlPrest.Entry preset = null;
      for (LvlPrest.Entry p : Riiablo.files.LvlPrest) {
        if (p.LevelId == act5Links[i].level) {
          preset = p;
          break;
        }
      }
      
      int finalX = linkData.coords[i].x + offsetX;
      int finalY = linkData.coords[i].y + offsetY;
      
      final int drlgType = level.DrlgType;
      final boolean isPresetLevel = drlgType == 2; // 2 == DRLGTYPE_PRESET

      if (preset != null && isPresetLevel) {
        int fileId[] = new int[6];
        int numFiles = Preset.getPresets(preset, fileId);
        if (numFiles > 0) {
          int selectIndex = MathUtils.random(numFiles - 1);
          int select = fileId[selectIndex];
          Zone zone = base.createZoneWithPreset(map, level, preset, select, finalX, finalY, 
              act5Links[i].level == LEVEL_HARROGATH);
          if (act5Links[i].level != LEVEL_HARROGATH) {
            zone.generator = base.createMonsterGenerator(socket);
          }
        }
      } else {
        Zone zone = base.createZoneWithGenerator(map, level, diff, finalX, finalY);
        if (act5Links[i].level != LEVEL_HARROGATH) {
          zone.generator = base.createMonsterGenerator(socket);
        }
      }
    }

    // 处理 TUNDRAWASTELANDS（独立区域，参考 DRLGOUTROOM_LinkLevelsByLevelDef）
    Levels.Entry tundraLevel = Riiablo.files.Levels.get(LEVEL_TUNDRAWASTELANDS);
    if (tundraLevel != null) {
      LvlPrest.Entry tundraPreset = null;
      for (LvlPrest.Entry p : Riiablo.files.LvlPrest) {
        if (p.LevelId == LEVEL_TUNDRAWASTELANDS) {
          tundraPreset = p;
          break;
        }
      }
      
      if (tundraPreset != null) {
        int fileId[] = new int[6];
        int numFiles = Preset.getPresets(tundraPreset, fileId);
        if (numFiles > 0) {
          int selectIndex = MathUtils.random(numFiles - 1);
          int select = fileId[selectIndex];
          base.createZoneWithPreset(map, tundraLevel, tundraPreset, select, 
              tundraLevel.OffsetX, tundraLevel.OffsetY, false);
        }
      } else {
        Zone zone = base.createZoneWithGenerator(map, tundraLevel, diff, 
            tundraLevel.OffsetX, tundraLevel.OffsetY);
        zone.generator = base.createMonsterGenerator(socket);
      }
    }

    // 添加高级功能：边界、路径、传送点、神殿等
    // 参考 D2MOD: DRLGOUTSIEGE_InitAct5OutdoorLevel
    for (Zone zone : map.zones) {
      if (!zone.town) {
        // 放置边界
        OutdoorFeatures.placeBorders(zone, seed, 4);
        
        // Act5 的特殊处理
        // 攻城区域可以放置神殿
        if (zone.level.Id == LEVEL_BLOODYFOOTHILLS || zone.level.Id == LEVEL_ID_ACT5_BARRICADE_1 ||
            zone.level.Id == LEVEL_ARREATPLATEAU || zone.level.Id == LEVEL_TUNDRAWASTELANDS) {
          OutdoorFeatures.placeShrines(zone, seed, 3);
        }
      }
    }
  }

}
