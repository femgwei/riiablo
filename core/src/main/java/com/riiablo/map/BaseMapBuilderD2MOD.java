package com.riiablo.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlPrest;
import com.riiablo.codec.excel.MonStats;

import com.riiablo.engine.server.monster.NativeMonsterRegion;
import com.riiablo.engine.server.NativeRng;
import com.riiablo.map.Map.Preset;
import com.riiablo.map.Map.Zone;

/**
 * 基础地图生成器 - 复用通用逻辑
 * 
 * 参考 D2MOD: source/D2Common/src/Drlg/DrlgOutPlace.cpp
 * 提供所有 Act 地图生成器共用的功能：
 * - 坐标计算
 * - 重叠检查
 * - 区域创建
 * - 地形生成
 */
public class BaseMapBuilderD2MOD extends MapBuilderResources {
  public BaseMapBuilderD2MOD() { super(); }
  public BaseMapBuilderD2MOD(MapResources resources) { super(resources); }
  protected static final String TAG = "BaseMapBuilderD2MOD";
  protected static final boolean DEBUG = true;
  protected static final boolean DEBUG_BUILD = DEBUG && true;

  // D2MOD: 方向定义 (sub_6FD81850/sub_6FD81430 中的 a3 参数)
  // 0 = 下方 (South), 1 = 左侧 (West), 2 = 上方 (North), 3 = 右侧 (East)
  protected static final int DIR_SOUTH = 0;
  protected static final int DIR_WEST = 1;
  protected static final int DIR_NORTH = 2;
  protected static final int DIR_EAST = 3;

  /**
   * D2MOD: sub_6FD81850 - 根据方向计算区域坐标偏移（用于城镇等）
   * 
   * @param coord1 源区域坐标
   * @param coord2 目标区域坐标（将被修改）
   * @param direction 方向 (0=South, 1=West, 2=North, 3=East)
   * @param offsetType 偏移类型 (0=无额外偏移, 1=小偏移, 2=中等偏移, 3=特殊偏移)
   */
  protected void calculateCoordOffset(ZoneCoord coord1, ZoneCoord coord2, int direction, int offsetType) {
    // D2MOD 使用 tile 单位，需要转换为 sub-tile 单位 (1 tile = 5 sub-tiles)
    final int TILE_TO_SUBTILE = 5;
    
    switch (direction) {
      case DIR_SOUTH: // 0: 下方
        coord2.x = coord1.x + coord1.width - coord2.width;
        coord2.y = coord1.y + coord1.height;
        if (offsetType == 1) {
          coord2.x += 16 * TILE_TO_SUBTILE; // 16 tiles = 80 sub-tiles
        }
        break;
        
      case DIR_WEST: // 1: 左侧
        coord2.x = coord1.x - coord2.width;
        coord2.y = coord1.y + coord1.height - coord2.height;
        if (offsetType == 1) {
          coord2.y += 16 * TILE_TO_SUBTILE;
        } else if (offsetType == 2) {
          coord2.y -= 8 * TILE_TO_SUBTILE;
        }
        break;
        
      case DIR_NORTH: // 2: 上方
        coord2.x = coord1.x;
        coord2.y = coord1.y - coord2.height;
        if (offsetType == 1) {
          coord2.x -= 16 * TILE_TO_SUBTILE;
        }
        break;
        
      case DIR_EAST: // 3: 右侧
        coord2.x = coord1.x + coord1.width;
        coord2.y = coord1.y;
        switch (offsetType) {
          case 1:
            coord2.y -= 16 * TILE_TO_SUBTILE;
            break;
          case 2:
            coord2.y += 8 * TILE_TO_SUBTILE;
            break;
          case 3:
            coord2.y -= 8 * TILE_TO_SUBTILE;
            break;
        }
        break;
    }
  }

