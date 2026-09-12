package com.riiablo.map;

import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntMap;
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
  /** Current D2MOO_JAVA aliases for the Worldstone Keep/Throne tail. */
  static final int LEVEL_WORLDSTONEKEEPLEV1 = D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV1;
  static final int LEVEL_THRONEOFDESTRUCTION = D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV2;
  static final int LEVEL_WORLDSTONECHAMBER = D2LevelIds.LEVEL_WORLDSTONECHAMBER;

  /** Native A5Q4 temple branch entered through Drehya's town portal. */
  static final int[] ACT5_NIHLATHAK_CHAIN = {
      D2LevelIds.LEVEL_NIHLATHAKSTEMPLE,
      D2LevelIds.LEVEL_HALLSOFANGUISH,
      D2LevelIds.LEVEL_HALLSOFPAIN,
      D2LevelIds.LEVEL_HALLSOFVAUGHT
  };

  static final int[][] ACT5_NIHLATHAK_LINKS = {
      {D2LevelIds.LEVEL_NIHLATHAKSTEMPLE, D2LevelIds.LEVEL_HALLSOFANGUISH},
      {D2LevelIds.LEVEL_HALLSOFANGUISH, D2LevelIds.LEVEL_HALLSOFPAIN},
      {D2LevelIds.LEVEL_HALLSOFPAIN, D2LevelIds.LEVEL_HALLSOFVAUGHT}
  };

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
      LEVEL_ARREATSUMMIT,
      LEVEL_WORLDSTONEKEEPLEV1,
      LEVEL_THRONEOFDESTRUCTION,
      LEVEL_WORLDSTONECHAMBER
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
      {LEVEL_ANCIENTSWAY, LEVEL_ARREATSUMMIT},
      {LEVEL_ARREATSUMMIT, LEVEL_WORLDSTONEKEEPLEV1},
      {LEVEL_WORLDSTONEKEEPLEV1, LEVEL_THRONEOFDESTRUCTION},
      {LEVEL_THRONEOFDESTRUCTION, LEVEL_WORLDSTONECHAMBER}
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

    // A5Q4 is a separate temple branch. It is not part of the outdoor
    // Harrogath-to-Summit chain and must not inherit a fake Summit edge.
    createNihlathakQuestZones(map, diff, base);
    
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
      if (!zone.town && isOutdoorFeatureLevel(zone.level.Id)) {
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

  private void createNihlathakQuestZones(Map map, int diff, BaseMapBuilderD2MOD base) {
    if (map == null || base == null || Riiablo.files == null || Riiablo.files.Levels == null) return;
    int cursor = 0;
    for (Zone existing : map.zones) {
      if (existing != null) cursor = Math.max(cursor, existing.x() + existing.width());
    }
    cursor += 32;
    for (int levelId : ACT5_NIHLATHAK_CHAIN) {
      Levels.Entry level = Riiablo.files.Levels.get(levelId);
      if (level == null) {
        Gdx.app.error(TAG, "A5Q4 level missing: " + levelId);
        continue;
      }
      Zone zone = base.createZoneWithGenerator(map, level, diff, cursor, 0);
      zone.generator = base.createMonsterGenerator(socket);
      cursor += zone.width() + 32;
      Gdx.app.debug(TAG, "A5Q4 quest zone placed: level=" + levelId
          + " x=" + zone.x() + " width=" + zone.width());
    }
  }

  private static boolean isOutdoorFeatureLevel(int levelId) {
    return levelId == LEVEL_BLOODYFOOTHILLS
        || levelId == LEVEL_ID_ACT5_BARRICADE_1
        || levelId == LEVEL_ARREATPLATEAU
        || levelId == LEVEL_FROZENTUNDRA;
  }

  /**
   * Replays the D2Common runtime first-empty-slot insertion for the Act V
   * progression links.  Levels.txt contains static Vis/Warp rows, but the
   * outdoor chain is also installed at runtime; keeping the overrides local
   * to this Map avoids mutating shared table data.
   */
  void configureAct5OutdoorWarps(Map map) {
    configureAct5WarpLinks(map, ACT5_MAIN_LINKS, "outdoor");
  }

  /** Configures the independent A5Q4 temple branch without connecting it to
   * the outdoor progression chain. */
  void configureAct5QuestWarps(Map map) {
    configureAct5WarpLinks(map, ACT5_NIHLATHAK_LINKS, "A5Q4 temple");
  }

  private void configureAct5WarpLinks(Map map, int[][] links, String label) {
    if (map == null || Riiablo.files == null || Riiablo.files.Levels == null) return;
    IntMap<RuntimeWarpState> states = new IntMap<>();
    for (int[] link : links) {
      for (int levelId : link) {
        if (states.containsKey(levelId)) continue;
        Levels.Entry level = Riiablo.files.Levels.get(levelId);
        if (level == null) {
          Gdx.app.error(TAG, "Act5 warp level missing: " + levelId);
          continue;
        }
        states.put(levelId, new RuntimeWarpState(level.Vis, level.Warp));
      }
    }
    int configured = 0;
    for (int[] link : links) {
      RuntimeWarpState source = states.get(link[0]);
      RuntimeWarpState destination = states.get(link[1]);
      if (source == null || destination == null) continue;
      int sourceSlot = source.ensureDestination(link[1]);
      int destinationSlot = destination.ensureDestination(link[0]);
      if (sourceSlot < 0 || destinationSlot < 0) {
        Gdx.app.error(TAG, String.format(
            "Act5 %s warp slot exhausted: %d->%d sourceSlot=%d destinationSlot=%d",
            label,
            link[0], link[1], sourceSlot, destinationSlot));
        continue;
      }
      map.addWarpDestinationOverride(link[0], sourceSlot, link[1]);
      map.addWarpDestinationOverride(link[1], destinationSlot, link[0]);
      ensureWarpMarker(map, link[0], sourceSlot);
      ensureWarpMarker(map, link[1], destinationSlot);
      configured++;
    }
    linkNativeWarpSpecials(map);
    Gdx.app.log(TAG, String.format("Act5 %s native warp table configured: links=%d/%d",
        label, configured, links.length));
  }

  /** Pair exported/synthetic warp cells with the reverse cell expected by WarpInteractor. */
  void linkNativeWarpSpecials(Map map) {
    if (map == null) return;
    int linked = 0;
    int missing = 0;
    for (Zone source : map.zones) {
      if (source == null || source.level == null || source.specials == null) continue;
      for (IntMap.Entry<DS1.Cell> entry : source.specials.entries()) {
        DS1.Cell sourceCell = entry.value;
        if (sourceCell == null || !Map.ID.WARPS.contains(sourceCell.id)) continue;
        int target = map.getWarpDestinationOverride(source.level.Id, sourceCell.mainIndex);
        if (target <= 0 && source.level.Vis != null
            && sourceCell.mainIndex >= 0 && sourceCell.mainIndex < source.level.Vis.length) {
          target = source.level.Vis[sourceCell.mainIndex];
        }
        Zone destination = findZone(map, target);
        if (destination == null || destination.specials == null) {
          missing++;
          continue;
        }
        DS1.Cell reverse = findReverseWarp(map, destination, source.level.Id);
        if (reverse == null) {
          missing++;
          continue;
        }
        source.setWarp(sourceCell.id, reverse.id);
        linked++;
      }
    }
    Gdx.app.log(TAG, String.format(
        "Act5 native warp special summary: linked=%d missingReverse=%d", linked, missing));
  }

  private static Zone findZone(Map map, int levelId) {
    if (map == null || levelId <= 0) return null;
    for (Zone zone : map.zones) {
      if (zone != null && zone.level != null && zone.level.Id == levelId) return zone;
    }
    return null;
  }

  private static DS1.Cell findReverseWarp(
      Map map, Zone destination, int sourceLevelId) {
    if (destination == null || destination.specials == null) return null;
    for (IntMap.Entry<DS1.Cell> entry : destination.specials.entries()) {
      DS1.Cell cell = entry.value;
      if (cell == null || !Map.ID.WARPS.contains(cell.id)) continue;
      int target = map.getWarpDestinationOverride(destination.level.Id, cell.mainIndex);
      if (target <= 0 && destination.level.Vis != null
          && cell.mainIndex >= 0 && cell.mainIndex < destination.level.Vis.length) {
        target = destination.level.Vis[cell.mainIndex];
      }
      if (target == sourceLevelId) return cell;
    }
    return null;
  }

  private static void ensureWarpMarker(Map map, int levelId, int mainIndex) {
    Zone zone = findZone(map, levelId);
    if (zone == null || zone.level == null || mainIndex < 0 || mainIndex >= 8) return;
    if (zone.specials != null) {
      for (IntMap.Entry<DS1.Cell> entry : zone.specials.entries()) {
        DS1.Cell cell = entry.value;
        if (cell != null && Map.ID.WARPS.contains(cell.id) && cell.mainIndex == mainIndex) return;
      }
    }
    int tx = Math.max(0, zone.tilesX / 2 + (mainIndex & 1) * 2 - 1);
    int ty = Math.max(0, zone.tilesY / 2 + (mainIndex / 2) * 2 - 1);
    zone.addNativeWarpMarker(mainIndex,
        tx * DT1.Tile.SUBTILE_SIZE,
        ty * DT1.Tile.SUBTILE_SIZE);
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
      for (int i = 0; i < vis.length; i++) {
        if (vis[i] == destinationLevelId) return i;
      }
      for (int i = 0; i < vis.length; i++) {
        if (vis[i] == 0 && warp[i] == -1) {
          vis[i] = destinationLevelId;
          return i;
        }
      }
      return -1;
    }
  }

}
