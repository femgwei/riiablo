package com.riiablo.engine.server.missile;

/**
 * 投射物标志位 - 基于 D2MOD Missiles.h 移植
 * 
 * <p>定义投射物的各种行为标志。
 * 
 * <p>参考：D2MOD/source/D2Game/src/MISSILES/Missiles.h
 * 
 * @author riiablo team
 */
public final class MissileFlags {
  private MissileFlags() {} // 不可实例化

  //==========================================================================
  // 投射物标志
  //==========================================================================

  /** 可以穿透目标 */
  public static final int PIERCE = 1 << 0;

  /** 可以爆炸 */
  public static final int EXPLODE = 1 << 1;

  /** 是近战投射物 */
  public static final int MELEE = 1 << 2;

  /** 会追踪目标 */
  public static final int HOMING = 1 << 3;

  /** 可以被格挡 */
  public static final int BLOCKABLE = 1 << 4;

  /** 造成物理伤害 */
  public static final int PHYSICAL = 1 << 5;

  /** 造成魔法伤害 */
  public static final int MAGICAL = 1 << 6;

  /** 造成火焰伤害 */
  public static final int FIRE = 1 << 7;

  /** 造成冰冷伤害 */
  public static final int COLD = 1 << 8;

  /** 造成闪电伤害 */
  public static final int LIGHTNING = 1 << 9;

  /** 造成毒素伤害 */
  public static final int POISON = 1 << 10;

  /** 只对怪物有效 */
  public static final int MONSTER_ONLY = 1 << 11;

  /** 只对玩家有效 */
  public static final int PLAYER_ONLY = 1 << 12;

  /** 不会触发命中事件 */
  public static final int NO_HIT_EVENT = 1 << 13;

  /** 可以击退 */
  public static final int KNOCKBACK = 1 << 14;

  /** 软追踪（不会立即转向） */
  public static final int SOFT_HOMING = 1 << 15;

  /** 会在地面留下效果 */
  public static final int LEAVES_TRAIL = 1 << 16;

  /** 可以被躲避 */
  public static final int CAN_EVADE = 1 << 17;

  /** 不显示 */
  public static final int INVISIBLE = 1 << 18;

  /** 是范围效果 */
  public static final int AOE = 1 << 19;

  /** 会分裂 */
  public static final int SPLITS = 1 << 20;

  /** 是陷阱 */
  public static final int TRAP = 1 << 21;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查是否有指定标志
   */
  public static boolean hasFlag(int flags, int flag) {
    return (flags & flag) != 0;
  }

  /**
   * 添加标志
   */
  public static int addFlag(int flags, int flag) {
    return flags | flag;
  }

  /**
   * 移除标志
   */
  public static int removeFlag(int flags, int flag) {
    return flags & ~flag;
  }

  /**
   * 检查是否是元素投射物
   */
  public static boolean isElemental(int flags) {
    return hasFlag(flags, FIRE | COLD | LIGHTNING | POISON);
  }

  /**
   * 获取伤害类型描述
   */
  public static String getDamageTypeString(int flags) {
    if (hasFlag(flags, FIRE)) return "Fire";
    if (hasFlag(flags, COLD)) return "Cold";
    if (hasFlag(flags, LIGHTNING)) return "Lightning";
    if (hasFlag(flags, POISON)) return "Poison";
    if (hasFlag(flags, MAGICAL)) return "Magic";
    if (hasFlag(flags, PHYSICAL)) return "Physical";
    return "None";
  }
}
