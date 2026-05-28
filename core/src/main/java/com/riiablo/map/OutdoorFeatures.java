package com.riiablo.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.LvlPrest;
import com.riiablo.codec.excel.LvlSub;
import com.riiablo.map.Map.Preset;
import com.riiablo.map.Map.Zone;

/**
 * 室外区域高级功能工具类
 * 
 * 参考 D2MOD:
 * - source/D2Common/src/Drlg/DrlgOutdoors.cpp (Waypoints, Shrines)
 * - source/D2Common/src/Drlg/DrlgOutPlace.cpp (Borders)
 * - source/D2Common/src/Drlg/DrlgOutWild.cpp (Rivers, Paths, Caves)
 */
public class OutdoorFeatures {
  private static final String TAG = "OutdoorFeatures";
  private static final boolean DEBUG = true;

  /**
   * 在区域中放置传送点（Waypoint）
   * 参考 D2MOD: DRLGOUTDOORS_SpawnAct12Waypoint
   * 
   * @param zone 目标区域
   * @param seed 随机种子（确保多人游戏一致性）
   */
  public static void placeWaypoint(Zone zone, int seed) {
    if (zone == null || zone.town) {
      return; // 不在城镇中放置传送点
    }

    // 使用区域特定的种子，确保相同区域总是生成相同位置
    long zoneSeed = (long) seed * 31 + zone.level.Id;
    MathUtils.random.setSeed((int) zoneSeed);

    // 在区域网格中随机选择一个位置（避开边界）
    int gridWidth = zone.gridsX;
    int gridHeight = zone.gridsY;
    
    if (gridWidth < 3 || gridHeight < 3) {
      if (DEBUG) {
        Gdx.app.debug(TAG, "Zone too small for waypoint: " + zone.level.LevelName);
      }
      return;
    }

    // 尝试多个随机位置，找到第一个有效位置
    int maxAttempts = (gridWidth - 2) * (gridHeight - 2);
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      int gridX = 1 + MathUtils.random(gridWidth - 3);
      int gridY = 1 + MathUtils.random(gridHeight - 3);

      // 检查该位置是否有效（没有预设或可以覆盖）
      if (zone.presets[gridX][gridY] == null || canPlaceFeature(zone, gridX, gridY)) {
        // 查找传送点预设（LvlPrest 中 LevelId 匹配且包含 waypoint）
        LvlPrest.Entry waypointPreset = findWaypointPreset(zone.level.Id);
        if (waypointPreset != null) {
          int fileId[] = new int[6];
          int numFiles = Preset.getPresets(waypointPreset, fileId);
          if (numFiles > 0) {
            int selectIndex = MathUtils.random(numFiles - 1);
            int select = fileId[selectIndex];
            zone.presets[gridX][gridY] = Preset.of(waypointPreset, select);
            
            if (DEBUG) {
              Gdx.app.debug(TAG, String.format("Placed waypoint in %s at grid (%d, %d)", 
                  zone.level.LevelName, gridX, gridY));
            }
            return;
          }
        }
      }
    }

