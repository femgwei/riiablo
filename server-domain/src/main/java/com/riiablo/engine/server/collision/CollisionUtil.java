package com.riiablo.engine.server.collision;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 碰撞工具类 - 基于 D2MOD Collision.cpp 移植
 * 
 * <p>提供碰撞检测相关的辅助方法。
 * 
 * <p>参考：D2MOD/source/D2Common/src/Collision/Collision.cpp
 * 
 * @author riiablo team
 */
public final class CollisionUtil {
  private static final Logger log = LogManager.getLogger(CollisionUtil.class);

  private CollisionUtil() {} // 不可实例化

  //==========================================================================
  // 射线追踪
  //==========================================================================

  /**
   * 射线追踪 - 检查从起点到终点是否有碰撞
   * 
   * <p>使用 Bresenham 算法沿射线检查碰撞
   * 
   * @param startX 起点 X
   * @param startY 起点 Y
   * @param endX 终点 X
   * @param endY 终点 Y
   * @param collisionGrid 碰撞网格（二维数组）
   * @param gridWidth 网格宽度
   * @param gridHeight 网格高度
   * @param mask 碰撞掩码
   * @param result 输出碰撞点（可为 null）
   * @return true 如果发现碰撞
   */
  public static boolean rayTrace(int startX, int startY, int endX, int endY,
                                  int[][] collisionGrid, int gridWidth, int gridHeight,
                                  int mask, Vector2 result) {
    int dx = Math.abs(endX - startX);
    int dy = Math.abs(endY - startY);
    int sx = startX < endX ? 1 : -1;
    int sy = startY < endY ? 1 : -1;
    int err = dx - dy;
    
    int x = startX;
    int y = startY;
    
    while (true) {
      // 检查边界
      if (x < 0 || x >= gridWidth || y < 0 || y >= gridHeight) {
        if (result != null) {
          result.set(x, y);
        }
        return true; // 超出边界视为碰撞
      }
      
      // 检查碰撞
      if (collisionGrid != null && CollisionMask.hasAny(collisionGrid[y][x], mask)) {
        if (result != null) {
          result.set(x, y);
        }
        return true;
      }
      
      // 到达终点
      if (x == endX && y == endY) {
        break;
      }
      
      // Bresenham 步进
      int e2 = 2 * err;
      if (e2 > -dy) {
        err -= dy;
        x += sx;
      }
      if (e2 < dx) {
        err += dx;
        y += sy;
      }
    }
    
    return false;
  }

  /**
   * 简化版射线追踪 - 检查两点之间是否有碰撞
   * 
   * @param startX 起点 X
   * @param startY 起点 Y
   * @param endX 终点 X
   * @param endY 终点 Y
   * @param collisionGrid 碰撞网格
   * @param gridWidth 网格宽度
   * @param gridHeight 网格高度
   * @param mask 碰撞掩码
   * @return true 如果有碰撞
   */
  public static boolean hasLineOfSight(int startX, int startY, int endX, int endY,
                                        int[][] collisionGrid, int gridWidth, int gridHeight,
                                        int mask) {
    return !rayTrace(startX, startY, endX, endY, collisionGrid, gridWidth, gridHeight, mask, null);
  }

  //==========================================================================
  // 距离计算
  //==========================================================================

  /**
   * 计算两点之间的曼哈顿距离
   * 
   * @param x1 点1 X
   * @param y1 点1 Y
   * @param x2 点2 X
   * @param y2 点2 Y
   * @return 曼哈顿距离
   */
  public static int manhattanDistance(int x1, int y1, int x2, int y2) {
    return Math.abs(x2 - x1) + Math.abs(y2 - y1);
  }

  /**
   * 计算两点之间的切比雪夫距离
   * 
   * @param x1 点1 X
   * @param y1 点1 Y
   * @param x2 点2 X
   * @param y2 点2 Y
   * @return 切比雪夫距离
   */
  public static int chebyshevDistance(int x1, int y1, int x2, int y2) {
    return Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
  }