  /**
   * D2MOD: sub_6FD81430 - 根据方向计算区域坐标偏移（用于 Blood Moor 等）
   */
  protected void calculateCoordOffsetAlt(ZoneCoord coord1, ZoneCoord coord2, int direction, int offsetType) {
    final int TILE_TO_SUBTILE = 5;
    
    switch (direction) {
      case DIR_SOUTH: // 0: 下方
        coord2.x = coord1.x;
        coord2.y = coord1.y + coord1.height;
        if (offsetType == 1) {
          coord2.x -= coord2.width / 2 + 8 * TILE_TO_SUBTILE;
        }
        break;
        
      case DIR_WEST: // 1: 左侧
        coord2.x = coord1.x;
        coord2.y = coord1.y + coord1.height;
        if (offsetType == 1) {
          coord2.x += coord2.width / 2 + 8 * TILE_TO_SUBTILE;
        }
        break;
        
      case DIR_NORTH: // 2: 上方
        coord2.x = coord1.x - coord2.width;
        coord2.y = coord1.y;
        if (offsetType == 1) {
          coord2.y -= coord2.height / 2 + 8 * TILE_TO_SUBTILE;
        }
        break;
        
      case DIR_EAST: // 3: 右侧
        coord2.x = coord1.x - coord2.width;
        coord2.y = coord1.y;
        if (offsetType == 1) {
          coord2.y += coord2.height / 2 + 8 * TILE_TO_SUBTILE;
        }
        break;
    }
  }

  /**
   * D2MOD: sub_6FD81330 - 处理第一个区域的放置
   * 直接使用 Levels.txt 中的 OffsetX/OffsetY
   */
  protected boolean placeFirstLevel(LevelLinkData linkData, int iteration, Levels.Entry level) {
    // D2MOD: 直接使用 Levels.txt 中的偏移量
    linkData.coords[iteration].x = level.OffsetX;
    linkData.coords[iteration].y = level.OffsetY;
    return true;
  }

  /**
   * D2MOD: sub_6FD82050 - 检查区域是否重叠（使用曼哈顿距离）
   * 参考 DRLG_CheckNotOverlappingUsingManhattanDistance
   */
  protected boolean checkNotOverlapping(LevelLinkData linkData, int iteration, int levelLink) {
    for (int i = 0; i < iteration; i++) {
      if (i != levelLink) {
        ZoneCoord coord1 = linkData.coords[iteration];
        ZoneCoord coord2 = linkData.coords[i];
        
        // 检查是否有重叠（允许刚好贴边，相当于曼哈顿距离 >= 0）
        if (!(coord1.x + coord1.width <= coord2.x || 
              coord2.x + coord2.width <= coord1.x ||
              coord1.y + coord1.height <= coord2.y ||
              coord2.y + coord2.height <= coord1.y)) {
          return false; // 有真正交叠
        }
      }
    }
    return true;
  }

  /**
   * 创建区域（带 preset）
   */
  protected Zone createZoneWithPreset(Map map, Levels.Entry level, LvlPrest.Entry preset, int select, 
                                       int x, int y, boolean isTown) {
    Zone zone = map.addZone(level, preset, select);
    zone.setPosition(x, y);
    zone.town = isTown;
    
    if (DEBUG_BUILD) {
      int minX = zone.x;
      int maxX = zone.x + zone.width;
      int minY = zone.y;
      int maxY = zone.y + zone.height;
      Gdx.app.debug(TAG, String.format("Placed %s (id=%d) at (%d, %d), town=%s", 
          level.LevelName, level.Id, x, y, isTown));
      Gdx.app.debug(TAG, String.format("  Zone bounds: X[%d, %d] (width=%d), Y[%d, %d] (height=%d)", 
          minX, maxX, zone.width, minY, maxY, zone.height));
    }
    
    return zone;
  }

