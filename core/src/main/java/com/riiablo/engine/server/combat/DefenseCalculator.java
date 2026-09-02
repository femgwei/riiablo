package com.riiablo.engine.server.combat;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 防御计算器 - 基于 D2MOD SUnitDmg.cpp 移植
 * 
 * <p>该类实现了暗黑破坏神2的完整防御计算公式，包括：
 * <ul>
 *   <li>格挡计算（盾牌格挡、武器格挡）</li>
 *   <li>闪避计算（亚马逊被动：Dodge、Avoid、Evade）</li>
 *   <li>伤害减免计算（物理减免、魔法减免）</li>
 *   <li>吸收计算（火焰吸收、闪电吸收等）</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/UNIT/SUnitDmg.cpp
 * 
 * @author riiablo team
 */
public class DefenseCalculator {
  private static final Logger log = LogManager.getLogger(DefenseCalculator.class);

  /** 单例实例 */
  public static final DefenseCalculator INSTANCE = new DefenseCalculator();

  private DefenseCalculator() {}

  //==========================================================================
  // 常量
  //==========================================================================

  /** 最大格挡率 */
  public static final int MAX_BLOCK_CHANCE = 75;

  /** 最大闪避率 */
  public static final int MAX_DODGE_CHANCE = 75;

  /** 格挡基础敏捷需求 */
  public static final int BLOCK_BASE_DEX = 15;

  /** PvP 伤害减免系数 */
  public static final int PVP_DAMAGE_DIVISOR = 6;

  //==========================================================================
  // 攻击类型
  //==========================================================================

  /** 近战攻击 */
  public static final int ATTACK_MELEE = 0;
  /** 远程攻击 */
  public static final int ATTACK_RANGED = 1;
  /** 魔法攻击 */
  public static final int ATTACK_MAGIC = 2;

  //==========================================================================
  // 防御结果
  //==========================================================================

  /** 防御结果：无防御 */
  public static final int DEFENSE_NONE = 0;
  /** 防御结果：格挡 */
  public static final int DEFENSE_BLOCK = 1;
  /** 防御结果：闪避（近战时站立） */
  public static final int DEFENSE_DODGE = 2;
  /** 防御结果：躲避（远程攻击） */
  public static final int DEFENSE_AVOID = 3;
  /** 防御结果：闪避（移动时） */
  public static final int DEFENSE_EVADE = 4;
  /** 防御结果：武器格挡（刺客） */
  public static final int DEFENSE_WEAPON_BLOCK = 5;

  //==========================================================================
  // 核心计算方法
  //==========================================================================

  /**
   * 检查是否触发任何防御机制
   * 
   * <p>防御判定顺序（来自 D2MOD）：
   * <ol>
   *   <li>格挡（需要装备盾牌且不在跑步/行走）</li>
   *   <li>武器格挡（刺客武器格挡技能）</li>
   *   <li>闪避/躲避/躲闪（亚马逊被动技能）</li>
   * </ol>
   * 
   * @param defender 防御者属性
   * @param attackType 攻击类型
   * @param isRunning 是否在跑步
   * @param isWalking 是否在行走
   * @param hasShield 是否装备盾牌
   * @param shieldBlockChance 盾牌格挡率
   * @return 防御结果类型
   */
  public int checkDefense(Attributes defender, int attackType, 
      boolean isRunning, boolean isWalking, boolean hasShield, int shieldBlockChance) {

    int level = getInt(defender, Stat.level, 1);
    int dexterity = getInt(defender, Stat.dexterity, 0);

    // 1. 盾牌格挡检查
    // 跑步和行走时不能格挡
    if (hasShield && !isRunning && !isWalking && shieldBlockChance > 0) {
      if (rollBlock(shieldBlockChance, dexterity, level)) {
        log.debug("Shield block successful");
        return DEFENSE_BLOCK;
      }
    }

    // 2. 武器格挡检查（刺客技能）
    int weaponBlockChance = getInt(defender, Stat.passive_weaponblock, 0);
    if (weaponBlockChance > 0 && !isRunning) {
      if (MathUtils.random(99) < Math.min(weaponBlockChance, MAX_BLOCK_CHANCE)) {
        log.debug("Weapon block successful: {}%", weaponBlockChance);
        return DEFENSE_WEAPON_BLOCK;
      }
    }

    // 3. 亚马逊闪避技能检查
    // 3.1 Dodge - 近战攻击时站立
    if (attackType == ATTACK_MELEE && !isRunning && !isWalking) {
      int dodgeChance = getInt(defender, Stat.passive_dodge, 0);
      if (dodgeChance > 0 && MathUtils.random(99) < Math.min(dodgeChance, MAX_DODGE_CHANCE)) {
        log.debug("Dodge successful: {}%", dodgeChance);
        return DEFENSE_DODGE;
      }
    }

    // 3.2 Avoid - 远程攻击时站立
    if (attackType == ATTACK_RANGED && !isRunning && !isWalking) {
      int avoidChance = getInt(defender, Stat.passive_avoid, 0);
      if (avoidChance > 0 && MathUtils.random(99) < Math.min(avoidChance, MAX_DODGE_CHANCE)) {
        log.debug("Avoid successful: {}%", avoidChance);
        return DEFENSE_AVOID;
      }
    }

    // 3.3 Evade - 任何攻击时移动中
    if (isRunning || isWalking) {
      int evadeChance = getInt(defender, Stat.passive_evade, 0);
      if (evadeChance > 0 && MathUtils.random(99) < Math.min(evadeChance, MAX_DODGE_CHANCE)) {
        log.debug("Evade successful: {}%", evadeChance);
        return DEFENSE_EVADE;
      }
    }

    return DEFENSE_NONE;
  }

