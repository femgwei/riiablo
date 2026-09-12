package com.riiablo.map;

import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlPrest;
import com.riiablo.engine.server.NativeDataTables;
import com.riiablo.engine.EntityFactory;
import com.d2moo.common.drlg.D2LevelIds;
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

  // D2MOD/1.10f Act V level ids. The previous literals (109..113) overlapped
  // Act IV and loaded the wrong Levels/LvlPrest records.
  static final int LEVEL_HARROGATH = D2LevelIds.LEVEL_HARROGATH;
  static final int LEVEL_BLOODYFOOTHILLS = D2LevelIds.LEVEL_BLOODYFOOTHILLS;
  /** Native barricade level used by D2MOO for Frigid Highlands. */
  static final int LEVEL_ID_ACT5_BARRICADE_1 = D2LevelIds.LEVEL_FRIGIDHIGHLANDS;
  static final int LEVEL_FRIGIDHIGHLANDS = D2LevelIds.LEVEL_FRIGIDHIGHLANDS;
  static final int LEVEL_ARREATPLATEAU = D2LevelIds.LEVEL_ARREATPLATEAU;
  static final int LEVEL_CRYSTALLINEPASSAGE = D2LevelIds.LEVEL_CRYSTALLINEPASSAGE;
  static final int LEVEL_FROZENTUNDRA = D2LevelIds.LEVEL_FROZENTUNDRA;
  static final int LEVEL_GLACIALTRAIL = D2LevelIds.LEVEL_GLACIALTRAIL;
  static final int LEVEL_FROZENRIVER = D2LevelIds.LEVEL_FROZENRIVER;
  static final int LEVEL_ANCIENTSWAY = D2LevelIds.LEVEL_ANCIENTSWAY;
  static final int LEVEL_ARREATSUMMIT = D2LevelIds.LEVEL_ARREATSUMMIT;

  /** Main Act V progression. Side caves are linked by their native LvlWarp rows. */
  static final int[] ACT5_MAIN_CHAIN = {
      LEVEL_HARROGATH,
      LEVEL_BLOODYFOOTHILLS,
      LEVEL_FRIGIDHIGHLANDS,
      LEVEL_ARREATPLATEAU,
      LEVEL_CRYSTALLINEPASSAGE,
      LEVEL_FROZENRIVER,
      LEVEL_GLACIALTRAIL,
      LEVEL_FROZENTUNDRA,
      LEVEL_ANCIENTSWAY,
      LEVEL_ARREATSUMMIT
  };

  static final int[][] ACT5_MAIN_LINKS = {
      {LEVEL_HARROGATH, LEVEL_BLOODYFOOTHILLS},
      {LEVEL_BLOODYFOOTHILLS, LEVEL_FRIGIDHIGHLANDS},
      {LEVEL_FRIGIDHIGHLANDS, LEVEL_ARREATPLATEAU},
      {LEVEL_ARREATPLATEAU, LEVEL_CRYSTALLINEPASSAGE},
      {LEVEL_CRYSTALLINEPASSAGE, LEVEL_FROZENRIVER},
      {LEVEL_FROZENRIVER, LEVEL_GLACIALTRAIL},
      {LEVEL_GLACIALTRAIL, LEVEL_FROZENTUNDRA},
      {LEVEL_FROZENTUNDRA, LEVEL_ANCIENTSWAY},
      {LEVEL_ANCIENTSWAY, LEVEL_ARREATSUMMIT}
  };

  @Wire(name = "factory")
  protected EntityFactory factory;

  @Wire(name = "client.socket", failOnNull = false)
  protected com.badlogic.gdx.net.Socket socket;

  @Override
  public void generate(Map map, int seed, int diff) {
    // 重要：设置随机种子，确保多人游戏中所有客户端生成相同的地图
    MathUtils.random.setSeed(seed);

    // D2MOD: gAct5OutdoorDrlgLink 数组
    BaseMapBuilderD2MOD.LevelLink[] act5Links = new BaseMapBuilderD2MOD.LevelLink[ACT5_MAIN_CHAIN.length];
    for (int i = 0; i < ACT5_MAIN_CHAIN.length; i++) {
      act5Links[i] = new BaseMapBuilderD2MOD.LevelLink(
          ACT5_MAIN_CHAIN[i], i == 0 ? -1 : i - 1, -1);
    }

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
      
      if (counter == 0) {
        // sub_6FD81330 - Harrogath is the anchor level.
        success = base.placeFirstLevel(linkData, counter, level);
      } else {
        // Place every outdoor level at the previous level's edge. This keeps
        // the generated chain connected even when a custom MPQ has placeholder
        // OffsetX/OffsetY values.
        int levelLink = act5Links[counter].levelLink;
        linkData.coords[counter].x = linkData.coords[levelLink].x
            + linkData.coords[levelLink].width;
        linkData.coords[counter].y = linkData.coords[levelLink].y;
        success = true;
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

    // 添加高级功能：边界、路径、传送点、神殿等
    // 参考 D2MOD: DRLGOUTSIEGE_InitAct5OutdoorLevel
    for (Zone zone : map.zones) {
      if (!zone.town) {
        // 放置边界
        OutdoorFeatures.placeBorders(zone, seed, 4);
        
        // Act5 的特殊处理
        // 攻城区域可以放置神殿
        if (zone.level.Id == LEVEL_BLOODYFOOTHILLS || zone.level.Id == LEVEL_ID_ACT5_BARRICADE_1 ||
            zone.level.Id == LEVEL_ARREATPLATEAU || zone.level.Id == LEVEL_FROZENTUNDRA) {
          OutdoorFeatures.placeShrines(zone, seed, 3);
        }
      }
    }
  }

}
