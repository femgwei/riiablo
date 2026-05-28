package com.riiablo.engine.server.monster;

/**
 * 怪物词缀 - 基于 D2MOD MonsterUnique.h 移植
 * 
 * <p>定义了暗金怪物可能拥有的特殊能力词缀。
 * 
 * <p>参考：D2MOD/source/D2Game/src/MONSTER/MonsterUnique.h
 * 
 * @author riiablo team
 */
public final class MonsterAffix {
  private MonsterAffix() {} // 不可实例化

  //==========================================================================
  // 词缀类型（位标志）
  //==========================================================================

  /** 无词缀 */
  public static final long NONE = 0;

  /** 额外强壮 - 生命增加 */
  public static final long EXTRA_STRONG = 1L << 0;

  /** 额外快速 - 移动和攻击速度增加 */
  public static final long EXTRA_FAST = 1L << 1;

  /** 诅咒 - 攻击附带诅咒效果 */
  public static final long CURSED = 1L << 2;

  /** 魔法抗性 - 魔法抗性提升 */
  public static final long MAGIC_RESISTANT = 1L << 3;

  /** 火焰强化 - 火焰伤害和抗性 */
  public static final long FIRE_ENCHANTED = 1L << 4;

  /** 冰冷强化 - 冰冷伤害和抗性 */
  public static final long COLD_ENCHANTED = 1L << 5;

  /** 闪电强化 - 闪电伤害和抗性 */
  public static final long LIGHTNING_ENCHANTED = 1L << 6;

  /** 毒素强化 - 毒素伤害和抗性 */
  public static final long POISON_ENCHANTED = 1L << 7;

  /** 法力燃烧 - 攻击消耗目标法力 */
  public static final long MANA_BURN = 1L << 8;

  /** 远程目标 - 远程攻击 */
  public static final long TELEPORTATION = 1L << 9;

  /** 幽灵 - 物理免疫 */
  public static final long SPECTRAL_HIT = 1L << 10;

  /** 石皮 - 物理抗性大幅提升 */
  public static final long STONE_SKIN = 1L << 11;

  /** 多重射击 - 发射多个投射物 */
  public static final long MULTISHOT = 1L << 12;

  /** 光环强化 - 带有光环 */
  public static final long AURA_ENCHANTED = 1L << 13;

  /** 召唤 - 可以召唤随从 */
  public static final long FANATIC = 1L << 14;

  /** 死后爆炸 - 死亡时造成范围伤害 */
  public static final long CORPSE_EXPLOSION = 1L << 15;

  /** 火焰免疫 */
  public static final long FIRE_IMMUNE = 1L << 16;

  /** 冰冷免疫 */
  public static final long COLD_IMMUNE = 1L << 17;

  /** 闪电免疫 */
  public static final long LIGHTNING_IMMUNE = 1L << 18;

  /** 毒素免疫 */
  public static final long POISON_IMMUNE = 1L << 19;

  /** 物理免疫 */
  public static final long PHYSICAL_IMMUNE = 1L << 20;

  /** 荆棘 - 反弹伤害 */
  public static final long THORNS = 1L << 21;

  /** 击退 - 攻击击退目标 */
  public static final long KNOCKBACK = 1L << 22;

  //==========================================================================
  // 普通难度可用词缀
  //==========================================================================

  public static final long[] NORMAL_AFFIXES = {
      EXTRA_STRONG,
      EXTRA_FAST,
      CURSED,
      FIRE_ENCHANTED,
      COLD_ENCHANTED,
      LIGHTNING_ENCHANTED,
  };

  //==========================================================================
  // 噩梦难度可用词缀
  //==========================================================================

  public static final long[] NIGHTMARE_AFFIXES = {
      EXTRA_STRONG,
      EXTRA_FAST,
      CURSED,
      MAGIC_RESISTANT,
      FIRE_ENCHANTED,
      COLD_ENCHANTED,
      LIGHTNING_ENCHANTED,
      POISON_ENCHANTED,
      MANA_BURN,
      TELEPORTATION,
      SPECTRAL_HIT,
      STONE_SKIN,
      MULTISHOT,
  };

  //==========================================================================
  // 地狱难度可用词缀
  //==========================================================================

  public static final long[] HELL_AFFIXES = {
      EXTRA_STRONG,
      EXTRA_FAST,
      CURSED,
      MAGIC_RESISTANT,
      FIRE_ENCHANTED,
      COLD_ENCHANTED,
      LIGHTNING_ENCHANTED,
      POISON_ENCHANTED,
      MANA_BURN,
      TELEPORTATION,
      SPECTRAL_HIT,
      STONE_SKIN,
      MULTISHOT,
      AURA_ENCHANTED,
      FANATIC,
      CORPSE_EXPLOSION,
      THORNS,
      KNOCKBACK,
  };

