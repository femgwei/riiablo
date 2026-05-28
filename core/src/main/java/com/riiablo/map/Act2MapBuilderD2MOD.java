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
 * Act2 地图生成器 - 完全复刻 D2MOD 实现
 * 
 * 参考 D2MOD:
 * - source/D2Common/src/Drlg/DrlgOutPlace.cpp (gAct2OutdoorDrlgLink, gAct2CanyonDrlgLink)
 * - source/D2Common/src/Drlg/DrlgOutDesr.cpp (DRLGOUTDESR_InitAct2OutdoorLevel)
 */
public enum Act2MapBuilderD2MOD implements MapBuilder {
  INSTANCE;

  private static final String TAG = "Act2MapBuilderD2MOD";
  private static final boolean DEBUG = true;
  private static final boolean DEBUG_BUILD = DEBUG && true;

  // D2MOD: gAct2OutdoorDrlgLink 数组
  private static final int LEVEL_LUTGHOLEIN = 40;
  private static final int LEVEL_ROCKYWASTE = 41;
  private static final int LEVEL_DRYHILLS = 42;
  private static final int LEVEL_FAROASIS = 43;
  private static final int LEVEL_LOSTCITY = 44;
  private static final int LEVEL_VALLEYOFSNAKES = 45;
  private static final int LEVEL_CANYONOFTHEMAGI = 46;

  @Wire(name = "factory")
  protected EntityFactory factory;

  @Wire(name = "client.socket", failOnNull = false)
  protected com.badlogic.gdx.net.Socket socket;

  /**
   * D2MOD: sub_6FD81B30 - 处理 ROCKYWASTE 的放置
   */
  private boolean placeRockyWaste(BaseMapBuilderD2MOD.LevelLinkData linkData, int iteration) {
    if (linkData.rand[1][iteration] == -1) {
      linkData.rand[1][iteration] = MathUtils.random.nextInt() & 3; // 0-3
      linkData.rand[0][iteration] = linkData.rand[1][iteration];
    } else {
      int nextRand = (linkData.rand[0][iteration] + 1) % 4;
      if (nextRand == linkData.rand[1][iteration]) {
        return false; // 回溯
      }
      linkData.rand[0][iteration] = nextRand;
    }

    int levelLink = linkData.links[iteration].levelLink;
    BaseMapBuilderD2MOD base = new BaseMapBuilderD2MOD() {};
    base.calculateCoordOffset(linkData.coords[levelLink], linkData.coords[iteration], linkData.rand[0][iteration], 0);
    return true;
  }

  /**
   * D2MOD: sub_6FD81530 - 处理 DRYHILLS, FAROASIS, LOSTCITY 的放置
   */
  private boolean placeDesertArea(BaseMapBuilderD2MOD.LevelLinkData linkData, int iteration) {
    if (linkData.rand[1][iteration] == -1) {
      linkData.rand[1][iteration] = MathUtils.random.nextInt() & 3; // 0-3
      linkData.rand[0][iteration] = linkData.rand[1][iteration];
    } else {
      int nextRand = (linkData.rand[0][iteration] + 1) % 4;
      if (nextRand == linkData.rand[1][iteration]) {
        return false; // 回溯
      }
      linkData.rand[0][iteration] = nextRand;
    }

    int levelLink = linkData.links[iteration].levelLink;
    BaseMapBuilderD2MOD base = new BaseMapBuilderD2MOD() {};
    base.calculateCoordOffsetAlt(linkData.coords[levelLink], linkData.coords[iteration], linkData.rand[0][iteration], 1);
    return true;
  }