  /**
   * 计算两点之间的欧几里得距离的平方
   * 
   * @param x1 点1 X
   * @param y1 点1 Y
   * @param x2 点2 X
   * @param y2 点2 Y
   * @return 距离的平方
   */
  public static int distanceSquared(int x1, int y1, int x2, int y2) {
    int dx = x2 - x1;
    int dy = y2 - y1;
    return dx * dx + dy * dy;
  }

  /**
   * 计算两点之间的欧几里得距离
   * 
   * @param x1 点1 X
   * @param y1 点1 Y
   * @param x2 点2 X
   * @param y2 点2 Y
   * @return 距离
   */
  public static float distance(int x1, int y1, int x2, int y2) {
    return (float) Math.sqrt(distanceSquared(x1, y1, x2, y2));
  }

  //==========================================================================
  // 边界框辅助
  //==========================================================================

  /**
   * 创建以指定点为中心的边界框
   * 
   * @param box 输出边界框
   * @param centerX 中心 X
   * @param centerY 中心 Y
   * @param sizeX X 方向大小
   * @param sizeY Y 方向大小
   */
  public static void createBoundingBox(BoundingBox box, int centerX, int centerY, int sizeX, int sizeY) {
    box.setFromCenter(centerX, centerY, sizeX, sizeY);
  }

  /**
   * 根据单位大小创建边界框
   * 
   * @param box 输出边界框
   * @param centerX 中心 X
   * @param centerY 中心 Y
   * @param unitSize 单位大小（CollisionSize 常量）
   */
  public static void createBoundingBoxForUnit(BoundingBox box, int centerX, int centerY, int unitSize) {
    int size = CollisionSize.getSubtileWidth(unitSize);
    createBoundingBox(box, centerX, centerY, size, size);
  }

  //==========================================================================
  // 空闲位置查找
  //==========================================================================

  /**
   * 在指定点附近查找空闲位置
   * 
   * <p>使用螺旋搜索模式
   * 
   * @param centerX 中心 X
   * @param centerY 中心 Y
   * @param collisionGrid 碰撞网格
   * @param gridWidth 网格宽度
   * @param gridHeight 网格高度
   * @param mask 碰撞掩码
   * @param maxRadius 最大搜索半径
   * @param result 输出空闲位置
   * @return true 如果找到空闲位置
   */
  public static boolean findFreePosition(int centerX, int centerY,
                                          int[][] collisionGrid, int gridWidth, int gridHeight,
                                          int mask, int maxRadius, Vector2 result) {
    // 先检查中心点
    if (isPositionFree(centerX, centerY, collisionGrid, gridWidth, gridHeight, mask)) {
      if (result != null) {
        result.set(centerX, centerY);
      }
      return true;
    }
    
    // 螺旋搜索
    for (int radius = 1; radius <= maxRadius; radius++) {
      // 顺时针螺旋搜索
      for (int i = -radius; i <= radius; i++) {
        // 上边
        if (checkAndSetResult(centerX + i, centerY + radius, 
            collisionGrid, gridWidth, gridHeight, mask, result)) {
          return true;
        }
        // 下边
        if (checkAndSetResult(centerX + i, centerY - radius, 
            collisionGrid, gridWidth, gridHeight, mask, result)) {
          return true;
        }
        // 右边
        if (checkAndSetResult(centerX + radius, centerY + i, 
            collisionGrid, gridWidth, gridHeight, mask, result)) {
          return true;
        }
        // 左边
        if (checkAndSetResult(centerX - radius, centerY + i, 
            collisionGrid, gridWidth, gridHeight, mask, result)) {
          return true;
        }
      }
    }
    
    return false;
  }

  private static boolean checkAndSetResult(int x, int y,
                                            int[][] collisionGrid, int gridWidth, int gridHeight,
                                            int mask, Vector2 result) {
    if (isPositionFree(x, y, collisionGrid, gridWidth, gridHeight, mask)) {
      if (result != null) {
        result.set(x, y);
      }
      return true;
    }
    return false;
  }

