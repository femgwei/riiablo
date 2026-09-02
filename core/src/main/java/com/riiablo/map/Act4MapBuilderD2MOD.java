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
 * Act4 地图生成器 - 完全复刻 D2MOD 实现
 * 
 * 参考 D2MOD:
 * - source/D2Common/src/Drlg/DrlgOutPlace.cpp (gAct4OutdoorDrlgLink, gAct4ChaosSanctumDrlgLink)
 */
public enum Act4MapBuilderD2MOD implements MapBuilder {
  INSTANCE;

  private static final String TAG = "Act4MapBuilderD2MOD";
  private static final boolean DEBUG = true;
  private static final boolean DEBUG_BUILD = DEBUG && false;

  // D2MOD: gAct4OutdoorDrlgLink 数组
  private static final int LEVEL_THEPANDEMONIUMFORTRESS = 103;
  private static final int LEVEL_OUTERSTEPPES = 104;
  private static final int LEVEL_PLAINSOFDESPAIR = 105;
  private static final int LEVEL_CITYOFTHEDAMNED = 106;
  private static final int LEVEL_CHAOSSANCTUM = 107;

  @Wire(name = "factory")
  protected EntityFactory factory;

  @Wire(name = "client.socket", failOnNull = false)
  protected com.badlogic.gdx.net.Socket socket;

  /**
   * D2MOD: sub_6FD81CA0 - 处理 OUTERSTEPPES 的放置
   */
  private boolean placeOuterSteppes(BaseMapBuilderD2MOD.LevelLinkData linkData, int iteration) {
    linkData.rand[1][iteration] = 3; // DIR_EAST
    linkData.rand[0][iteration] = linkData.rand[1][iteration];

    int levelLink = linkData.links[iteration].levelLink;
    BaseMapBuilderD2MOD base = new BaseMapBuilderD2MOD() {};
    
    // 随机选择使用哪个函数计算偏移
    if ((MathUtils.random.nextInt() & 1) == 0) {
      base.calculateCoordOffset(linkData.coords[levelLink], linkData.coords[iteration], 
          linkData.rand[0][iteration], 3);
    } else {
      base.calculateCoordOffsetAlt(linkData.coords[levelLink], linkData.coords[iteration], 
          linkData.rand[0][iteration], 3);
    }
    
    return true;
  }

