package com.riiablo.engine.server.collision;

/**
 * 碰撞单位大小 - 基于 D2MOD D2C_CollisionUnitSize 移植
 * 
 * <p>定义了单位在子格中的占用宽度。
 * 
 * <p>参考：D2MOD/source/D2Common/include/D2Collision.h
 * 
 * @author riiablo team
 */
public final class CollisionSize {
  private CollisionSize() {} // 不可实例化

  //==========================================================================
  // 大小常量
  //==========================================================================

  /** 无大小（不占用空间） */
  public static final int NONE = 0;
  
  /** 点大小（占用 1 个子格宽度） */
  public static final int POINT = 1;
  
  /** 小型单位（占用 2 个子格宽度） */
  public static final int SMALL = 2;
  
  /** 大型单位（占用 3 个子格宽度） */
  public static final int BIG = 3;
  
  /** 大小种类数 */
  public static final int COUNT = 4;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查大小是否有效
   * 
   * @param size 大小值
   * @return true 如果有效
   */
  public static boolean isValid(int size) {
    return size >= NONE && size < COUNT;
  }

  /**
   * 获取大小名称
   * 
   * @param size 大小值
   * @return 大小名称
   */
  public static String getName(int size) {
    switch (size) {
      case NONE: return "None";
      case POINT: return "Point";
      case SMALL: return "Small";
      case BIG: return "Big";
      default: return "Unknown";
    }
  }

  /**
   * 获取实际的子格宽度
   * 
   * @param size 大小值
   * @return 子格宽度
   */
  public static int getSubtileWidth(int size) {
    switch (size) {
      case NONE: return 0;
      case POINT: return 1;
      case SMALL: return 2;
      case BIG: return 3;
      default: return 1;
    }
  }

  /**
   * 根据子格宽度获取大小类型
   * 
   * @param width 子格宽度
   * @return 大小类型
   */
  public static int fromSubtileWidth(int width) {
    if (width <= 0) return NONE;
    if (width == 1) return POINT;
    if (width == 2) return SMALL;
    return BIG;
  }
}
