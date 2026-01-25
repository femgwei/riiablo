package com.riiablo.engine.server.collision;

/**
 * 碰撞掩码常量 - 基于 D2MOO D2C_CollisionMaskFlags 移植
 * 
 * <p>定义了游戏中的各种碰撞类型标志。
 * 
 * <p>参考：D2MOO/source/D2Common/include/D2Collision.h
 * 
 * @author riiablo team
 */
public final class CollisionMask {
  private CollisionMask() {} // 不可实例化

  //==========================================================================
  // 基础碰撞标志
  //==========================================================================

  /** 无碰撞 */
  public static final int NONE = 0x0000;
  
  /** 墙壁碰撞 - 悬崖、黑色空间等，完全阻挡玩家 */
  public static final int WALL = 0x0001;
  
  /** 可见障碍 - 无法射击穿过的基于地块的障碍 */
  public static final int VISIBLE = 0x0002;
  
  /** 飞弹屏障 - 阻挡飞弹/飞行单位 */
  public static final int MISSILE_BARRIER = 0x0004;
  
  /** 禁止玩家 - 玩家无法进入 */
  public static final int NO_PLAYER = 0x0008;
  
  /** 预设地板 - 某些地板有此设置 */
  public static final int PRESET = 0x0010;
  
  /** 空白/无效 - 子格无效时返回 */
  public static final int BLANK = 0x0020;
  
  /** 飞弹 - 飞弹占用 */
  public static final int MISSILE = 0x0040;
  
  /** 玩家 - 玩家占用 */
  public static final int PLAYER = 0x0080;
  
  /** 水域 - 水面区域 */
  public static final int WATER = 0x00C0;
  
  /** 怪物 - 怪物占用 */
  public static final int MONSTER = 0x0100;
  
  /** 物品 - 地面物品占用 */
  public static final int ITEM = 0x0200;
  
  /** 物体 - 游戏物体占用 */
  public static final int OBJECT = 0x0400;
  
  /** 门 - 门类物体 */
  public static final int DOOR = 0x0800;
  
  /** 无路径 - 某些单位设置，但不总是 */
  public static final int NO_PATH = 0x1000;
  
  /** 宠物 - 可被攻击的宠物/召唤物 */
  public static final int PET = 0x2000;
  
  /** 未知标志 */
  public static final int FLAG_4000 = 0x4000;
  
  /** 尸体 - 死亡怪物和传送门 */
  public static final int CORPSE = 0x8000;

  //==========================================================================
  // 组合掩码
  //==========================================================================

  /** 所有掩码 */
  public static final int ALL = 0xFFFF;
  
  /** 无效区域掩码 */
  public static final int INVALID = BLANK | MISSILE_BARRIER | VISIBLE | WALL;
  
  /** 玩家路径掩码 - 阻挡玩家行走 */
  public static final int PLAYER_PATH = WALL | NO_PLAYER | OBJECT | DOOR | NO_PATH;
  
  /** 玩家飞行掩码 - 阻挡传送等 */
  public static final int PLAYER_FLYING = DOOR | MISSILE_BARRIER;
  
  /** 玩家旋风斩掩码 */
  public static final int PLAYER_WHIRLWIND = WALL | OBJECT | DOOR;
  
  /** 径向屏障掩码 */
  public static final int RADIAL_BARRIER = DOOR | MISSILE_BARRIER | WALL;
  
  /** 飞行单位掩码 */
  public static final int FLYING_UNIT = MISSILE_BARRIER | DOOR | NO_PATH;
  
  /** 可开门怪物掩码 */
  public static final int MONSTER_OPEN_DOORS = WALL | OBJECT | NO_PATH | PET;
  
  /** 怪物飞弹掩码 */
  public static final int MONSTER_MISSILE = MONSTER | WALL;
  
  /** 怪物路径掩码 */
  public static final int MONSTER_PATH = MONSTER_OPEN_DOORS | DOOR;
  
  /** 门阻挡可见掩码 */
  public static final int DOOR_BLOCK_VISIBLE = DOOR | MISSILE_BARRIER | VISIBLE;
  
