package com.riiablo.map;

import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlPrest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.EntityFactory;
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
public class BaseMapBuilderD2MOD {
  protected static final String TAG = "BaseMapBuilderD2MOD";
  protected static final boolean DEBUG = true;
  protected static final boolean DEBUG_BUILD = DEBUG && true;

  // D2MOD: 方向定义 (sub_6FD81850/sub_6FD81430 中的 a3 参数)
  // 0 = 下方 (South), 1 = 左侧 (West), 2 = 上方 (North), 3 = 右侧 (East)
  protected static final int DIR_SOUTH = 0;
  protected static final int DIR_WEST = 1;
  protected static final int DIR_NORTH = 2;
  protected static final int DIR_EAST = 3;

  // 这些字段在子类中会被 @Wire 注入
  protected EntityFactory factory;
  protected com.badlogic.gdx.net.Socket socket;

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
    int tilesX = level.SizeX[diff];
    int tilesY = level.SizeY[diff];
    int gridsX = tilesX / gridSizeX;  // 例如：80 / 8 = 10
    int gridsY = tilesY / gridSizeY;  // 例如：80 / 8 = 10
    
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
  protected Zone.Generator createMonsterGenerator(final com.badlogic.gdx.net.Socket finalSocket) {
    return new Zone.Generator() {
      final float SPAWN_MULT = 2f;
      MonStats.Entry[] monsters;

      @Override
      public void init(Zone zone) {
        int prob = 0;
        int numMon = zone.level.NumMon;
        if (numMon <= 0) {
          monsters = new MonStats.Entry[0];
          return;
        }
        MonStats.Entry[] monstats = new MonStats.Entry[numMon];
        for (int j = 0; j < numMon; j++) {
          String mon = zone.level.mon[j];
          if (mon == null || mon.isEmpty()) continue;
          monstats[j] = Riiablo.files.monstats.get(mon);
          if (monstats[j] != null) {
            prob += monstats[j].Rarity;
          }
        }

        if (prob <= 0) {
          monsters = new MonStats.Entry[0];
          return;
        }

        monsters = new MonStats.Entry[prob];
        prob = 0;
        for (MonStats.Entry entry : monstats) {
          if (entry != null) {
            for (int j = 0; j < entry.Rarity; j++) {
              monsters[prob++] = entry;
            }
          }
        }
      }

      @Override
      public void generate(Zone zone, DT1s dt1s, int tx, int ty) {
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
            
            // 生成怪物（仅在客户端）
            if (finalSocket == null && zone.map.factory != null && monsters != null && monsters.length > 0) {
              if (MathUtils.randomBoolean(SPAWN_MULT * zone.level.MonDen[zone.diff] / 100000f)) {
                int idx = MathUtils.random(monsters.length - 1);
                MonStats.Entry monster = monsters[idx];
                if (monster == null) continue;
                int count = monster.MinGrp == monster.MaxGrp
                    ? monster.MaxGrp
                    : MathUtils.random(monster.MinGrp, monster.MaxGrp);
                for (int j = 0; j < count; j++) {
                  float px = zone.getGlobalX((currentTx - zone.tx) * DT1.Tile.SUBTILE_SIZE) + MathUtils.random(-2f, 2f);
                  float py = zone.getGlobalY((currentTy - zone.ty) * DT1.Tile.SUBTILE_SIZE) + MathUtils.random(-2f, 2f);
                  zone.map.factory.createMonster(monster, px, py);
                }
              }
            }
          }
        }
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
        MathUtils.random.setSeed(seed);
        
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
          int randomIndex = MathUtils.random(floorTileIds.size - 1);
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
