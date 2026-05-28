package com.riiablo.engine.server.monster;

/**
 * 怪物标志位 - 基于 D2MOD MonStatsTxt 标志移植
 * 
 * <p>定义了怪物的各种属性标志，对应 monstats.txt 中的标志列。
 * 
 * <p>参考：D2MOD/source/D2Common/include/D2DataTbls.h
 * 
 * @author riiablo team
 */
public final class MonsterFlags {
  private MonsterFlags() {} // 不可实例化

  //==========================================================================
  // 怪物属性标志（MonStats.txt 的 flags）
  //==========================================================================

  /** 是否是近战怪物 */
  public static final int MELEE = 0x00000001;
  /** 是否是远程怪物 */
  public static final int RANGED = 0x00000002;
  /** 不使用路径寻路 */
  public static final int NOPATH = 0x00000004;
  /** 是否是 NPC */
  public static final int NPC = 0x00000008;
  /** 是否是恶魔 */
  public static final int DEMON = 0x00000010;
  /** 是否是亡灵 */
  public static final int UNDEAD = 0x00000020;
  /** 可被击飞 */
  public static final int FLYING = 0x00000040;
  /** 是否是 Boss */
  public static final int BOSS = 0x00000080;
  /** 可以行走 */
  public static final int CANWALK = 0x00000100;
  /** 可以奔跑 */
  public static final int CANRUN = 0x00000200;
  /** 可以被吸引 */
  public static final int CANATTRACT = 0x00000400;
  /** 可以被复活 */
  public static final int CANRAISE = 0x00000800;
  /** 无比例调整 */
  public static final int NORATIO = 0x00001000;
  /** 是否是小型怪物 */
  public static final int SMALL = 0x00002000;
  /** 是否是大型怪物 */
  public static final int LARGE = 0x00004000;
  /** 是否可以被敲开 */
  public static final int OPENDOORS = 0x00008000;
  /** 是否是精英怪物 */
  public static final int CHAMPION = 0x00010000;
  /** 是否是唯一怪物 */
  public static final int UNIQUE = 0x00020000;
  /** 不显示名字 */
  public static final int NONAME = 0x00040000;
  /** 不显示血条 */
  public static final int NOLIFEBAR = 0x00080000;
  /** 不能使用技能 */
  public static final int NOSKILLS = 0x00100000;
  /** 可以被石化 */
  public static final int CANSTONE = 0x00200000;
  /** 免疫诅咒 */
  public static final int CURSEIMMUNE = 0x00400000;
  /** 免疫冰冻 */
  public static final int FREEZEIMMUNE = 0x00800000;
  /** 是否是召唤物 */
  public static final int SUMMON = 0x01000000;
  /** 是否是宠物 */
  public static final int PET = 0x02000000;
  /** 是否是暗影 */
  public static final int SHADOW = 0x04000000;
  /** 不需要召唤者 */
  public static final int NOSUMMONER = 0x08000000;
  /** 可以被困住 */
  public static final int CANTRAP = 0x10000000;

  //==========================================================================
  // 召唤者标志（SummonerFlag）
  //==========================================================================

  /** 已被复活 */
  public static final int SUMMONER_RAISED = 0x0001;
  /** 被皈依 */
  public static final int SUMMONER_CONVERTED = 0x0002;
  /** 正在逃跑 */
  public static final int SUMMONER_FLEEING = 0x0004;
  /** 是小boss */
  public static final int SUMMONER_MINION = 0x0008;
  /** 可以被反复复活 */
  public static final int SUMMONER_RAISEMULTIPLE = 0x0010;
  /** 生成爆炸效果 */
  public static final int SUMMONER_SPAWNER = 0x0020;

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
   * 检查是否是亡灵
   * 
   * @param flags 怪物标志
   * @return true 如果是亡灵
   */
  public static boolean isUndead(int flags) {
    return hasFlag(flags, UNDEAD);
  }

  /**
   * 检查是否是恶魔
   * 
   * @param flags 怪物标志
   * @return true 如果是恶魔
   */
  public static boolean isDemon(int flags) {
    return hasFlag(flags, DEMON);
  }

  /**
   * 检查是否是 Boss
   * 
   * @param flags 怪物标志
   * @return true 如果是 Boss
   */
  public static boolean isBoss(int flags) {
    return hasFlag(flags, BOSS);
  }

  /**
   * 检查是否是 NPC
   * 
   * @param flags 怪物标志
   * @return true 如果是 NPC
   */
  public static boolean isNpc(int flags) {
    return hasFlag(flags, NPC);
  }

  /**
   * 检查是否免疫诅咒
   * 
   * @param flags 怪物标志
   * @return true 如果免疫诅咒
   */
  public static boolean isCurseImmune(int flags) {
    return hasFlag(flags, CURSEIMMUNE);
  }

  /**
   * 检查是否免疫冰冻
   * 
   * @param flags 怪物标志
   * @return true 如果免疫冰冻
   */
  public static boolean isFreezeImmune(int flags) {
    return hasFlag(flags, FREEZEIMMUNE);
  }
}
