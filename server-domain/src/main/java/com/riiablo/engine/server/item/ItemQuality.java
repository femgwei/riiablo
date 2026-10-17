package com.riiablo.engine.server.item;

/**
 * 物品品质枚举 - 基于 D2MOD D2C_ItemQualities 移植
 * 
 * <p>定义了游戏中所有物品品质级别。
 * 
 * <p>参考：D2MOD/source/D2Common/include/D2Items.h
 * 
 * @author riiablo team
 */
public final class ItemQuality {
  private ItemQuality() {} // 不可实例化

  //==========================================================================
  // 物品品质常量
  //==========================================================================

  /** 劣质物品（白色，带负面属性） */
  public static final int INFERIOR = 0x01;
  
  /** 普通物品（白色） */
  public static final int NORMAL = 0x02;
  
  /** 超强物品（白色，带正面属性） */
  public static final int SUPERIOR = 0x03;
  
  /** 魔法物品（蓝色） */
  public static final int MAGIC = 0x04;
  
  /** 套装物品（绿色） */
  public static final int SET = 0x05;
  
  /** 稀有物品（黄色） */
  public static final int RARE = 0x06;
  
  /** 暗金物品（金色） */
  public static final int UNIQUE = 0x07;
  
  /** 手工物品（橙色） */
  public static final int CRAFT = 0x08;
  
  /** 锻造物品（用于测试） */
  public static final int TEMPERED = 0x09;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查品质是否有效
   * 
   * @param quality 品质值
   * @return true 如果有效
   */
  public static boolean isValid(int quality) {
    return quality >= INFERIOR && quality <= TEMPERED;
  }

  /**
   * 获取品质名称
   * 
   * @param quality 品质值
   * @return 品质名称
   */
  public static String getName(int quality) {
    switch (quality) {
      case INFERIOR: return "Inferior";
      case NORMAL: return "Normal";
      case SUPERIOR: return "Superior";
      case MAGIC: return "Magic";
      case SET: return "Set";
      case RARE: return "Rare";
      case UNIQUE: return "Unique";
      case CRAFT: return "Crafted";
      case TEMPERED: return "Tempered";
      default: return "Unknown";
    }
  }

  /**
   * 获取品质对应的颜色代码
   * 
   * @param quality 品质值
   * @return 颜色代码（0xRRGGBBAA）
   */
  public static int getColor(int quality) {
    switch (quality) {
      case INFERIOR:
      case NORMAL:
      case SUPERIOR:
        return 0xFFFFFFFF; // 白色
      case MAGIC:
        return 0x6969FFFF; // 蓝色
      case SET:
        return 0x00FF00FF; // 绿色
      case RARE:
        return 0xFFFF00FF; // 黄色
      case UNIQUE:
        return 0xC7B377FF; // 金色/棕褐色
      case CRAFT:
        return 0xFFA500FF; // 橙色
      default:
        return 0xFFFFFFFF;
    }
  }

  /**
   * 检查是否是魔法或更高品质
   * 
   * @param quality 品质值
   * @return true 如果是魔法或更高
   */
  public static boolean isMagicOrHigher(int quality) {
    return quality >= MAGIC;
  }

  /**
   * 检查是否可以有插槽
   * 
   * @param quality 品质值
   * @return true 如果可以有插槽
   */
  public static boolean canHaveSockets(int quality) {
    return quality == NORMAL || quality == SUPERIOR || quality == MAGIC || quality == RARE;
  }

  /**
   * 检查是否是唯一/套装（不能添加词缀）
   * 
   * @param quality 品质值
   * @return true 如果是唯一或套装
   */
  public static boolean isUniqueOrSet(int quality) {
    return quality == UNIQUE || quality == SET;
  }
}