  @Override
  public void generate(Map map, int seed, int diff) {
    // 重要：设置随机种子，确保多人游戏中所有客户端生成相同的地图
    MathUtils.random.setSeed(seed);

    // D2MOD: gAct4OutdoorDrlgLink 数组
    BaseMapBuilderD2MOD.LevelLink[] act4Links = new BaseMapBuilderD2MOD.LevelLink[] {
      new BaseMapBuilderD2MOD.LevelLink(LEVEL_THEPANDEMONIUMFORTRESS, -1, -1), // 0: 城镇
      new BaseMapBuilderD2MOD.LevelLink(LEVEL_OUTERSTEPPES, 0, -1),            // 1
      new BaseMapBuilderD2MOD.LevelLink(LEVEL_PLAINSOFDESPAIR, 1, -1),        // 2
      new BaseMapBuilderD2MOD.LevelLink(LEVEL_CITYOFTHEDAMNED, 2, -1),        // 3
      null // 结束标记
    };

    BaseMapBuilderD2MOD.LevelLinkData linkData = new BaseMapBuilderD2MOD.LevelLinkData();

    // 初始化区域尺寸
    for (int i = 0; i < act4Links.length && act4Links[i] != null; i++) {
      linkData.links[i] = act4Links[i];
      Levels.Entry level = Riiablo.files.Levels.get(act4Links[i].level);
      if (level == null) {
        Gdx.app.error(TAG, "Level not found: " + act4Links[i].level);
        continue;
      }
      int sizeX = NativeDataTables.levelSizeX(level, diff, 1);
      int sizeY = NativeDataTables.levelSizeY(level, diff, 1);
      linkData.coords[i].width = sizeX * 5;
      linkData.coords[i].height = sizeY * 5;
    }

    // D2MOD: sub_6FD823C0 - 主要链接循环
    int counter = 0;
    while (counter < act4Links.length && act4Links[counter] != null) {
      boolean success = false;
      Levels.Entry level = Riiablo.files.Levels.get(act4Links[counter].level);
      if (level == null) {
        Gdx.app.error(TAG, "Level not found: " + act4Links[counter].level);
        break;
      }
      
      BaseMapBuilderD2MOD base = new BaseMapBuilderD2MOD() {};
      
      if (act4Links[counter].level == LEVEL_THEPANDEMONIUMFORTRESS) {
        // sub_6FD81330 - 第一个区域（城镇）
        success = base.placeFirstLevel(linkData, counter, level);
      } else if (act4Links[counter].level == LEVEL_OUTERSTEPPES) {
        // sub_6FD81CA0 - OUTERSTEPPES
        success = placeOuterSteppes(linkData, counter);
      } else if (act4Links[counter].level == LEVEL_PLAINSOFDESPAIR || 
                 act4Links[counter].level == LEVEL_CITYOFTHEDAMNED) {
        // sub_6FD81380 - PLAINSOFDESPAIR, CITYOFTHEDAMNED
        if (linkData.rand[1][counter] == -1) {
          linkData.rand[1][counter] = MathUtils.random.nextInt() & 3;
          linkData.rand[0][counter] = linkData.rand[1][counter];
        } else {
          int nextRand = (linkData.rand[0][counter] + 1) % 4;
          if (nextRand == linkData.rand[1][counter]) {
            success = false;
          } else {
            linkData.rand[0][counter] = nextRand;
            int levelLink = linkData.links[counter].levelLink;
            base.calculateCoordOffsetAlt(linkData.coords[levelLink], linkData.coords[counter], 
                linkData.rand[0][counter], 1);
            success = true;
          }
        }
      } else {
        // 默认逻辑
        if (act4Links[counter].levelLink >= 0) {
          int levelLink = act4Links[counter].levelLink;
          linkData.coords[counter].x = linkData.coords[levelLink].x + linkData.coords[levelLink].width;
          linkData.coords[counter].y = linkData.coords[levelLink].y;
          success = true;
        } else {
          linkData.coords[counter].x = level.OffsetX;
          linkData.coords[counter].y = level.OffsetY;
          success = true;
        }
      }

      // 检查重叠（参考 DRLGOUTPLACE_LinkAct4Outdoors）
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
    
    for (int i = 0; i < act4Links.length && act4Links[i] != null; i++) {
      Levels.Entry level = Riiablo.files.Levels.get(act4Links[i].level);
      if (level == null) continue;
      
      LvlPrest.Entry preset = null;
      for (LvlPrest.Entry p : Riiablo.files.LvlPrest) {
        if (p.LevelId == act4Links[i].level) {
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
              act4Links[i].level == LEVEL_THEPANDEMONIUMFORTRESS);
          if (act4Links[i].level != LEVEL_THEPANDEMONIUMFORTRESS) {
            zone.generator = base.createMonsterGenerator(socket);
          }
        }
      } else {
        Zone zone = base.createZoneWithGenerator(map, level, diff, finalX, finalY);
        if (act4Links[i].level != LEVEL_THEPANDEMONIUMFORTRESS) {
          zone.generator = base.createMonsterGenerator(socket);
        }
      }
    }

    // 处理 CHAOSSANCTUM（独立区域）
    Levels.Entry chaosLevel = Riiablo.files.Levels.get(LEVEL_CHAOSSANCTUM);
    if (chaosLevel != null) {
      LvlPrest.Entry chaosPreset = null;
      for (LvlPrest.Entry p : Riiablo.files.LvlPrest) {
        if (p.LevelId == LEVEL_CHAOSSANCTUM) {
          chaosPreset = p;
          break;
        }
      }
      
      if (chaosPreset != null) {
        int fileId[] = new int[6];
        int numFiles = Preset.getPresets(chaosPreset, fileId);
        if (numFiles > 0) {
          int selectIndex = MathUtils.random(numFiles - 1);
          int select = fileId[selectIndex];
          base.createZoneWithPreset(map, chaosLevel, chaosPreset, select, 
              chaosLevel.OffsetX, chaosLevel.OffsetY, false);
        }
      }
    }

    // 添加高级功能：边界、路径、传送点、神殿等
    // 参考 D2MOD: DRLGOUTPLACE_InitAct4OutdoorLevel
    for (Zone zone : map.zones) {
      if (!zone.town) {
        // 放置边界
        OutdoorFeatures.placeBorders(zone, seed, 3);
        
        // Act4 的特殊处理
        // 地狱区域可以放置神殿
        if (zone.level.Id == LEVEL_OUTERSTEPPES || zone.level.Id == LEVEL_PLAINSOFDESPAIR ||
            zone.level.Id == LEVEL_CITYOFTHEDAMNED) {
          OutdoorFeatures.placeShrines(zone, seed, 3);
        }
      }
    }
  }

}