  /**
   * D2MOD: sub_6FD81BF0 - 处理 VALLEYOFSNAKES 的放置
   */
  private boolean placeValleyOfSnakes(BaseMapBuilderD2MOD.LevelLinkData linkData, int iteration) {
    if (linkData.rand[1][iteration] == -1) {
      linkData.rand[1][iteration] = MathUtils.random.nextInt() & 3; // 0-3
      linkData.rand[0][iteration] = linkData.rand[1][iteration];
    } else {
      int nextRand = (linkData.rand[0][iteration] + 1) % 4;
      if (nextRand == linkData.rand[1][iteration]) {
        return false; // 回溯
      }
      linkData.rand[0][iteration] = nextRand;
    }

    int levelLink = linkData.links[iteration].levelLink;
    BaseMapBuilderD2MOD base = new BaseMapBuilderD2MOD() {};
    base.calculateCoordOffset(linkData.coords[levelLink], linkData.coords[iteration], linkData.rand[0][iteration], 0);
    return true;
  }

  @Override
  public void generate(Map map, int seed, int diff) {
    // 重要：设置随机种子，确保多人游戏中所有客户端生成相同的地图
    MathUtils.random.setSeed(seed);

    // D2MOD: gAct2OutdoorDrlgLink 数组
    // { 函数指针, 区域ID, levelLink, levelLinkEx }
    BaseMapBuilderD2MOD.LevelLink[] act2Links = new BaseMapBuilderD2MOD.LevelLink[] {
      new BaseMapBuilderD2MOD.LevelLink(LEVEL_LUTGHOLEIN, -1, -1),      // 0: 城镇
      new BaseMapBuilderD2MOD.LevelLink(LEVEL_ROCKYWASTE, 0, -1),       // 1
      new BaseMapBuilderD2MOD.LevelLink(LEVEL_DRYHILLS, 1, -1),        // 2
      new BaseMapBuilderD2MOD.LevelLink(LEVEL_FAROASIS, 2, -1),        // 3
      new BaseMapBuilderD2MOD.LevelLink(LEVEL_LOSTCITY, 3, -1),        // 4
      new BaseMapBuilderD2MOD.LevelLink(LEVEL_VALLEYOFSNAKES, 4, -1),   // 5
      null // 结束标记
    };

    BaseMapBuilderD2MOD.LevelLinkData linkData = new BaseMapBuilderD2MOD.LevelLinkData();

    // 初始化区域尺寸（从 Levels.txt 读取）
    for (int i = 0; i < act2Links.length && act2Links[i] != null; i++) {
      linkData.links[i] = act2Links[i];
      Levels.Entry level = Riiablo.files.Levels.get(act2Links[i].level);
      if (level == null) {
        Gdx.app.error(TAG, "Level not found: " + act2Links[i].level);
        continue;
      }
      int sizeX = level.SizeX[diff];
      int sizeY = level.SizeY[diff];
      linkData.coords[i].width = sizeX * 5;  // tile to sub-tile
      linkData.coords[i].height = sizeY * 5;
    }

    // D2MOD: sub_6FD823C0 - 主要链接循环
    int counter = 0;
    while (counter < act2Links.length && act2Links[counter] != null) {
      boolean success = false;
      Levels.Entry level = Riiablo.files.Levels.get(act2Links[counter].level);
      if (level == null) {
        Gdx.app.error(TAG, "Level not found: " + act2Links[counter].level);
        break;
      }
      
      // 根据区域类型选择处理函数
      if (act2Links[counter].level == LEVEL_LUTGHOLEIN) {
        // sub_6FD81330 - 第一个区域（城镇），使用 Levels.txt 中的偏移
        BaseMapBuilderD2MOD base = new BaseMapBuilderD2MOD() {};
        success = base.placeFirstLevel(linkData, counter, level);
      } else if (act2Links[counter].level == LEVEL_ROCKYWASTE) {
        // sub_6FD81B30 - ROCKYWASTE
        success = placeRockyWaste(linkData, counter);
      } else if (act2Links[counter].level == LEVEL_DRYHILLS || 
                 act2Links[counter].level == LEVEL_FAROASIS ||
                 act2Links[counter].level == LEVEL_LOSTCITY) {
        // sub_6FD81530 - DRYHILLS, FAROASIS, LOSTCITY
        success = placeDesertArea(linkData, counter);
      } else if (act2Links[counter].level == LEVEL_VALLEYOFSNAKES) {
        // sub_6FD81BF0 - VALLEYOFSNAKES
        success = placeValleyOfSnakes(linkData, counter);
      } else {
        // 未知区域类型，使用默认逻辑
        if (act2Links[counter].levelLink >= 0) {
          int levelLink = act2Links[counter].levelLink;
          linkData.coords[counter].x = linkData.coords[levelLink].x + linkData.coords[levelLink].width;
          linkData.coords[counter].y = linkData.coords[levelLink].y;
          success = true;
        } else {
          linkData.coords[counter].x = level.OffsetX;
          linkData.coords[counter].y = level.OffsetY;
          success = true;
        }
      }

      // 检查重叠（参考 DRLGOUTPLACE_LinkAct2Outdoors）
      BaseMapBuilderD2MOD base = new BaseMapBuilderD2MOD() {};
      if (success && base.checkNotOverlapping(linkData, counter, linkData.links[counter].levelLink)) {
        counter++;
      } else {
        // 回溯：重置随机数
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

    // 找到城镇（LUTGHOLEIN）的索引，用于计算坐标偏移
    int townIndex = -1;
    for (int i = 0; i < act2Links.length && act2Links[i] != null; i++) {
      if (act2Links[i].level == LEVEL_LUTGHOLEIN) {
        townIndex = i;
        break;
      }
    }
    
    // 计算坐标偏移，使城镇在 (0, 0) 附近
    int offsetX = 0;
    int offsetY = 0;
    if (townIndex >= 0) {
      offsetX = -linkData.coords[townIndex].x;
      offsetY = -linkData.coords[townIndex].y;
    } else {
      offsetX = -linkData.coords[0].x;
      offsetY = -linkData.coords[0].y;
    }
    
    // 创建区域：先创建城镇
    Zone townZone = null;
    for (int i = 0; i < act2Links.length && act2Links[i] != null; i++) {
      if (act2Links[i].level == LEVEL_LUTGHOLEIN) {
        Levels.Entry level = Riiablo.files.Levels.get(act2Links[i].level);
        final int drlgType = level.DrlgType;
        final boolean isPresetLevel = drlgType == 2; // 2 == DRLGTYPE_PRESET

        // 查找与 level 对应的 preset（仅当 DrlgType 为 PRESET 时才整图使用）
        LvlPrest.Entry preset = null;
        if (isPresetLevel) {
          for (LvlPrest.Entry p : Riiablo.files.LvlPrest) {
            if (p.LevelId == act2Links[i].level) {
              preset = p;
              break;
            }
          }
        }

        BaseMapBuilderD2MOD base = new BaseMapBuilderD2MOD() {};
        base.factory = factory;
        base.socket = socket;

        int finalX = linkData.coords[i].x + offsetX;
        int finalY = linkData.coords[i].y + offsetY;

        if (!isPresetLevel || preset == null) {
          // 若不是 PRESET 关卡，则退回随机生成，保持与 D2MOO 一致
          townZone = base.createZoneWithGenerator(map, level, diff, finalX, finalY);
        } else {
          int fileId[] = new int[6];
          int numFiles = Preset.getPresets(preset, fileId);
          if (numFiles == 0) {
            Gdx.app.error(TAG, "No valid presets found for level " + level.LevelName);
            break;
          }
          int selectIndex = MathUtils.random(numFiles - 1);
          int select = fileId[selectIndex];

          if (select < 0 || select >= preset.File.length || 
              preset.File[select] == null || 
              preset.File[select].isEmpty() || 
              preset.File[select].charAt(0) == '0') {
            Gdx.app.error(TAG, "Invalid file index " + select + " for level " + level.LevelName);
            break;
          }

          townZone = base.createZoneWithPreset(map, level, preset, select, finalX, finalY, true);
        }
        break;
      }
    }
    
    // 创建其他区域
    for (int i = 0; i < act2Links.length && act2Links[i] != null; i++) {
      if (act2Links[i].level == LEVEL_LUTGHOLEIN) {
        continue; // 跳过城镇
      }
      
      Levels.Entry level = Riiablo.files.Levels.get(act2Links[i].level);
      if (level == null) {
        Gdx.app.error(TAG, "Level not found: " + act2Links[i].level);
        continue;
      }
      
      // 查找与 level 对应的 preset
      LvlPrest.Entry preset = null;
      for (LvlPrest.Entry p : Riiablo.files.LvlPrest) {
        if (p.LevelId == act2Links[i].level) {
          preset = p;
          break;
        }
      }
      
      Zone zone;
      BaseMapBuilderD2MOD base = new BaseMapBuilderD2MOD() {};
      base.factory = factory;
      base.socket = socket;
      
      int finalX = linkData.coords[i].x + offsetX;
      int finalY = linkData.coords[i].y + offsetY;
      
      final int drlgType = level.DrlgType;
      final boolean isPresetLevel = drlgType == 2; // 2 == DRLGTYPE_PRESET

      if (preset != null && isPresetLevel) {
        int fileId[] = new int[6];
        int numFiles = Preset.getPresets(preset, fileId);
        if (numFiles == 0) {
          Gdx.app.error(TAG, "No valid presets found for level " + level.LevelName);
          continue;
        }
        int selectIndex = MathUtils.random(numFiles - 1);
        int select = fileId[selectIndex];
        
        if (select < 0 || select >= preset.File.length || 
            preset.File[select] == null || 
            preset.File[select].isEmpty() || 
            preset.File[select].charAt(0) == '0') {
          Gdx.app.error(TAG, "Invalid file index " + select + " for level " + level.LevelName);
          continue;
        }
        
        zone = base.createZoneWithPreset(map, level, preset, select, finalX, finalY, false);
      } else {
        zone = base.createZoneWithGenerator(map, level, diff, finalX, finalY);
      }
      
      // 设置怪物生成器
      zone.generator = base.createMonsterGenerator(socket);
    }
    
    // 处理 CANYONOFTHEMAGI（独立区域）
    Levels.Entry canyonLevel = Riiablo.files.Levels.get(LEVEL_CANYONOFTHEMAGI);
    if (canyonLevel != null) {
      LvlPrest.Entry canyonPreset = null;
      for (LvlPrest.Entry p : Riiablo.files.LvlPrest) {
        if (p.LevelId == LEVEL_CANYONOFTHEMAGI) {
          canyonPreset = p;
          break;
        }
      }
      
      if (canyonPreset != null) {
        int fileId[] = new int[6];
        int numFiles = Preset.getPresets(canyonPreset, fileId);
        if (numFiles > 0) {
          int selectIndex = MathUtils.random(numFiles - 1);
          int select = fileId[selectIndex];
          BaseMapBuilderD2MOD base = new BaseMapBuilderD2MOD() {};
          base.factory = factory;
          base.socket = socket;
          base.createZoneWithPreset(map, canyonLevel, canyonPreset, select, 
              canyonLevel.OffsetX, canyonLevel.OffsetY, false);
        }
      }
    }

    // 设置城镇和野外区域的入口连接
    if (townZone != null) {
      // TODO: 设置 warp 连接（参考 Act1MapBuilderD2MOD）
    }

    // 添加高级功能：边界、路径、传送点、神殿等
    // 参考 D2MOD: DRLGOUTDESR_InitAct2OutdoorLevel
    for (Zone zone : map.zones) {
      if (!zone.town) {
        // 放置边界
        OutdoorFeatures.placeBorders(zone, seed, 1);
        
        // 放置传送点（特定区域）
        if (zone.level.Id == LEVEL_DRYHILLS || zone.level.Id == LEVEL_FAROASIS || 
            zone.level.Id == LEVEL_LOSTCITY) {
          OutdoorFeatures.placeWaypoint(zone, seed);
        }
        
        // 放置神殿（5个）
        if (zone.level.Id == LEVEL_ROCKYWASTE || zone.level.Id == LEVEL_DRYHILLS ||
            zone.level.Id == LEVEL_FAROASIS || zone.level.Id == LEVEL_LOSTCITY ||
            zone.level.Id == LEVEL_CANYONOFTHEMAGI) {
          OutdoorFeatures.placeShrines(zone, seed, 5);
        }
      }
    }
  }

}