  /**
   * 创建区域（无 preset，使用 generator）
   * 使用 8x8 网格系统（参考 D2MOD: DRLGGRID）
   */
  protected Zone createZoneWithGenerator(Map map, Levels.Entry level, int diff, int x, int y) {
    // 使用 8x8 网格系统（OutdoorGrid.GRID_SIZE_TILES = 8）
    int gridSizeX = OutdoorGrid.GRID_SIZE_TILES;  // 每个网格 8 tiles
    int gridSizeY = OutdoorGrid.GRID_SIZE_TILES;  // 每个网格 8 tiles
    
    // 计算网格数量
    int tilesX = Math.max(gridSizeX, MapDataPort.levelSizeX(level, diff, 1));
    int tilesY = Math.max(gridSizeY, MapDataPort.levelSizeY(level, diff, 1));
    int gridsX = Math.max(1, (tilesX + gridSizeX - 1) / gridSizeX);
    int gridsY = Math.max(1, (tilesY + gridSizeY - 1) / gridSizeY);
    
    // 使用带网格数量的 addZone 方法
    Zone zone = map.addZone(level, gridSizeX, gridSizeY, gridsX, gridsY);
    zone.setPosition(x, y);
    zone.town = false;
    
    if (DEBUG_BUILD) {
      int minX = zone.x;
      int maxX = zone.x + zone.width;
      int minY = zone.y;
      int maxY = zone.y + zone.height;
      Gdx.app.debug(TAG, String.format("Placed %s (id=%d) at (%d, %d) with generator", 
          level.LevelName, level.Id, x, y));
      Gdx.app.debug(TAG, String.format("  Zone bounds: X[%d, %d] (width=%d), Y[%d, %d] (height=%d)", 
          minX, maxX, zone.width, minY, maxY, zone.height));
      Gdx.app.debug(TAG, String.format("  Grid system: %dx%d grids (%dx%d tiles each), total: %dx%d tiles", 
          gridsX, gridsY, gridSizeX, gridSizeY, tilesX, tilesY));
    }
    
    return zone;
  }

