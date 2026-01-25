package com.riiablo.engine.server.collision;

/**
 * 碰撞模式 - 基于 D2MOO D2C_CollisionPattern 移植
 * 
 * <p>定义了不同类型单位的碰撞检测模式。
 * 
 * <p>参考：D2MOO/source/D2Common/include/D2Collision.h
 * 
 * @author riiablo team
 */
public final class CollisionPattern {
  private CollisionPattern() {} // 不可实例化

  //==========================================================================
  // 模式常量
  //==========================================================================

  /** 无模式 */
  public static final int NONE = 0;
  
  /** 小型单位存在 */
  public static final int SMALL_UNIT_PRESENCE = 1;
  
  /** 大型单位存在 */
  public static final int BIG_UNIT_PRESENCE = 2;
  
  /** 小型宠物存在（与是否可被攻击相关） */
  public static final int SMALL_PET_PRESENCE = 3;
  
  /** 大型宠物存在 */
  public static final int BIG_PET_PRESENCE = 4;
  
  /** 小型无存在（不阻挡路径） */
  public static final int SMALL_NO_PRESENCE = 5;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查模式是否有效
   * 
   * @param pattern 模式值
   * @return true 如果有效
   */
  public static boolean isValid(int pattern) {
    return pattern >= NONE && pattern <= SMALL_NO_PRESENCE;
  }

  /**
   * 获取模式名称
   * 
   * @param pattern 模式值
   * @return 模式名称
   */
  public static String getName(int pattern) {
    switch (pattern) {
      case NONE: return "None";
      case SMALL_UNIT_PRESENCE: return "Small Unit";
      case BIG_UNIT_PRESENCE: return "Big Unit";
      case SMALL_PET_PRESENCE: return "Small Pet";
      case BIG_PET_PRESENCE: return "Big Pet";
      case SMALL_NO_PRESENCE: return "Small No Presence";
      default: return "Unknown";
    }
  }

  /**
   * 检查模式是否是宠物
   * 
   * @param pattern 模式值
   * @return true 如果是宠物模式
   */
  public static boolean isPetPattern(int pattern) {
    return pattern == SMALL_PET_PRESENCE || pattern == BIG_PET_PRESENCE;
  }

  /**
   * 检查模式是否是大型单位
   * 
   * @param pattern 模式值
   * @return true 如果是大型单位
   */
  public static boolean isBigPattern(int pattern) {
    return pattern == BIG_UNIT_PRESENCE || pattern == BIG_PET_PRESENCE;
  }

  /**
   * 获取模式对应的碰撞掩码
   * 
   * @param pattern 模式值
   * @return 对应的碰撞掩码
   */
  public static int getCollisionMask(int pattern) {
    switch (pattern) {
      case SMALL_UNIT_PRESENCE:
      case BIG_UNIT_PRESENCE:
        return CollisionMask.MONSTER;
      case SMALL_PET_PRESENCE:
      case BIG_PET_PRESENCE:
        return CollisionMask.PET;
      case SMALL_NO_PRESENCE:
        return CollisionMask.NONE;
      default:
        return CollisionMask.NONE;
    }
  }

  /**
   * 根据单位类型和大小获取模式
   * 
   * @param isMonster 是否是怪物/玩家
   * @param isPet 是否是宠物
   * @param isBig 是否是大型单位
   * @return 碰撞模式
   */
  public static int getPattern(boolean isMonster, boolean isPet, boolean isBig) {
    if (isPet) {
      return isBig ? BIG_PET_PRESENCE : SMALL_PET_PRESENCE;
    } else if (isMonster) {
      return isBig ? BIG_UNIT_PRESENCE : SMALL_UNIT_PRESENCE;
    }
    return NONE;
  }
}
