package com.riiablo.engine.server.item;

/**
 * 物品标志位 - 基于 D2MOD D2C_ItemFlags 移植
 * 
 * <p>定义了物品的各种属性标志。
 * 
 * <p>参考：D2MOD/source/D2Common/include/D2Items.h
 * 
 * @author riiablo team
 */
public final class ItemFlags {
  private ItemFlags() {} // 不可实例化

  //==========================================================================
  // 物品标志常量
  //==========================================================================

  /** 新物品（刚拾取，名字闪烁） */
  public static final int NEWITEM = 0x00000001;
  
  /** 是目标 */
  public static final int TARGET = 0x00000002;
  
  /** 正在瞄准 */
  public static final int TARGETING = 0x00000004;
  
  /** 已删除 */
  public static final int DELETED = 0x00000008;
  
  /** 已鉴定 */
  public static final int IDENTIFIED = 0x00000010;
  
  /** 数量（堆叠物品） */
  public static final int QUANTITY = 0x00000020;
  
  /** 已切换武器组 */
  public static final int SWITCHIN = 0x00000040;
  
  /** 已切换出武器组 */
  public static final int SWITCHOUT = 0x00000080;
  
  /** 已损坏 */
  public static final int BROKEN = 0x00000100;
  
  /** 已修复（用于赫拉迪克方块） */
  public static final int REPAIRED = 0x00000200;
  
  /** 未知标志 */
  public static final int UNK_0x400 = 0x00000400;
  
  /** 有插槽 */
  public static final int SOCKETED = 0x00000800;
  
  /** 不可出售给 NPC */
  public static final int NOSELL = 0x00001000;
  
  /** 在商店中 */
  public static final int INSTORE = 0x00002000;
  
  /** 不能装备 */
  public static final int NOEQUIP = 0x00004000;
  
  /** 已命名（稀有/手工物品） */
  public static final int NAMED = 0x00008000;
  
  /** 是耳朵（PvP 击杀） */
  public static final int ISEAR = 0x00010000;
  
  /** 新手物品 */
  public static final int STARTERITEM = 0x00020000;
  
  /** 未知标志 */
  public static final int UNK_0x40000 = 0x00040000;
  
  /** 未知标志 */
  public static final int UNK_0x80000 = 0x00080000;
  
  /** 简易物品（无额外数据） */
  public static final int COMPACTSAVE = 0x00200000;
  
  /** 无穷堆栈（幻化之刃等） */
  public static final int ETHEREAL = 0x00400000;
  
  /** 物品被保存 */
  public static final int JUSTSAVED = 0x00800000;
  
  /** 个人化物品 */
  public static final int PERSONALIZED = 0x01000000;
  
  /** 未知标志 */
  public static final int UNK_0x2000000 = 0x02000000;
  
  /** 符文之语物品 */
  public static final int RUNEWORD = 0x04000000;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查是否有指定标志
   * 
   * @param flags 标志集合
   * @param flag 要检查的标志
   * @return true 如果有标志
   */
  public static boolean hasFlag(int flags, int flag) {
    return (flags & flag) != 0;
  }

  /**
   * 设置标志
   * 
   * @param flags 原始标志
   * @param flag 要设置的标志
   * @return 新的标志值
   */
  public static int setFlag(int flags, int flag) {
    return flags | flag;
  }

  /**
   * 清除标志
   * 
   * @param flags 原始标志
   * @param flag 要清除的标志
   * @return 新的标志值
   */
  public static int clearFlag(int flags, int flag) {
    return flags & ~flag;
  }

  /**
   * 切换标志
   * 
   * @param flags 原始标志
   * @param flag 要切换的标志
   * @param set true 设置，false 清除
   * @return 新的标志值
   */
  public static int toggleFlag(int flags, int flag, boolean set) {
    return set ? setFlag(flags, flag) : clearFlag(flags, flag);
  }

  /**
   * 检查物品是否已鉴定
   * 
   * @param flags 物品标志
   * @return true 如果已鉴定
   */
  public static boolean isIdentified(int flags) {
    return hasFlag(flags, IDENTIFIED);
  }

  /**
   * 检查物品是否已损坏
   * 
   * @param flags 物品标志
   * @return true 如果已损坏
   */
  public static boolean isBroken(int flags) {
    return hasFlag(flags, BROKEN);
  }

  /**
   * 检查物品是否有插槽
   * 
   * @param flags 物品标志
   * @return true 如果有插槽
   */
  public static boolean isSocketed(int flags) {
    return hasFlag(flags, SOCKETED);
  }

  /**
   * 检查物品是否是幻化
   * 
   * @param flags 物品标志
   * @return true 如果是幻化
   */
  public static boolean isEthereal(int flags) {
    return hasFlag(flags, ETHEREAL);
  }

  /**
   * 检查物品是否是符文之语
   * 
   * @param flags 物品标志
   * @return true 如果是符文之语
   */
  public static boolean isRuneword(int flags) {
    return hasFlag(flags, RUNEWORD);
  }

  /**
   * 检查物品是否是新物品
   * 
   * @param flags 物品标志
   * @return true 如果是新物品
   */
  public static boolean isNew(int flags) {
    return hasFlag(flags, NEWITEM);
  }

  /**
   * 检查物品是否已个人化
   * 
   * @param flags 物品标志
   * @return true 如果已个人化
   */
  public static boolean isPersonalized(int flags) {
    return hasFlag(flags, PERSONALIZED);
  }
}
