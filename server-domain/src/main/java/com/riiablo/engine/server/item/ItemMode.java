package com.riiablo.engine.server.item;

/**
 * 物品模式枚举 - 基于 D2MOD D2C_ItemModes 移植
 * 
 * <p>定义了物品在游戏中的不同状态/位置。
 * 
 * <p>参考：D2MOD/source/D2Common/include/D2Items.h
 * 
 * @author riiablo team
 */
public final class ItemMode {
  private ItemMode() {} // 不可实例化

  //==========================================================================
  // 物品模式常量
  //==========================================================================

  /** 物品在存储中（背包、魔方、仓库） */
  public static final int STORED = 0;
  
  /** 物品已装备 */
  public static final int EQUIPPED = 1;
  
  /** 物品在腰带中 */
  public static final int IN_BELT = 2;
  
  /** 物品在地面上 */
  public static final int ON_GROUND = 3;
  
  /** 物品在光标上（正在拖动） */
  public static final int ON_CURSOR = 4;
  
  /** 物品正在掉落 */
  public static final int DROPPING = 5;
  
  /** 物品已镶嵌到另一个物品 */
  public static final int SOCKETED = 6;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查模式是否有效
   * 
   * @param mode 模式值
   * @return true 如果有效
   */
  public static boolean isValid(int mode) {
    return mode >= STORED && mode <= SOCKETED;
  }

  /**
   * 获取模式名称
   * 
   * @param mode 模式值
   * @return 模式名称
   */
  public static String getName(int mode) {
    switch (mode) {
      case STORED: return "Stored";
      case EQUIPPED: return "Equipped";
      case IN_BELT: return "In Belt";
      case ON_GROUND: return "On Ground";
      case ON_CURSOR: return "On Cursor";
      case DROPPING: return "Dropping";
      case SOCKETED: return "Socketed";
      default: return "Unknown";
    }
  }

  /**
   * 检查物品是否在玩家控制中
   * 
   * @param mode 模式值
   * @return true 如果在玩家控制中
   */
  public static boolean isInPlayerControl(int mode) {
    return mode == STORED || mode == EQUIPPED || mode == IN_BELT || mode == ON_CURSOR;
  }

  /**
   * 检查物品是否在地面上
   * 
   * @param mode 模式值
   * @return true 如果在地面上或正在掉落
   */
  public static boolean isOnGround(int mode) {
    return mode == ON_GROUND || mode == DROPPING;
  }

  /**
   * 检查物品是否可以被拾取
   * 
   * @param mode 模式值
   * @return true 如果可以被拾取
   */
  public static boolean canPickUp(int mode) {
    return mode == ON_GROUND;
  }

  /**
   * 检查物品是否可以被使用
   * 
   * @param mode 模式值
   * @return true 如果可以被使用
   */
  public static boolean canUse(int mode) {
    return mode == STORED || mode == EQUIPPED || mode == IN_BELT || mode == ON_CURSOR;
  }
}
