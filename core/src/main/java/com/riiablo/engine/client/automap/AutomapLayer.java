package com.riiablo.engine.client.automap;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

/**
 * 小地图图层数据
 * 存储单个关卡/区域的小地图单元格信息
 * 
 * 参考: D2MOO D2AutomapLayerStrc
 */
public class AutomapLayer {
  
  /** 图层编号/关卡ID */
  public final int layerId;
  
  /** 是否已保存（用于持久化） */
  public boolean saved;
  
  /** 地板单元格列表 */
  public final Array<AutomapCell> floors = new Array<>();
  
  /** 墙壁单元格列表 */
  public final Array<AutomapCell> walls = new Array<>();
  
  /** 物体单元格列表 */
  public final Array<AutomapCell> objects = new Array<>();
  
  /** 额外单元格列表（特殊标记等） */
  public final Array<AutomapCell> extras = new Array<>();
  
  /** 已探索的子瓷砖位置 (key = x << 16 | y) */
  private final IntMap<Boolean> exploredTiles = new IntMap<>();
  
  /** 探索半径（以子瓷砖为单位） */
  private static final int EXPLORE_RADIUS = 15;
  
  public AutomapLayer(int layerId) {
    this.layerId = layerId;
    this.saved = false;
  }
  
  /**
   * 添加地板单元格
   */
  public void addFloor(int cellNo, int x, int y) {
    floors.add(new AutomapCell(cellNo, x, y));
  }
  
  /**
   * 添加墙壁单元格
   */
  public void addWall(int cellNo, int x, int y) {
    walls.add(new AutomapCell(cellNo, x, y));
  }
  
  /**
   * 添加物体单元格
   */
  public void addObject(int cellNo, int x, int y) {
    objects.add(new AutomapCell(cellNo, x, y));
  }
  
  /**
   * 添加额外单元格
   */
  public void addExtra(int cellNo, int x, int y) {
    extras.add(new AutomapCell(cellNo, x, y));
  }
  
  /**
   * 更新玩家位置，标记周围区域为已探索
   * 
   * @param playerX 玩家X坐标（子瓷砖）
   * @param playerY 玩家Y坐标（子瓷砖）
   */
  public void updateExploration(int playerX, int playerY) {
    // 以玩家为中心，标记周围区域为已探索
    for (int dy = -EXPLORE_RADIUS; dy <= EXPLORE_RADIUS; dy++) {
      for (int dx = -EXPLORE_RADIUS; dx <= EXPLORE_RADIUS; dx++) {
        // 使用圆形范围检测
        if (dx * dx + dy * dy <= EXPLORE_RADIUS * EXPLORE_RADIUS) {
          int key = ((playerX + dx) << 16) | (playerY + dy) & 0xFFFF;
          exploredTiles.put(key, Boolean.TRUE);
        }
      }
    }
  }
  
  /**
   * 检查指定位置是否已探索
   * 
   * @param x X坐标（子瓷砖）
   * @param y Y坐标（子瓷砖）
   * @return 是否已探索
   */
  public boolean isExplored(int x, int y) {
    int key = (x << 16) | y & 0xFFFF;
    return exploredTiles.containsKey(key);
  }
  
  /**
   * 获取已探索的瓷砖数量
   */
  public int getExploredCount() {
    return exploredTiles.size;
  }
  
  /**
   * 清除探索数据
   */
  public void clearExploration() {
    exploredTiles.clear();
  }
  
  /**
   * 清除所有单元格数据
   */
  public void clear() {
    floors.clear();
    walls.clear();
    objects.clear();
    extras.clear();
    exploredTiles.clear();
    saved = false;
  }
}