  /**
   * 检查指定位置是否空闲
   * 
   * @param x X 坐标
   * @param y Y 坐标
   * @param collisionGrid 碰撞网格
   * @param gridWidth 网格宽度
   * @param gridHeight 网格高度
   * @param mask 碰撞掩码
   * @return true 如果位置空闲
   */
  public static boolean isPositionFree(int x, int y,
                                        int[][] collisionGrid, int gridWidth, int gridHeight,
                                        int mask) {
    // 边界检查
    if (x < 0 || x >= gridWidth || y < 0 || y >= gridHeight) {
      return false;
    }
    
    // 碰撞检查
    if (collisionGrid == null) {
      return true;
    }
    
    return !CollisionMask.hasAny(collisionGrid[y][x], mask);
  }

  //==========================================================================
  // 移动碰撞
  //==========================================================================

  /**
   * 尝试移动单位碰撞掩码
   * 
   * <p>对应 D2MOD COLLISION_TryMoveUnitCollisionMask
   * 
   * @param fromX 起点 X
   * @param fromY 起点 Y
   * @param toX 终点 X
   * @param toY 终点 Y
   * @param unitSize 单位大小
   * @param collisionMask 单位碰撞掩码
   * @param moveConditionMask 移动条件掩码
   * @param collisionGrid 碰撞网格
   * @param gridWidth 网格宽度
   * @param gridHeight 网格高度
   * @return 碰撞结果（0 表示成功移动）
   */
  public static int tryMoveUnit(int fromX, int fromY, int toX, int toY,
                                 int unitSize, int collisionMask, int moveConditionMask,
                                 int[][] collisionGrid, int gridWidth, int gridHeight) {
    // 检查目标位置是否可以进入
    int size = CollisionSize.getSubtileWidth(unitSize);
    
    for (int dx = 0; dx < size; dx++) {
      for (int dy = 0; dy < size; dy++) {
        int checkX = toX + dx;
        int checkY = toY + dy;
        
        if (checkX < 0 || checkX >= gridWidth || checkY < 0 || checkY >= gridHeight) {
          return CollisionMask.BLANK;
        }
        
        int cellMask = collisionGrid[checkY][checkX];
        if (CollisionMask.hasAny(cellMask, moveConditionMask)) {
          return cellMask;
        }
      }
    }
    
    // 成功移动：清除旧位置，设置新位置
    // 注意：实际的碰撞网格修改应由调用者处理
    return 0;
  }

  //==========================================================================
  // 调试输出
  //==========================================================================

  /**
   * 打印碰撞网格（调试用）
   * 
   * @param collisionGrid 碰撞网格
   * @param gridWidth 网格宽度
   * @param gridHeight 网格高度
   */
  public static void debugPrintGrid(int[][] collisionGrid, int gridWidth, int gridHeight) {
    if (collisionGrid == null) {
      log.debug("Collision grid is null");
      return;
    }
    
    StringBuilder sb = new StringBuilder();
    sb.append("Collision Grid (").append(gridWidth).append("x").append(gridHeight).append("):\n");
    
    for (int y = gridHeight - 1; y >= 0; y--) {
      for (int x = 0; x < gridWidth; x++) {
        int mask = collisionGrid[y][x];
        if (mask == 0) {
          sb.append('.');
        } else if (CollisionMask.hasAny(mask, CollisionMask.WALL)) {
          sb.append('#');
        } else if (CollisionMask.hasAny(mask, CollisionMask.PLAYER)) {
          sb.append('P');
        } else if (CollisionMask.hasAny(mask, CollisionMask.MONSTER)) {
          sb.append('M');
        } else if (CollisionMask.hasAny(mask, CollisionMask.OBJECT)) {
          sb.append('O');
        } else {
          sb.append('?');
        }
      }
      sb.append('\n');
    }
    
    log.debug(sb.toString());
  }
}
