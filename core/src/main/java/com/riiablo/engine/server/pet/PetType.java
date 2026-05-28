package com.riiablo.engine.server.pet;

/**
 * 宠物类型枚举 - 基于 D2MOD PlayerPets.h 移植
 * 
 * <p>定义了游戏中所有召唤物/宠物的类型。
 * 
 * <p>参考：D2MOD/source/D2Game/src/PLAYER/PlayerPets.h
 * 
 * @author riiablo team
 */
public final class PetType {
  private PetType() {} // 不可实例化

  //==========================================================================
  // 无效类型
  //==========================================================================

  public static final int NONE = -1;

  //==========================================================================
  // 死灵法师召唤物
  //==========================================================================

  /** 骷髅战士 */
  public static final int SKELETON = 0;
  /** 骷髅法师 */
  public static final int SKELETON_MAGE = 1;
  /** 泥土石魔 */
  public static final int CLAY_GOLEM = 2;
  /** 血肉石魔 */
  public static final int BLOOD_GOLEM = 3;
  /** 钢铁石魔 */
  public static final int IRON_GOLEM = 4;
  /** 火焰石魔 */
  public static final int FIRE_GOLEM = 5;
  /** 复活的怪物 */
  public static final int REVIVE = 6;

  //==========================================================================
  // 德鲁伊召唤物
  //==========================================================================

  /** 乌鸦 */
  public static final int RAVEN = 10;
  /** 狼 */
  public static final int SPIRIT_WOLF = 11;
  /** 凶狼 */
  public static final int DIRE_WOLF = 12;
  /** 灰熊 */
  public static final int GRIZZLY = 13;
  /** 藤蔓 */
  public static final int VINE = 14;
  /** 橡木圣灵 */
  public static final int OAK_SAGE = 15;
  /** 荆棘之灵 */
  public static final int HEART_OF_WOLVERINE = 16;
  /** 狂热之灵 */
  public static final int SPIRIT_OF_BARBS = 17;

  //==========================================================================
  // 亚马逊召唤物
  //==========================================================================

  /** 女武神 */
  public static final int VALKYRIE = 20;
  /** 诱饵 */
  public static final int DECOY = 21;

  //==========================================================================
  // 刺客召唤物
  //==========================================================================

  /** 影子战士 */
  public static final int SHADOW_WARRIOR = 30;
  /** 影子大师 */
  public static final int SHADOW_MASTER = 31;

  //==========================================================================
  // 陷阱
  //==========================================================================

  /** 闪电哨兵 */
  public static final int LIGHTNING_SENTRY = 40;
  /** 死亡哨兵 */
  public static final int DEATH_SENTRY = 41;
  /** 火焰冲击哨兵 */
  public static final int WAKE_OF_FIRE = 42;
  /** 地狱之火哨兵 */
  public static final int WAKE_OF_INFERNO = 43;
  /** 电荷哨兵 */
  public static final int CHARGED_BOLT_SENTRY = 44;
  /** 刀刃哨戒 */
  public static final int BLADE_SENTINEL = 45;

  //==========================================================================
  // 法师召唤物
  //==========================================================================

  /** 九头蛇 */
  public static final int HYDRA = 50;

  //==========================================================================
  // 佣兵/追随者
  //==========================================================================

  /** 第一幕 - 弓箭手 */
  public static final int MERC_ACT1_ROGUE = 100;
  /** 第二幕 - 沙漠佣兵 */
  public static final int MERC_ACT2_DESERT = 101;
  /** 第三幕 - 铁狼 */
  public static final int MERC_ACT3_IRON_WOLF = 102;
  /** 第五幕 - 野蛮人 */
  public static final int MERC_ACT5_BARBARIAN = 103;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 是否是石魔类型
   */
  public static boolean isGolem(int type) {
    return type >= CLAY_GOLEM && type <= FIRE_GOLEM;
  }

  /**
   * 是否是骷髅类型
   */
  public static boolean isSkeleton(int type) {
    return type == SKELETON || type == SKELETON_MAGE;
  }

  /**
   * 是否是德鲁伊召唤物
   */
  public static boolean isDruidSummon(int type) {
    return type >= RAVEN && type <= SPIRIT_OF_BARBS;
  }

  /**
   * 是否是陷阱
   */
  public static boolean isTrap(int type) {
    return type >= LIGHTNING_SENTRY && type <= BLADE_SENTINEL;
  }

  /**
   * 是否是佣兵
   */
  public static boolean isMercenary(int type) {
    return type >= MERC_ACT1_ROGUE && type <= MERC_ACT5_BARBARIAN;
  }

  /**
   * 是否是灵魂类召唤物（德鲁伊）
   */
  public static boolean isSpirit(int type) {
    return type == OAK_SAGE || type == HEART_OF_WOLVERINE || type == SPIRIT_OF_BARBS;
  }

  /**
   * 获取类型名称
   */
  public static String getName(int type) {
    switch (type) {
      case SKELETON: return "Skeleton";
      case SKELETON_MAGE: return "Skeleton Mage";
      case CLAY_GOLEM: return "Clay Golem";
      case BLOOD_GOLEM: return "Blood Golem";
      case IRON_GOLEM: return "Iron Golem";
      case FIRE_GOLEM: return "Fire Golem";
      case REVIVE: return "Revive";
      case RAVEN: return "Raven";
      case SPIRIT_WOLF: return "Spirit Wolf";
      case DIRE_WOLF: return "Dire Wolf";
      case GRIZZLY: return "Grizzly";
      case VINE: return "Vine";
      case OAK_SAGE: return "Oak Sage";
      case HEART_OF_WOLVERINE: return "Heart of Wolverine";
      case SPIRIT_OF_BARBS: return "Spirit of Barbs";
      case VALKYRIE: return "Valkyrie";
      case DECOY: return "Decoy";
      case SHADOW_WARRIOR: return "Shadow Warrior";
      case SHADOW_MASTER: return "Shadow Master";
      case LIGHTNING_SENTRY: return "Lightning Sentry";
      case DEATH_SENTRY: return "Death Sentry";
      case WAKE_OF_FIRE: return "Wake of Fire";
      case WAKE_OF_INFERNO: return "Wake of Inferno";
      case CHARGED_BOLT_SENTRY: return "Charged Bolt Sentry";
      case BLADE_SENTINEL: return "Blade Sentinel";
      case HYDRA: return "Hydra";
      case MERC_ACT1_ROGUE: return "Act 1 Mercenary";
      case MERC_ACT2_DESERT: return "Act 2 Mercenary";
      case MERC_ACT3_IRON_WOLF: return "Act 3 Mercenary";
      case MERC_ACT5_BARBARIAN: return "Act 5 Mercenary";
      default: return "Unknown";
    }
  }
}