  /**
   * 创建通用的怪物生成器
   */
  protected Zone.Generator createMonsterGenerator(final boolean remoteClient) {
    return new Zone.Generator() {
      MonStats.Entry[] monsters;
      boolean nativePopulationQueued;
      NativeRng selectionRng;
      NativeRng populationRng;

      @Override
      public void init(Zone zone) {
        // D2Game initializes all Levels.txt regions from one shared stream.
        // A per-level Java/LCG stream produces a plausible map but shifts the
        // roster as soon as an earlier level consumes a different number of
        // draws.  The state object also replays rows that are not materialized
        // by this headless map, so direct level probes keep native ordering.
        NativeMonsterRegionState.Selection selection =
            zone.map.nativeMonsterRegions().forLevel(zone.level.Id);
        monsters = selection.weighted.clone();
        zone.nativeRegionMonsters = selection.selected.clone();
        // Kept for the compatibility (non-native-tile) path. Native room
        // population uses the D2Seed streams in finish().
        selectionRng = null;
        populationRng = null;
      }

      @Override
      public void generate(Zone zone, DT1s dt1s, int tx, int ty) {
        // Native terrain and collision replace compatibility layers later.
        // Do not create live monsters against a floor that will be discarded.
        if (zone.nativeTileGrid() != null) return;
        if (DEBUG_BUILD && dt1s == null) {
          Gdx.app.error(TAG, String.format("generate: dt1s is null for zone %s (LevelType=%d)", 
              zone.level.LevelName, zone.level.LevelType));
        }
        
        final int startTx = tx;
        final int startTy = ty;
        final int gridSize = OutdoorGrid.GRID_SIZE_TILES;
        
        // 计算当前 grid 在 zone 中的网格坐标
        int gridX = (tx - zone.tx) / gridSize;
        int gridY = (ty - zone.ty) / gridSize;
        
        // 获取 Dt1Mask（根据 Level 的 Act 和 LevelType）
        int dt1Mask = OutdoorGrid.getDt1MaskForLevel(zone.level);
        
        if (DEBUG_BUILD && dt1s != null && dt1s.tiles.size == 0) {
          Gdx.app.error(TAG, String.format("generate: dt1s is empty for zone %s (LevelType=%d, dt1Mask=0x%X)", 
              zone.level.LevelName, zone.level.LevelType, dt1Mask));
        }
        
        // 遍历当前 8x8 网格内的所有 tile
        for (int x = 0; x < gridSize; x++) {
          for (int y = 0; y < gridSize; y++) {
            int currentTx = startTx + x;
            int currentTy = startTy + y;
            
            // 检查是否在 zone 范围内
            if (currentTx < zone.tx || currentTx >= zone.tx + zone.tilesX ||
                currentTy < zone.ty || currentTy >= zone.ty + zone.tilesY) {
              continue;
            }
            
            int tileIndex = Zone.index(zone.tilesX, currentTx - zone.tx, currentTy - zone.ty);
            
            // 生成地板（如果还没有被 preset 覆盖）
            if (zone.getLayer(Map.FLOOR_OFFSET)[tileIndex] == null) {
              DT1.Tile tile = selectTerrainTile(dt1s, gridX, gridY, x, y, dt1Mask);
              if (tile != null) {
                zone.getLayer(Map.FLOOR_OFFSET)[tileIndex] = tile;
              } else {
                // 回退到默认地板
                zone.getLayer(Map.FLOOR_OFFSET)[tileIndex] = dt1s.get(0, 0, 0);
              }
            }
            
            // Record authoritative compatibility placements; network clients receive replicas.
            if (!remoteClient && monsters != null && monsters.length > 0) {
              int populationTile = x * gridSize + y;
              int populationAttempts =
                  NativeMonsterRegion.populationAttemptsForGameTile(populationTile);
              for (int attempt = 0; attempt < populationAttempts; attempt++) {
                if (!NativeMonsterRegion.densityRoll(
                    NativeMonsterRegion.density(zone.level, zone.diff),
                    populationRng.nextInt(100000))) continue;
                MonStats.Entry selectedMonster = monsters[populationRng.nextInt(monsters.length)];
                if (selectedMonster == null) continue;
                if (!NativeMonsterRegion.sparsePopulationRoll(
                    selectedMonster.sparsePopulate, populationRng.nextInt(100))) continue;
                int minGroup = MapDataPort.minGroup(selectedMonster);
                int maxGroup = MapDataPort.maxGroup(selectedMonster);
                if (NativeMonsterRegion.isSingleMemberNormalGroup(
                    selectedMonster.BaseId, selectedMonster.Id)) {
                  minGroup = 1;
                  maxGroup = 1;
                }
                int count = minGroup == maxGroup
                    ? maxGroup : minGroup + populationRng.nextInt(maxGroup - minGroup + 1);
                for (int j = 0; j < count; j++) {
                  float px = zone.getGlobalX((currentTx - zone.tx) * DT1.Tile.SUBTILE_SIZE) + MapRng.nextFloat(populationRng, -2f, 2f);
                  float py = zone.getGlobalY((currentTy - zone.ty) * DT1.Tile.SUBTILE_SIZE) + MapRng.nextFloat(populationRng, -2f, 2f);
                  zone.queueMonster(selectedMonster.hcIdx, px, py);
                }
              }
            }
          }
        }
      }
      
      @Override
      public void finish(Zone zone) {
        if (zone.nativeTileGrid() == null || !zone.hasNativeRoomTopology()
            || zone.isTown() || remoteClient || nativePopulationQueued) return;
        NativeD2SeedRandom gameRandom = zone.map.nativePopulationRandom();
        int packId = 0;
        int uniqueCount = 0, populationRooms = 0, visitedRooms = 0;
        for (Map.RoomEx room : zone.getRoomsEx()) {
          if ((room.populationFlags() & com.d2moo.common.drlg.D2DrlgRoomFlags.POPULATION_ZERO) == 0)
            populationRooms++;
          for (Map.MonsterSpawn spawn : room.getPendingMonsterSpawns()) {
            packId = Math.max(packId, spawn.packId + 1);
            if (spawn.superUniqueId >= 0 || spawn.rank == com.riiablo.engine.server.monster.MonsterRank.UNIQUE
                || spawn.rank == com.riiablo.engine.server.monster.MonsterRank.CHAMPION) uniqueCount++;
          }
        }
        int bossMin = NativeMonsterEliteRules.bossMin(zone.level, zone.diff);
        int bossMax = NativeMonsterEliteRules.bossMax(zone.level, zone.diff);
        NativeMonsterEliteRules elites = bossMin > 0 || bossMax > 0
            ? NativeMonsterEliteRules.load(zone.map.mpqs()) : null;
        nativePopulationQueued = true;
        // Normal Act V outdoor levels have MonDen=0 and rely on these DS1
        // MonPlace group markers. Expand only after native collision exists.
        for (Map.NativeMonster preset : zone.getNativeMonsters()) {
          if (preset.populationGroupSize <= 0 || preset.spawned) continue;
          int x = Math.round(zone.x() + preset.x), y = Math.round(zone.y() + preset.y);
          Map.RoomEx room = zone.findRoomEx(x, y);
          MonStats.Entry monster = zone.map.files().monstats.get(preset.monsterId);
          if (room != null && monster != null)
            NativeRoomMonsterPopulation.addGroupAt(zone, room, monster, preset.populationGroupSize,
                packId++, new NativeD2SeedRandom(room.runtimeSeed()), x, y);
        }
        if (monsters == null || monsters.length == 0) return;
        for (Map.RoomEx room : zone.getRoomsEx()) {
          if ((room.populationFlags() & com.d2moo.common.drlg.D2DrlgRoomFlags.POPULATION_ZERO) != 0) continue;
          visitedRooms++;
          // RoomEx pSeed drives monster choice and placement.  It is kept on
          // the RoomEx object so later AI/activation code observes the same
          // consumed seed as the population pass.
          NativeD2SeedRandom roomRandom = new NativeD2SeedRandom(room.runtimeSeed());
          // Native density budgets and pack searches belong to each eligible
          // coordinate list, not to the entire RoomEx rectangle.
          for (NativeMonsterPopulationRegion region : room.populationRegions()) {
            for (int attempt = region.densityAttempts(); attempt > 0; attempt--) {
              if (!NativeMonsterRegion.densityRoll(NativeMonsterRegion.density(zone.level, zone.diff),
                  gameRandom.nextInt(100000))) continue;
              MonStats.Entry monster = replacePlaceSpawn(zone, monsters[roomRandom.nextInt(monsters.length)], 20, roomRandom);
              if (elites != null && NativeMonsterEliteRules.selectElite(
                  uniqueCount, bossMin, bossMax, visitedRooms, populationRooms, roomRandom)) {
                MonStats.Entry boss = chooseElite(zone, roomRandom);
                if (boss != null) uniqueCount += NativeRoomMonsterPopulation.addElite(
                    zone, room, boss, packId++, roomRandom, elites, region);
                continue;
              }
              if (monster == null || !NativeMonsterRegion.sparsePopulationRoll(
                  monster.sparsePopulate, gameRandom.nextInt(100))) continue;
              int min = MapDataPort.minGroup(monster), max = MapDataPort.maxGroup(monster);
              if (NativeMonsterRegion.isSingleMemberNormalGroup(monster.BaseId, monster.Id)) min = max = 1;
              // The normal-pack count is rolled from the leader's unit seed in
              // the native game.  Keep the room stream for placement/selection
              // and let addNativeGroup consume pGameSeed once per allocation.
              NativeRoomMonsterPopulation.addNativeGroup(
                  zone, room, monster, min, max, packId++, roomRandom, gameRandom, region);
            }
          }
        }
      }

      private MonStats.Entry chooseElite(Zone zone, java.util.Random rng) {
        // Normal bosses use the complete umon list, NOT the NumMon subset.
        if (zone.diff == 0) {
          Array<MonStats.Entry> candidates = new Array<>();
          if (zone.level.umon != null) for (String id : zone.level.umon) {
            if (id == null || id.isEmpty() || "0".equals(id)) continue;
            MonStats.Entry candidate = zone.map.files().monstats.get(id);
            if (candidate != null) candidates.add(candidate);
          }
          return candidates.size == 0 ? null : candidates.get(rng.nextInt(candidates.size));
        }
        return replacePlaceSpawn(zone, monsters[rng.nextInt(monsters.length)], 0, rng);
      }

      private MonStats.Entry replacePlaceSpawn(Zone zone, MonStats.Entry monster,
          int chance, java.util.Random rng) {
        if (monster != null && monster.placespawn && monster.spawn != null && !monster.spawn.isEmpty()) {
          MonStats.Entry replacement = zone.map.files().monstats.get(monster.spawn);
          if (replacement != null && rng.nextInt(100) > chance) return replacement;
        }
        return monster;
      }

      /**
       * 根据网格位置和 Dt1Mask 选择地形瓦片
       * 参考 D2MOD: DRLGOUTPLACE_CreateOutdoorRoomEx
       */
      private DT1.Tile selectTerrainTile(DT1s dt1s, int gridX, int gridY, int x, int y, int dt1Mask) {
        if (dt1s == null) {
          if (DEBUG_BUILD) {
            Gdx.app.debug(TAG, "selectTerrainTile: dt1s is null!");
          }
          return null;
        }
        
        // 使用网格坐标和局部坐标生成伪随机索引
        // 这样可以确保相同位置总是生成相同的地形（多人游戏一致性）
        int seed = (gridX * 31 + gridY) * 17 + (x * 7 + y);
        NativeRng terrainRng = MapRng.open(seed, "base.terrain", ((gridX & 0xffff) << 16) ^ (gridY & 0xffff));
        
        // 收集所有可用的 floor tiles (orientation = 0)
        Array<Integer> floorTileIds = new Array<>();
        for (IntMap.Entry<Array<DT1.Tile>> entry : dt1s.tiles.entries()) {
          int id = entry.key;
          int orientation = DT1.Tile.Index.orientation(id);
          if (orientation == 0) { // Floor tile
            Array<DT1.Tile> tileArray = entry.value;
            if (tileArray != null && tileArray.size > 0) {
              floorTileIds.add(id);
            }
          }
        }
        
        // 从所有可用的 floor tiles 中随机选择一个
        if (floorTileIds.size > 0) {
          int randomIndex = terrainRng.nextInt(floorTileIds.size);
          int selectedId = floorTileIds.get(randomIndex);
          DT1.Tile tile = dt1s.get(selectedId);
          if (tile != null) {
            return tile;
          }
        }
        
        // 如果找不到任何 floor tile，回退到默认值
        DT1.Tile tile = dt1s.get(0, 0, 0);
        if (tile == null && DEBUG_BUILD) {
          int totalTiles = 0;
          int floorTileCount = 0;
          for (IntMap.Entry<Array<DT1.Tile>> entry : dt1s.tiles.entries()) {
            Array<DT1.Tile> tileArray = entry.value;
            if (tileArray != null) {
              totalTiles += tileArray.size;
              int orientation = DT1.Tile.Index.orientation(entry.key);
              if (orientation == 0) floorTileCount++;
            }
          }
          Gdx.app.error(TAG, String.format("selectTerrainTile: No floor tile found! dt1s has %d tile types (%d floor types), %d total tiles", 
              dt1s.tiles.size, floorTileCount, totalTiles));
        }
        
        return tile;
      }
    };
  }

  /**
   * 区域坐标结构
   */
  protected static class ZoneCoord {
    int x, y, width, height;
    
    ZoneCoord() {}
    
    ZoneCoord(int x, int y, int width, int height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }
  }

  /**
   * 区域链接结构
   */
  protected static class LevelLink {
    int level;
    int levelLink;
    int levelLinkEx;
    
    LevelLink(int level, int levelLink, int levelLinkEx) {
      this.level = level;
      this.levelLink = levelLink;
      this.levelLinkEx = levelLinkEx;
    }
  }

  /**
   * 区域链接数据（对应 D2MOD 的 D2DrlgLevelLinkDataStrc）
   */
  protected static class LevelLinkData {
    ZoneCoord[] coords = new ZoneCoord[15];
    LevelLink[] links = new LevelLink[15];
    int[][] rand = new int[4][15]; // [0-3][0-14]
    
    LevelLinkData() {
      for (int i = 0; i < 15; i++) {
        coords[i] = new ZoneCoord();
        rand[0][i] = -1;
        rand[1][i] = -1;
        rand[2][i] = -1;
        rand[3][i] = -1;
      }
    }
  }
}