  /** 阻挡门的单位 */
  public static final int BLOCKS_DOOR = PLAYER | MONSTER | CORPSE;
  
  /** 生成点掩码 - 不能生成单位的位置 */
  public static final int SPAWN = WALL | ITEM | OBJECT | DOOR | NO_PATH | PET;
  
  /** 放置掩码 */
  public static final int PLACEMENT = SPAWN | PRESET | MONSTER;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查是否有指定掩码
   * 
   * @param flags 当前标志
   * @param mask 要检查的掩码
   * @return true 如果有任何匹配
   */
  public static boolean hasAny(int flags, int mask) {
    return (flags & mask) != 0;
  }

  /**
   * 检查是否有所有指定掩码
   * 
   * @param flags 当前标志
   * @param mask 要检查的掩码
   * @return true 如果全部匹配
   */
  public static boolean hasAll(int flags, int mask) {
    return (flags & mask) == mask;
  }

  /**
   * 设置掩码
   * 
   * @param flags 原始标志
   * @param mask 要设置的掩码
   * @return 新的标志值
   */
  public static int set(int flags, int mask) {
    return flags | mask;
  }

  /**
   * 清除掩码
   * 
   * @param flags 原始标志
   * @param mask 要清除的掩码
   * @return 新的标志值
   */
  public static int clear(int flags, int mask) {
    return flags & ~mask;
  }

  /**
   * 检查是否是墙壁
   * 
   * @param flags 标志
   * @return true 如果是墙壁
   */
  public static boolean isWall(int flags) {
    return hasAny(flags, WALL);
  }

  /**
   * 检查是否是无效区域
   * 
   * @param flags 标志
   * @return true 如果是无效区域
   */
  public static boolean isInvalid(int flags) {
    return hasAny(flags, INVALID);
  }

  /**
   * 检查是否可以行走
   * 
   * @param flags 标志
   * @return true 如果可以行走
   */
  public static boolean isWalkable(int flags) {
    return !hasAny(flags, PLAYER_PATH);
  }

  /**
   * 检查是否可以飞行穿过
   * 
   * @param flags 标志
   * @return true 如果可以飞行
   */
  public static boolean isFlyable(int flags) {
    return !hasAny(flags, PLAYER_FLYING);
  }

  /**
   * 检查是否可以生成单位
   * 
   * @param flags 标志
   * @return true 如果可以生成
   */
  public static boolean canSpawn(int flags) {
    return !hasAny(flags, SPAWN);
  }

  /**
   * 获取掩码的字符串表示
   * 
   * @param flags 标志
   * @return 掩码字符串
   */
  public static String toString(int flags) {
    if (flags == NONE) return "NONE";
    
    StringBuilder sb = new StringBuilder();
    if (hasAny(flags, WALL)) sb.append("WALL|");
    if (hasAny(flags, VISIBLE)) sb.append("VISIBLE|");
    if (hasAny(flags, MISSILE_BARRIER)) sb.append("MISSILE_BARRIER|");
    if (hasAny(flags, NO_PLAYER)) sb.append("NO_PLAYER|");
    if (hasAny(flags, PRESET)) sb.append("PRESET|");
    if (hasAny(flags, BLANK)) sb.append("BLANK|");
    if (hasAny(flags, MISSILE)) sb.append("MISSILE|");
    if (hasAny(flags, PLAYER)) sb.append("PLAYER|");
    if (hasAny(flags, MONSTER)) sb.append("MONSTER|");
    if (hasAny(flags, ITEM)) sb.append("ITEM|");
    if (hasAny(flags, OBJECT)) sb.append("OBJECT|");
    if (hasAny(flags, DOOR)) sb.append("DOOR|");
    if (hasAny(flags, NO_PATH)) sb.append("NO_PATH|");
    if (hasAny(flags, PET)) sb.append("PET|");
    if (hasAny(flags, CORPSE)) sb.append("CORPSE|");
    
    if (sb.length() > 0) {
      sb.setLength(sb.length() - 1); // 移除最后的 |
    }
    return sb.toString();
  }
}