  /**
   * Evaluates only passive/weapon defenses for a pre-built combat context.
   * CombatSystem uses this overload so it can preserve native attack type and
   * movement information without rebuilding an Attributes object.
   */
  public int checkPassiveDefense(int attackType, boolean isMoving,
      int dodgeChance, int avoidChance, int evadeChance, int weaponBlockChance) {
    if (weaponBlockChance > 0 && rollPassive(weaponBlockChance)) {
      log.debug("Weapon block successful: {}%", weaponBlockChance);
      return DEFENSE_WEAPON_BLOCK;
    }
    if (isMoving) {
      if (evadeChance > 0 && rollPassive(evadeChance)) {
        log.debug("Evade successful: {}%", evadeChance);
        return DEFENSE_EVADE;
      }
      return DEFENSE_NONE;
    }
    if (attackType == ATTACK_MELEE && dodgeChance > 0 && rollPassive(dodgeChance)) {
      log.debug("Dodge successful: {}%", dodgeChance);
      return DEFENSE_DODGE;
    }
    if (attackType == ATTACK_RANGED && avoidChance > 0 && rollPassive(avoidChance)) {
      log.debug("Avoid successful: {}%", avoidChance);
      return DEFENSE_AVOID;
    }
    return DEFENSE_NONE;
  }

  private boolean rollPassive(int chance) {
    if (chance >= 100) return true;
    return MathUtils.random(99) < Math.min(Math.max(chance, 0), MAX_DODGE_CHANCE);
  }

  /**
   * 计算格挡几率
   * 
   * <p>格挡公式（来自 D2MOD）：
   * <pre>
   * 格挡率 = 盾牌格挡% * (DEX - 15) / (等级 * 2)
   * </pre>
   * 
   * @param baseBlockChance 盾牌基础格挡率
   * @param dexterity 敏捷值
   * @param level 角色等级
   * @return 实际格挡几率
   */
  public int calculateBlockChance(int baseBlockChance, int dexterity, int level) {
    if (baseBlockChance <= 0) {
      return 0;
    }

    // D2MOD 格挡公式
    int effectiveBlock = baseBlockChance + (dexterity - BLOCK_BASE_DEX) / (Math.max(level, 1) * 2);
    return Math.max(0, Math.min(MAX_BLOCK_CHANCE, effectiveBlock));
  }

  /**
   * 判断是否格挡成功
   */
  public boolean rollBlock(int baseBlockChance, int dexterity, int level) {
    int effectiveBlock = calculateBlockChance(baseBlockChance, dexterity, level);
    if (effectiveBlock <= 0) {
      return false;
    }
    return MathUtils.random(99) < effectiveBlock;
  }

