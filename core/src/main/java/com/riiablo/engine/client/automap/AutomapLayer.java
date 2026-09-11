package com.riiablo.engine.client.automap;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.map.Map;

/**
 * 小地图图层数据
 * 存储单个关卡/区域的小地图单元格信息
 * 
 * 参考: D2MOD D2AutomapLayerStrc
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
          exploredTiles.put(tileKey(playerX + dx, playerY + dy), Boolean.TRUE);
        }
      }
    }
  }

  /**
   * Reveals a native D2MOO RoomEx rectangle.  Room activation is authoritative
   * for automap visibility: the current room and its CLIENT_IN_SIGHT ring are
   * revealed, while OUT_OF_SIGHT/UNTILE rooms remain hidden.
   */
  public void updateRoomExploration(Map.Zone zone, int playerX, int playerY) {
    if (zone == null || !zone.hasNativeRoomTopology()) {
      updateExploration(playerX, playerY);
      return;
    }
    boolean revealed = false;
    for (int i = 0, n = zone.getRoomsEx().size; i < n; i++) {
      Map.RoomEx room = zone.getRoomsEx().get(i);
      if (room.getActivationStatus() <= Map.RoomEx.CLIENT_IN_SIGHT) {
        revealRoom(room);
        revealed = true;
      }
    }
    // Before RoomActivationSystem has observed the first player tick, retain
    // the original small reveal so the automap is not blank for one frame.
    if (!revealed) updateExploration(playerX, playerY);
  }

  /** Reveals one RoomEx footprint in world-subtile coordinates. */
  public void revealRoom(Map.RoomEx room) {
    if (room == null) return;
    revealRect(room.x, room.y, room.width, room.height);
  }

  /** Reveals a rectangular world-subtile footprint; dimensions are clamped positive. */
  public void revealRect(int x, int y, int width, int height) {
    int maxX = x + Math.max(1, width);
    int maxY = y + Math.max(1, height);
    for (int py = y; py < maxY; py++) {
      for (int px = x; px < maxX; px++) {
        exploredTiles.put(tileKey(px, py), Boolean.TRUE);
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
    return exploredTiles.containsKey(tileKey(x, y));
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

  /** Clears generated DC6 cells while preserving exploration progress. */
  public void clearCells() {
    floors.clear();
    walls.clear();
    objects.clear();
    extras.clear();
  }
  
  /**
   * 清除所有单元格数据
   */
  public void clear() {
    clearCells();
    exploredTiles.clear();
    saved = false;
  }

  private static int tileKey(int x, int y) {
    return (x << 16) | (y & 0xFFFF);
  }
}