  //==========================================================================
  // 词缀数量
  //==========================================================================

  /** 普通难度暗金词缀数量 */
  public static final int NORMAL_AFFIX_COUNT = 2;

  /** 噩梦难度暗金词缀数量 */
  public static final int NIGHTMARE_AFFIX_COUNT = 3;

  /** 地狱难度暗金词缀数量 */
  public static final int HELL_AFFIX_COUNT = 4;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查是否有词缀
   */
  public static boolean hasAffix(long affixes, long affix) {
    return (affixes & affix) != 0;
  }

  /**
   * 添加词缀
   */
  public static long addAffix(long affixes, long affix) {
    return affixes | affix;
  }

  /**
   * 移除词缀
   */
  public static long removeAffix(long affixes, long affix) {
    return affixes & ~affix;
  }

  /**
   * 检查是否是免疫词缀
   */
  public static boolean isImmuneAffix(long affix) {
    return affix == FIRE_IMMUNE || affix == COLD_IMMUNE || 
           affix == LIGHTNING_IMMUNE || affix == POISON_IMMUNE ||
           affix == PHYSICAL_IMMUNE;
  }

  /**
   * 检查是否是元素强化词缀
   */
  public static boolean isElementalAffix(long affix) {
    return affix == FIRE_ENCHANTED || affix == COLD_ENCHANTED || 
           affix == LIGHTNING_ENCHANTED || affix == POISON_ENCHANTED;
  }

  /**
   * 获取词缀名称
   */
  public static String getName(long affix) {
    if (affix == NONE) return "None";
    if (affix == EXTRA_STRONG) return "Extra Strong";
    if (affix == EXTRA_FAST) return "Extra Fast";
    if (affix == CURSED) return "Cursed";
    if (affix == MAGIC_RESISTANT) return "Magic Resistant";
    if (affix == FIRE_ENCHANTED) return "Fire Enchanted";
    if (affix == COLD_ENCHANTED) return "Cold Enchanted";
    if (affix == LIGHTNING_ENCHANTED) return "Lightning Enchanted";
    if (affix == POISON_ENCHANTED) return "Poison Enchanted";
    if (affix == MANA_BURN) return "Mana Burn";
    if (affix == TELEPORTATION) return "Teleportation";
    if (affix == SPECTRAL_HIT) return "Spectral Hit";
    if (affix == STONE_SKIN) return "Stone Skin";
    if (affix == MULTISHOT) return "Multishot";
    if (affix == AURA_ENCHANTED) return "Aura Enchanted";
    if (affix == FANATIC) return "Fanatic";
    if (affix == CORPSE_EXPLOSION) return "Corpse Explosion";
    if (affix == FIRE_IMMUNE) return "Fire Immune";
    if (affix == COLD_IMMUNE) return "Cold Immune";
    if (affix == LIGHTNING_IMMUNE) return "Lightning Immune";
    if (affix == POISON_IMMUNE) return "Poison Immune";
    if (affix == PHYSICAL_IMMUNE) return "Physical Immune";
    if (affix == THORNS) return "Thorns";
    if (affix == KNOCKBACK) return "Knockback";
    return "Unknown";
  }

  /**
   * 将词缀组合转换为字符串
   */
  public static String affixesToString(long affixes) {
    if (affixes == NONE) {
      return "None";
    }

    StringBuilder sb = new StringBuilder();
    long[] allAffixes = {
        EXTRA_STRONG, EXTRA_FAST, CURSED, MAGIC_RESISTANT,
        FIRE_ENCHANTED, COLD_ENCHANTED, LIGHTNING_ENCHANTED, POISON_ENCHANTED,
        MANA_BURN, TELEPORTATION, SPECTRAL_HIT, STONE_SKIN,
        MULTISHOT, AURA_ENCHANTED, FANATIC, CORPSE_EXPLOSION,
        FIRE_IMMUNE, COLD_IMMUNE, LIGHTNING_IMMUNE, POISON_IMMUNE,
        PHYSICAL_IMMUNE, THORNS, KNOCKBACK
    };

    for (long affix : allAffixes) {
      if (hasAffix(affixes, affix)) {
        if (sb.length() > 0) {
          sb.append(", ");
        }
        sb.append(getName(affix));
      }
    }

    return sb.toString();
  }
}
