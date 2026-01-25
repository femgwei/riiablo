package com.riiablo.engine.server.cube;

/**
 * 赫拉迪克方块配方类型 - 基于 D2MOO HoradricCube.h 移植
 * 
 * <p>定义了赫拉迪克方块的配方类型。
 * 
 * <p>参考：D2MOO/source/D2Common/src/DataTbls/HoradricCube.cpp
 * 
 * @author riiablo team
 */
public final class CubeRecipeType {
  private CubeRecipeType() {} // 不可实例化

  //==========================================================================
  // 配方类型
  //==========================================================================

  /** 无效配方 */
  public static final int NONE = 0;

  /** 宝石升级 */
  public static final int GEM_UPGRADE = 1;

  /** 符文升级 */
  public static final int RUNE_UPGRADE = 2;

  /** 药水合成 */
  public static final int POTION_COMBINE = 3;

  /** 装备修复 */
  public static final int REPAIR = 4;

  /** 装备升级（普通->优质->精华） */
  public static final int ITEM_UPGRADE = 5;

  /** 洗点重置 */
  public static final int RESPEC = 6;

  /** 任务物品合成 */
  public static final int QUEST = 7;

  /** 符文之语 */
  public static final int RUNEWORD = 8;

  /** 添加凹槽 */
  public static final int ADD_SOCKET = 9;

  /** 装备再造（重新随机属性） */
  public static final int REROLL = 10;

  /** 装备降级 */
  public static final int ITEM_DOWNGRADE = 11;

  /** 护身符升级 */
  public static final int CHARM_UPGRADE = 12;

  /** Token 制作 */
  public static final int TOKEN = 13;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 获取类型名称
   */
  public static String getName(int type) {
    switch (type) {
      case NONE: return "None";
      case GEM_UPGRADE: return "Gem Upgrade";
      case RUNE_UPGRADE: return "Rune Upgrade";
      case POTION_COMBINE: return "Potion Combine";
      case REPAIR: return "Repair";
      case ITEM_UPGRADE: return "Item Upgrade";
      case RESPEC: return "Respec";
      case QUEST: return "Quest";
      case RUNEWORD: return "Runeword";
      case ADD_SOCKET: return "Add Socket";
      case REROLL: return "Reroll";
      case ITEM_DOWNGRADE: return "Item Downgrade";
      case CHARM_UPGRADE: return "Charm Upgrade";
      case TOKEN: return "Token";
      default: return "Unknown";
    }
  }
}