  //==========================================================================
  // 伤害减免计算
  //==========================================================================

  /**
   * 计算物理伤害减免
   * 
   * <p>物理减免来源：
   * <ul>
   *   <li>物理伤害减少% (damageresist)</li>
   *   <li>物理伤害减少固定值 (normal_damage_reduction)</li>
   *   <li>魔法伤害减少固定值 (magic_damage_reduction)</li>
   * </ul>
   * 
   * @param damage 原始物理伤害
   * @param defender 防御者属性
   * @return 减免后的伤害
   */
  public int applyPhysicalDamageReduction(int damage, Attributes defender) {
    if (damage <= 0) {
      return 0;
    }

    // 1. 物理伤害减少%
    int resistPercent = getInt(defender, Stat.damageresist, 0);
    // 物理抗性上限为 50%（普通怪物）或更高（BOSS）
    resistPercent = Math.min(resistPercent, 50);

    int reducedDamage = damage;
    if (resistPercent != 0) {
      reducedDamage = damage * (100 - resistPercent) / 100;
    }

    // 2. 物理伤害减少固定值
    int flatReduction = getInt(defender, Stat.normal_damage_reduction, 0);
    reducedDamage -= flatReduction;

    log.debug("Physical DR: damage={} -> {}, resist={}%, flat={}", 
        damage, Math.max(reducedDamage, 0), resistPercent, flatReduction);

    return Math.max(reducedDamage, 0);
  }

  /**
   * 计算魔法伤害减免
   * 
   * @param damage 原始魔法伤害
   * @param defender 防御者属性
   * @return 减免后的伤害
   */
  public int applyMagicDamageReduction(int damage, Attributes defender) {
    if (damage <= 0) {
      return 0;
    }

    // 魔法伤害减少固定值
    int flatReduction = getInt(defender, Stat.magic_damage_reduction, 0);
    int reducedDamage = damage - flatReduction;

    return Math.max(reducedDamage, 0);
  }

  //==========================================================================
  // 吸收计算
  //==========================================================================

  /**
   * 计算元素吸收
   * 
   * <p>吸收机制：
   * <ol>
   *   <li>先应用吸收%：伤害的一部分转为治疗</li>
   *   <li>再应用固定吸收：从伤害中减去固定值并治疗</li>
   * </ol>
   * 
   * @param damage 应用抗性后的伤害
   * @param absorbPercent 吸收百分比
   * @param absorbFlat 固定吸收值
   * @param healAmount 输出：治疗量数组（单元素数组）
   * @return 吸收后的伤害
   */
  public int applyAbsorption(int damage, int absorbPercent, int absorbFlat, int[] healAmount) {
    if (damage <= 0) {
      if (healAmount != null && healAmount.length > 0) {
        healAmount[0] = 0;
      }
      return 0;
    }

    int totalHeal = 0;
    int remainingDamage = damage;

    // 1. 吸收百分比
    if (absorbPercent > 0) {
      int absorbedPercent = damage * absorbPercent / 100;
      remainingDamage -= absorbedPercent;
      totalHeal += absorbedPercent;
    }

    // 2. 固定吸收
    if (absorbFlat > 0 && remainingDamage > 0) {
      int absorbedFlat = Math.min(absorbFlat, remainingDamage);
      remainingDamage -= absorbedFlat;
      totalHeal += absorbedFlat;
    }

    if (healAmount != null && healAmount.length > 0) {
      healAmount[0] = totalHeal;
    }

    return Math.max(remainingDamage, 0);
  }

  /**
   * 计算火焰吸收
   */
  public int applyFireAbsorption(int damage, Attributes defender, int[] healAmount) {
    int absorbPercent = getInt(defender, Stat.item_absorbfire_percent, 0);
    int absorbFlat = getInt(defender, Stat.item_absorbfire, 0);
    return applyAbsorption(damage, absorbPercent, absorbFlat, healAmount);
  }

  /**
   * 计算冰冷吸收
   */
  public int applyColdAbsorption(int damage, Attributes defender, int[] healAmount) {
    int absorbPercent = getInt(defender, Stat.item_absorbcold_percent, 0);
    int absorbFlat = getInt(defender, Stat.item_absorbcold, 0);
    return applyAbsorption(damage, absorbPercent, absorbFlat, healAmount);
  }