    if (DEBUG) {
      Gdx.app.debug(TAG, "Failed to place waypoint in: " + zone.level.LevelName);
    }
  }

  /**
   * 在区域中放置神殿（Shrines）
   * 参考 D2MOD: DRLGOUTDOORS_SpawnAct12Shrines
   * 
   * @param zone 目标区域
   * @param seed 随机种子
   * @param count 神殿数量
   */
  public static void placeShrines(Zone zone, int seed, int count) {
    if (zone == null || zone.town || count <= 0) {
      return;
    }

    long zoneSeed = (long) seed * 31 + zone.level.Id;
    MathUtils.random.setSeed((int) zoneSeed);

    int gridWidth = zone.gridsX;
    int gridHeight = zone.gridsY;
    
    if (gridWidth < 3 || gridHeight < 3) {
      return;
    }

    // 神殿类型（0-3）
    int shrineType = MathUtils.random.nextInt() & 3;
    int placed = 0;
    int maxAttempts = (gridWidth - 2) * (gridHeight - 2);

    for (int attempt = 0; attempt < maxAttempts && placed < count; attempt++) {
      int gridX = 1 + MathUtils.random(gridWidth - 3);
      int gridY = 1 + MathUtils.random(gridHeight - 3);

      if (zone.presets[gridX][gridY] == null || canPlaceFeature(zone, gridX, gridY)) {
        LvlPrest.Entry shrinePreset = findShrinePreset(zone.level.Id, shrineType);
        if (shrinePreset != null) {
          int fileId[] = new int[6];
          int numFiles = Preset.getPresets(shrinePreset, fileId);
          if (numFiles > 0) {
            int selectIndex = MathUtils.random(numFiles - 1);
            int select = fileId[selectIndex];
            zone.presets[gridX][gridY] = Preset.of(shrinePreset, select);
            placed++;
            shrineType = (shrineType + 1) % 4; // 循环使用不同类型
          }
        }
      }
    }

    if (DEBUG && placed > 0) {
      Gdx.app.debug(TAG, String.format("Placed %d shrines in %s", placed, zone.level.LevelName));
    }
  }

  /**
   * 在区域边界放置边界预设
   * 参考 D2MOD: DRLGOUTPLACE_PlaceAct1245OutdoorBorders
   *
   * Act1 由 placeBordersAct1 单独调用；Act2-5 在此统一走 LvlSub 驱动逻辑。
   * 当 Level 有有效 SubType 且 LvlSub 中存在 BordType>=0 的条目时，使用 LvlSub 选择边界块。
   *
   * @param zone 目标区域
   * @param seed 随机种子
   * @param act Act 编号（0-4）
   */
  public static void placeBorders(Zone zone, int seed, int act) {
    if (zone == null || zone.town) {
      return;
    }
    // 优先使用 LvlSub 驱动（Act2-5 与 Act1 共享同一套逻辑）
    placeBordersFromLvlSub(zone, seed);
  }

  /**
   * Act1 专用入口：使用 LvlSub / LvlPrest 选择边界预制体。
   * 仅对 Act1 户外关卡生效，内部委托给 placeBordersFromLvlSub。
   */
  public static void placeBordersAct1(Zone zone, int seed) {
    if (zone == null || zone.town || zone.level.Act != 0) {
      return;
    }
    placeBordersFromLvlSub(zone, seed);
  }

  /**
   * 使用 LvlSub / LvlPrest 选择边界预制体（Act1-5 通用）。
   * 参考 D2MOD: DRLGOUTPLACE_PlaceAct1245OutdoorBorders
   *
   * 核心思路：
   * - 根据当前 Level 的 SubType 读取 LvlSub 分组；
   * - 过滤出 BordType >= 0 的记录，视为边界 ds1；
   * - 在四条边上随机挑选这些 ds1，并映射到对应的 LvlPrest 记录，再放入 zone.presets。
   */
  public static void placeBordersFromLvlSub(Zone zone, int seed) {
    if (zone == null || zone.town) {
      return;
    }
    if (Riiablo.files == null || Riiablo.files.LvlSub == null) {
      return;
    }

    long zoneSeed = (long) seed * 31 + zone.level.Id;
    MathUtils.random.setSeed((int) zoneSeed);

    final int subType = zone.level.SubType;
    if (subType < 0) {
      return;
    }

    LvlSub.Entry[] subs = Riiablo.files.LvlSub.getByType(subType);
    if (subs.length == 0) {
      return;
    }

    java.util.ArrayList<LvlSub.Entry> borderSubs = new java.util.ArrayList<>();
    for (LvlSub.Entry e : subs) {
      if (e.BordType >= 0 && e.File != null && !e.File.isEmpty()) {
        borderSubs.add(e);
      }
    }
    if (borderSubs.isEmpty()) {
      return;
    }

    int gridWidth = zone.gridsX;
    int gridHeight = zone.gridsY;

    for (int x = 0; x < gridWidth; x++) {
      if (zone.presets[x][0] == null) {
        zone.presets[x][0] = pickBorderPresetFromLvlSub(borderSubs, zone);
      }
      if (zone.presets[x][gridHeight - 1] == null) {
        zone.presets[x][gridHeight - 1] = pickBorderPresetFromLvlSub(borderSubs, zone);
      }
    }

    for (int y = 0; y < gridHeight; y++) {
      if (zone.presets[0][y] == null) {
        zone.presets[0][y] = pickBorderPresetFromLvlSub(borderSubs, zone);
      }
      if (zone.presets[gridWidth - 1][y] == null) {
        zone.presets[gridWidth - 1][y] = pickBorderPresetFromLvlSub(borderSubs, zone);
      }
    }
  }

  /**
   * Act1 专用：使用 LvlSub / LvlPrest 在野外区域内铺设细节块
   *（沼泽、石头、水坑、树等），不包含边界本身。
   *
   * 这是在没有完整 DRLGOUTDOORS Tile 系统之前，用于“提前消费”
   * Type=6 这组 LvlSub 的过渡实现，让画面更接近原版。
   */
  public static void placeWildDetailsAct1(Zone zone, int seed) {
    if (zone == null || zone.town) {
      return;
    }

    // 仅对 Act1 户外关卡生效（Blood Moor ~ Tamoe Highland 这一带）
    if (zone.level.Act != 0) {
      return;
    }

    if (Riiablo.files == null || Riiablo.files.LvlSub == null) {
      return;
    }

    final int subType = zone.level.SubType;
    if (subType < 0) {
      return;
    }

    // 当前数据中，Act1 野外细节使用 Type=6（Swamp / Stone / Puddles / Trees / Object）
    LvlSub.Entry[] subs = Riiablo.files.LvlSub.getByType(subType);
    if (subs.length == 0) {
      return;
    }

    java.util.ArrayList<LvlSub.Entry> detailSubs = new java.util.ArrayList<>();
    for (LvlSub.Entry e : subs) {
      if (e.File == null || e.File.isEmpty()) continue;
      // 排除 BordType>=0 的边界（目前 Type=6 全是 -1，这里只是保持语义清晰）
      if (e.BordType >= 0) continue;
      detailSubs.add(e);
    }

    if (detailSubs.isEmpty()) {
      return;
    }

    long zoneSeed = ((long) seed * 1315423911L) ^ (zone.level.Id * 0x9E3779B9L);
    MathUtils.random.setSeed((int) zoneSeed);

    int gridsX = zone.gridsX;
    int gridsY = zone.gridsY;

    // 简化版：对每种细节块，按照 Prob0 / Max0 进行若干次尝试
    for (LvlSub.Entry sub : detailSubs) {
      int prob = (sub.Prob != null && sub.Prob.length > 0) ? sub.Prob[0] : 0;
      int max  = (sub.Max  != null && sub.Max.length  > 0) ? sub.Max[0]  : 0;
      if (prob <= 0 || max <= 0) continue;

      // 经验上 Prob0 往往是百分数，这里用 0-99 比例近似
      int attempts = max;
      for (int i = 0; i < attempts; i++) {
        if (MathUtils.random(0, 99) >= prob) continue;

        int gx = MathUtils.random(0, gridsX - 1);
        int gy = MathUtils.random(0, gridsY - 1);

        // 尽量避免覆盖已经放置的重要 preset（如入口、路径等）
        if (zone.presets[gx][gy] != null && isImportantPreset(zone.presets[gx][gy])) {
          continue;
        }
        if (zone.presets[gx][gy] != null && !canPlaceFeature(zone, gx, gy)) {
          continue;
        }

        Preset preset = pickDetailPresetFromLvlSub(sub, zone);
        if (preset == null) continue;

        zone.presets[gx][gy] = preset;
      }
    }
  }

  /**
   * 从一组 LvlSub 记录中选择一个边界块，并映射到对应的 LvlPrest 预设。
   *
   * 映射策略：
   * - 根据 LvlSub.Entry.File 字段，在 LvlPrest 中查找包含该 ds1 文件名的记录；
   * - 在该 LvlPrest 的 File 数组中找出与 LvlSub.File 完全匹配的下标，作为 ds1 index；
   * - 返回对应的 Preset.of(presetEntry, index)。
   *
   * 注意：
   * - 如果找不到对应的 LvlPrest 或具体文件索引，则返回 null，不在当前位置放置边界。
   */
  private static Preset pickBorderPresetFromLvlSub(java.util.List<LvlSub.Entry> borderSubs, Zone zone) {
    if (borderSubs.isEmpty()) {
      return null;
    }

    // 简单随机：从候选边界记录中挑一个
    int idx = MathUtils.random(borderSubs.size() - 1);
    LvlSub.Entry sub = borderSubs.get(idx);
    String targetFile = sub.File;
    if (targetFile == null || targetFile.isEmpty()) {
      return null;
    }

    // 在 LvlPrest 中查找包含该 ds1 的记录
    for (LvlPrest.Entry preset : Riiablo.files.LvlPrest) {
      // 仅考虑同一 Act 或同一 LevelType 的记录，粗略过滤
      if (preset.LevelId != 0 && preset.LevelId != zone.level.Id) {
        continue;
      }

      if (preset.File == null) continue;

      for (int i = 0; i < preset.File.length; i++) {
        String file = preset.File[i];
        if (file == null || file.isEmpty()) continue;

        // ds1 文件路径通常大小写不敏感，这里用 equalsIgnoreCase
        if (file.equalsIgnoreCase(targetFile)) {
          return Preset.of(preset, i);
        }
      }
    }

    // 找不到匹配的 preset，返回 null
    return null;
  }

  /**
   * 将单条 LvlSub 记录映射到对应的 LvlPrest 预设，用于野外细节块。
   * 与边界类似，但不过滤 LevelId，只是优先同 LevelId / 同 Act。
   */
  private static Preset pickDetailPresetFromLvlSub(LvlSub.Entry sub, Zone zone) {
    String targetFile = sub.File;
    if (targetFile == null || targetFile.isEmpty()) {
      return null;
    }

    LvlPrest.Entry best = null;
    int bestIndex = -1;

    // 第一轮：优先 LevelId 精确匹配
    for (LvlPrest.Entry preset : Riiablo.files.LvlPrest) {
      if (preset.LevelId != zone.level.Id) continue;
      if (preset.File == null) continue;
      for (int i = 0; i < preset.File.length; i++) {
        String file = preset.File[i];
        if (file == null || file.isEmpty()) continue;
        if (file.equalsIgnoreCase(targetFile)) {
          best = preset;
          bestIndex = i;
          break;
        }
      }
      if (best != null) break;
    }

    // 第二轮：放宽到 LevelId==0 或其它关卡（同 Act 的资源往往复用）
    if (best == null) {
      for (LvlPrest.Entry preset : Riiablo.files.LvlPrest) {
        if (preset.File == null) continue;
        for (int i = 0; i < preset.File.length; i++) {
          String file = preset.File[i];
          if (file == null || file.isEmpty()) continue;
          if (file.equalsIgnoreCase(targetFile)) {
            best = preset;
            bestIndex = i;
            break;
          }
        }
        if (best != null) break;
      }
    }

    if (best == null || bestIndex < 0) {
      return null;
    }

    return Preset.of(best, bestIndex);
  }

  /**
   * 在区域中放置路径（Paths）
   * 参考 D2MOD: DRLGOUTDOORS_SpawnAct1DirtPaths
   * 
   * @param zone 目标区域
   * @param seed 随机种子
   */
  public static void placePaths(Zone zone, int seed) {
    if (zone == null || zone.town) {
      return;
    }

    long zoneSeed = (long) seed * 31 + zone.level.Id;
    MathUtils.random.setSeed((int) zoneSeed);

    // 简化实现：在区域中随机放置一些路径预设
    // 实际 D2MOD 使用更复杂的路径生成算法
    int gridWidth = zone.gridsX;
    int gridHeight = zone.gridsY;
    int pathCount = MathUtils.random(2, 5); // 2-5 条路径

    for (int i = 0; i < pathCount; i++) {
      int startX = MathUtils.random(gridWidth - 1);
      int startY = MathUtils.random(gridHeight - 1);
      int length = MathUtils.random(3, 8);
      int direction = MathUtils.random(3); // 0=右, 1=下, 2=左, 3=上

      for (int j = 0; j < length; j++) {
        int x = startX;
        int y = startY;

        switch (direction) {
          case 0: x += j; break;
          case 1: y += j; break;
          case 2: x -= j; break;
          case 3: y -= j; break;
        }

        if (x >= 0 && x < gridWidth && y >= 0 && y < gridHeight) {
          if (zone.presets[x][y] == null) {
            LvlPrest.Entry pathPreset = findPathPreset(zone.level.Id);
            if (pathPreset != null) {
              int fileId[] = new int[6];
              int numFiles = Preset.getPresets(pathPreset, fileId);
              if (numFiles > 0) {
                int selectIndex = MathUtils.random(numFiles - 1);
                int select = fileId[selectIndex];
                zone.presets[x][y] = Preset.of(pathPreset, select);
              }
            }
          }
        }
      }
    }

    if (DEBUG) {
      Gdx.app.debug(TAG, String.format("Placed paths in %s", zone.level.LevelName));
    }
  }

  /**
   * 检查位置是否可以放置功能
   */
  private static boolean canPlaceFeature(Zone zone, int gridX, int gridY) {
    // 检查周围是否有重要预设（如入口、出口等）
    for (int dx = -1; dx <= 1; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        int x = gridX + dx;
        int y = gridY + dy;
        if (x >= 0 && x < zone.gridsX && y >= 0 && y < zone.gridsY) {
          Preset preset = zone.presets[x][y];
          if (preset != null && isImportantPreset(preset)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * 检查是否是重要的预设（不应被覆盖）
   */
  private static boolean isImportantPreset(Preset preset) {
    // TODO: 实现重要预设检查逻辑
    // 例如：入口、出口、城镇连接等
    return false;
  }

  /**
   * 查找传送点预设
   * 参考 D2MOD: 传送点通常有特定的预设 ID
   */
  private static LvlPrest.Entry findWaypointPreset(int levelId) {
    // 方法1: 通过 LevelId 查找（如果 LvlPrest 中有专门的 waypoint 预设）
    for (LvlPrest.Entry preset : Riiablo.files.LvlPrest) {
      if (preset.LevelId == levelId) {
        // 检查预设名称或文件路径是否包含 waypoint
        for (String file : preset.File) {
          if (file != null && (file.toLowerCase().contains("waypoint") || 
              file.toLowerCase().contains("wp"))) {
            return preset;
          }
        }
      }
    }
    
    // 方法2: 查找通用的 waypoint 预设（Def ID 可能需要根据实际数据调整）
    // TODO: 根据实际的 LvlPrest.txt 数据查找 waypoint 预设的 Def ID
    // 例如：Act1 waypoint 可能是某个特定的 Def ID
    
    return null;
  }

  /**
   * 查找神殿预设
   */
  private static LvlPrest.Entry findShrinePreset(int levelId, int shrineType) {
    // 查找包含 "shrine" 的预设
    for (LvlPrest.Entry preset : Riiablo.files.LvlPrest) {
      if (preset.LevelId == levelId) {
        for (String file : preset.File) {
          if (file != null && file.toLowerCase().contains("shrine")) {
            return preset;
          }
        }
      }
    }
    return null;
  }

  /**
   * 从 zone 中已放置的 path preset 获取一个路径瓦片 ID，用于 D2MOD 路径绘制。
   * 优先使用 path/dirt 预设的地板瓦片，确保路径在地图上可见。
   * @return 有效的 path tile id，或 -1
   */
  public static int getPathTileIdForZone(Zone zone) {
    if (zone == null || zone.dt1s == null || zone.presets == null) return -1;
    for (int gx = 0; gx < zone.presets.length; gx++) {
      Preset[] col = zone.presets[gx];
      if (col == null) continue;
      for (int gy = 0; gy < col.length; gy++) {
        Preset preset = col[gy];
        if (preset == null || preset.preset == null) continue;
        // 检查是否为 path 预设
        boolean isPath = false;
        for (String f : preset.preset.File) {
          if (f != null && (f.toLowerCase().contains("path") || f.toLowerCase().contains("dirt"))) {
            isPath = true;
            break;
          }
        }
        if (!isPath) continue;
        DS1 ds1 = preset.ds1;
        if (ds1 == null || ds1.floors == null || ds1.floorLen <= 0) continue;
        for (int i = 0; i < ds1.floorLen; i++) {
          DS1.Cell cell = ds1.floors[i];
          if (cell != null && cell.mainIndex >= 0 && cell.subIndex >= 0) {
            DT1.Tile t = zone.dt1s.get(cell.id);
            if (t != null && t.id > 0) return t.id;
          }
        }
      }
    }
    return -1;
  }

  /**
   * 查找路径预设
   */
  private static LvlPrest.Entry findPathPreset(int levelId) {
    for (LvlPrest.Entry preset : Riiablo.files.LvlPrest) {
      if (preset.LevelId == levelId) {
        for (String file : preset.File) {
          if (file != null && (file.toLowerCase().contains("path") || 
              file.toLowerCase().contains("dirt"))) {
            return preset;
          }
        }
      }
    }
    return null;
  }

}