  /**
   * 计算闪电吸收
   */
  public int applyLightningAbsorption(int damage, Attributes defender, int[] healAmount) {
    int absorbPercent = getInt(defender, Stat.item_absorblight_percent, 0);
    int absorbFlat = getInt(defender, Stat.item_absorblight, 0);
    return applyAbsorption(damage, absorbPercent, absorbFlat, healAmount);
  }

  /**
   * 计算魔法吸收
   */
  public int applyMagicAbsorption(int damage, Attributes defender, int[] healAmount) {
    int absorbPercent = getInt(defender, Stat.item_absorbmagic_percent, 0);
    int absorbFlat = getInt(defender, Stat.item_absorbmagic, 0);
    return applyAbsorption(damage, absorbPercent, absorbFlat, healAmount);
  }

  //==========================================================================
  // PvP 伤害修正
  //==========================================================================

  /**
   * 应用 PvP 伤害修正
   * 
   * <p>PvP 中所有伤害除以 6（1.10 版本后）
   * 
   * @param damage 原始伤害
   * @return PvP 修正后的伤害
   */
  public int applyPvPDamageReduction(int damage) {
    if (damage <= 0) {
      return 0;
    }
    return damage / PVP_DAMAGE_DIVISOR;
  }

  //==========================================================================
  // 特殊防御效果
  //==========================================================================

  /**
   * 检查冰冻免疫
   * 
   * @param defender 防御者属性
   * @return true 如果免疫冰冻
   */
  public boolean isFreezingImmune(Attributes defender) {
    return getInt(defender, Stat.item_freeze, 0) > 0;
  }

  /**
   * 检查中毒长度减免
   * 
   * @param defender 防御者属性
   * @return 中毒长度减免百分比
   */
  public int getPoisonLengthReduction(Attributes defender) {
    return getInt(defender, Stat.item_poisonlengthresist, 0);
  }

  /**
   * 应用中毒长度减免
   * 
   * @param duration 原始毒素持续时间
   * @param defender 防御者属性
   * @return 减免后的持续时间
   */
  public int applyPoisonLengthReduction(int duration, Attributes defender) {
    if (duration <= 0) {
      return 0;
    }

    int reduction = getPoisonLengthReduction(defender);
    if (reduction >= 100) {
      return 0; // 完全免疫毒素持续时间
    }

    return duration * (100 - reduction) / 100;
  }

  //==========================================================================
  // 能量护盾
  //==========================================================================

  /**
   * 应用能量护盾
   * 
   * <p>能量护盾将一部分伤害转移到法力：
   * 伤害转移% 由技能等级决定
   * 转移的伤害以 2:1 的比例消耗法力
   * 
   * @param damage 原始伤害
   * @param absorbPercent 能量护盾吸收比例
   * @param currentMana 当前法力值
   * @param manaUsed 输出：消耗的法力量
   * @return 剩余伤害
   */
  public int applyEnergyShield(int damage, int absorbPercent, int currentMana, int[] manaUsed) {
    if (damage <= 0 || absorbPercent <= 0 || currentMana <= 0) {
      if (manaUsed != null && manaUsed.length > 0) {
        manaUsed[0] = 0;
      }
      return damage;
    }

    // 计算要吸收的伤害
    int absorbDamage = damage * absorbPercent / 100;

    // 计算需要的法力（2:1 比例）
    int manaNeeded = absorbDamage * 2;

    // 如果法力不足，只能吸收部分
    if (manaNeeded > currentMana) {
      manaNeeded = currentMana;
      absorbDamage = manaNeeded / 2;
    }

    if (manaUsed != null && manaUsed.length > 0) {
      manaUsed[0] = manaNeeded;
    }

    log.debug("Energy Shield: absorbed {} damage, used {} mana", absorbDamage, manaNeeded);

    return damage - absorbDamage;
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  private static int getInt(Attributes attrs, short stat, int defaultValue) {
    if (attrs == null) return defaultValue;
    StatRef ref = attrs.get(stat);
    return ref != null ? ref.asInt() : defaultValue;
  }
}
