package com.riiablo.engine.server.combat;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 伤害计算器 - 基于 D2MOD SUnitDmg.cpp 移植
 * 
 * <p>该类实现了暗黑破坏神2的完整伤害计算公式，包括：
 * <ul>
 *   <li>物理伤害计算（含武器伤害、力量/敏捷加成）</li>
 *   <li>元素伤害计算（火焰、闪电、冰冷、魔法、毒素）</li>
 *   <li>暴击（致命一击、暴击）计算</li>
 *   <li>抗性和吸收计算</li>
 *   <li>生命/法力偷取</li>
 *   <li>特殊伤害类型（对恶魔/亡灵增伤）</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/UNIT/SUnitDmg.cpp
 * 
 * @author riiablo team
 */
public class DamageCalculator {
  private static final Logger log = LogManager.getLogger(DamageCalculator.class);

  /** 单例实例 */
  public static final DamageCalculator INSTANCE = new DamageCalculator();

  private DamageCalculator() {}

  //==========================================================================
  // 伤害结果标志位 (D2MOD: D2DamageResultFlags)
  //==========================================================================
  
  /** 成功命中 */
  public static final int RESULT_SUCCESSFUL_HIT = 0x00000001;
  /** 目标将死亡 */
  public static final int RESULT_WILL_DIE = 0x00000002;
  /** 触发受击动画 */
  public static final int RESULT_GET_HIT = 0x00000004;
  /** 击退效果 */
  public static final int RESULT_KNOCKBACK = 0x00000008;
  /** 格挡 */
  public static final int RESULT_BLOCK = 0x00000010;
  /** 闪避（亚马逊被动） */
  public static final int RESULT_DODGE = 0x00000080;
  /** 躲避远程攻击 */
  public static final int RESULT_AVOID = 0x00000100;
  /** 躲避近战攻击 */
  public static final int RESULT_EVADE = 0x00000200;
  /** 暴击/致命一击 */
  public static final int RESULT_CRITICAL_STRIKE = 0x00002000;
  /** 轻微命中（无硬直） */
  public static final int RESULT_SOFT_HIT = 0x00004000;
  /** 武器格挡（刺客） */
  public static final int RESULT_WEAPON_BLOCK = 0x00008000;

  //==========================================================================
  // 伤害命中标志位 (D2MOD: D2DamageHitFlags)
  //==========================================================================
  
  /** 生命偷取 */
  public static final int HIT_LIFE_DRAIN = 0x00000004;
  /** 法力偷取 */
  public static final int HIT_MANA_DRAIN = 0x00000008;
  /** 体力偷取 */
  public static final int HIT_STAMINA_DRAIN = 0x00000010;
  /** 无视亡灵抗性 */
  public static final int HIT_BYPASS_UNDEAD = 0x00000100;
  /** 无视恶魔抗性 */
  public static final int HIT_BYPASS_DEMON = 0x00000200;
  /** 无视野兽抗性 */
  public static final int HIT_BYPASS_BEASTS = 0x00000400;

  //==========================================================================
  // 伤害减免类型 (D2MOD: D2DamageReductionType)
  //==========================================================================
  
  /** 无减免 */
  public static final int REDUCTION_NONE = 0;
  /** 物理减免 */
  public static final int REDUCTION_PHYSICAL = 1;
  /** 魔法减免 */
  public static final int REDUCTION_MAGICAL = 2;

  //==========================================================================
  // 核心计算方法
  //==========================================================================

  /**
   * 计算攻击命中率
   * 
   * <p>命中公式（来自 D2MOD SUNITDMG_IsHitSuccessful）：
   * <pre>
   * 命中率 = 100 * 攻击准确率 / (攻击准确率 + 防御者防御值)
   * </pre>
   * 
   * @param attackerAR   攻击者命中率 (Attack Rating)
   * @param defenderDef  防御者防御值 (Defense)
   * @param attackerLvl  攻击者等级
   * @param defenderLvl  防御者等级
   * @return 命中概率 (0-100)
   */
  public int calculateHitChance(int attackerAR, int defenderDef, int attackerLvl, int defenderLvl) {
    // Reference D2MOD: SUNITDMG_IsHitSuccessful (lines 2510-2533)
    // Formula: nChanceToHit = Clamp(2 * attackerLevel * toHitFactor / (defenderLevel + attackerLevel), 5, 95)
    // where toHitFactor = 100 * nToHit / (nToHit + nDefense) if divisor != 0, else 100
    
    // Handle negative defense
    if (defenderDef < 0) {
      attackerAR -= defenderDef;
      defenderDef = 0;
    }
    
    // Handle negative AR
    if (attackerAR < 0) {
      defenderDef -= attackerAR;
      attackerAR = 0;
    }
    
    defenderDef = Math.max(defenderDef, 0);
    
    // Calculate toHitFactor
    int divisor = attackerAR + defenderDef;
    int toHitFactor = 100;
    if (divisor > 0) {
      toHitFactor = 100 * attackerAR / divisor;
    }
    
    // Final hit chance formula from D2MOD (avoid div-by-zero when both levels 0)
    int levelSum = Math.max(1, defenderLvl + attackerLvl);
    int hitChance = 2 * attackerLvl * toHitFactor / levelSum;
    
    // Clamp to 5%-95%
    return Math.max(5, Math.min(95, hitChance));
  }

  /**
   * 判断攻击是否命中
   * 
   * @param attackerAR   攻击者命中率
   * @param defenderDef  防御者防御值
   * @param attackerLvl  攻击者等级
   * @param defenderLvl  防御者等级
   * @return 是否命中
   */
  public boolean isHitSuccessful(int attackerAR, int defenderDef, int attackerLvl, int defenderLvl) {
    int hitChance = calculateHitChance(attackerAR, defenderDef, attackerLvl, defenderLvl);
    int roll = MathUtils.random(99);
    boolean hit = roll < hitChance;
    log.debug("Hit check: AR={}, Def={}, attLvl={}, defLvl={}, hitChance={}%, roll={}, result={}", 
        attackerAR, defenderDef, attackerLvl, defenderLvl, hitChance, roll, hit ? "HIT" : "MISS");
    return hit;
  }

  /**
   * 计算物理伤害
   * 
   * <p>伤害公式（来自 D2MOD SUNITDMG_ApplyDamageBonuses）：
   * <pre>
   * 1. 获取武器基础伤害 (minDmg ~ maxDmg)
   * 2. 计算伤害加成%：
   *    - 力量加成 = strBonus * STR / 100
   *    - 敏捷加成 = dexBonus * DEX / 100
   *    - 技能伤害加成%
   * 3. 最终伤害 = 基础伤害 * (100 + 伤害加成%) / 100
   * </pre>
   * 
   * @param minDamage      最小伤害
   * @param maxDamage      最大伤害
   * @param strength       力量值
   * @param dexterity      敏捷值
   * @param strBonus       力量加成系数（武器属性，默认100）
   * @param dexBonus       敏捷加成系数（武器属性，默认0）
   * @param damagePercent  额外伤害加成%
   * @param srcDamPercent  技能伤害系数（128=100%）
   * @return 计算后的物理伤害
   */
  public int calculatePhysicalDamage(int minDamage, int maxDamage, 
      int strength, int dexterity, int strBonus, int dexBonus, 
      int damagePercent, int srcDamPercent) {
    
    // D2MOD: SUNITDMG_ApplyDamageBonuses
    
    // 1. 确保最小/最大伤害有效
    if (minDamage < 1) minDamage = 1;
    if (maxDamage <= minDamage) maxDamage = minDamage + 1;
    
    // 2. 计算伤害加成百分比
    int totalDamagePercent = damagePercent;
    
    // 力量加成（近战武器通常有力量加成）
    if (strBonus > 0) {
      totalDamagePercent += strBonus * strength / 100;
    }
    
    // 敏捷加成（弓箭等远程武器）
    if (dexBonus > 0) {
      totalDamagePercent += dexBonus * dexterity / 100;
    }
    
    // 3. 伤害加成下限为-90%
    totalDamagePercent = Math.max(totalDamagePercent, -90);
    
    // 4. 计算最终伤害范围
    int finalMin = minDamage + minDamage * totalDamagePercent / 100;
    int finalMax = maxDamage + maxDamage * totalDamagePercent / 100;
    
    // 5. 在范围内随机
    int damage = finalMin;
    if (finalMax > finalMin) {
      damage += MathUtils.random(finalMax - finalMin);
    }
    
    // 6. 应用技能伤害系数（128 = 100%）
    if (srcDamPercent != 128 && srcDamPercent > 0) {
      damage = damage * srcDamPercent / 128;
    }
    
    log.debug("Physical damage calculation: base={}~{}, STR={}, DEX={}, bonus={}%, final={}", 
        minDamage, maxDamage, strength, dexterity, totalDamagePercent, damage);
    
    return Math.max(damage, 0);
  }

  /**
   * 计算元素伤害
   * 
   * <p>元素伤害计算（来自 D2MOD SUNITDMG_FillDamageValues）：
   * <pre>
   * 1. 获取元素基础伤害 (minDmg ~ maxDmg)
   * 2. 应用精通加成（女巫被动技能）
   * 3. 在范围内随机
   * </pre>
   * 
   * @param minDamage     最小元素伤害
   * @param maxDamage     最大元素伤害
   * @param masteryBonus  精通加成%（女巫被动技能）
   * @return 计算后的元素伤害
   */
  public int calculateElementalDamage(int minDamage, int maxDamage, int masteryBonus) {
    // D2MOD: SUNITDMG_RollDamageValueInRange
    
    if (maxDamage < 1) return 0;
    if (minDamage < 0) minDamage = 0;
    if (maxDamage <= minDamage) maxDamage = minDamage + 1;
    
    // 应用精通加成
    int finalMin = minDamage + minDamage * masteryBonus / 100;
    int finalMax = maxDamage + maxDamage * masteryBonus / 100;
    
    // 在范围内随机
    int damage = finalMin;
    if (finalMax > finalMin) {
      damage += MathUtils.random(finalMax - finalMin);
    }
    
    return Math.max(damage, 0);
  }

  /**
   * 应用抗性减免
   * 
   * <p>抗性公式（来自 D2MOD SUNITDMG_ApplyResistancesAndAbsorb）：
   * <pre>
   * 1. 实际抗性 = min(抗性, 最大抗性) - 穿透%
   * 2. 减免后伤害 = 伤害 * (100 - 实际抗性) / 100
   * 3. 吸收伤害 = min(减免后伤害, 吸收%)
   * 4. 最终伤害 = 减免后伤害 - 吸收伤害 - 固定吸收
   * </pre>
   * 
   * @param damage        原始伤害
   * @param resistance    抗性值
   * @param maxResist     最大抗性（通常75%，可被装备提升）
   * @param pierce        穿透%（负抗性效果）
   * @param absorbPercent 吸收%
   * @param absorbFlat    固定吸收值
   * @return 减免后的伤害
   */
  public int applyResistance(int damage, int resistance, int maxResist, 
      int pierce, int absorbPercent, int absorbFlat) {
    
    // D2MOD: SUNITDMG_ApplyResistancesAndAbsorb
    
    if (damage <= 0) return 0;
    
    // 1. 计算有效抗性（考虑最大抗性和穿透）
    int effectiveResist = Math.min(resistance, maxResist) - pierce;
    
    // 抗性可以是负数（增伤）
    // 但最低不能低于-100%
    effectiveResist = Math.max(effectiveResist, -100);
    
    // 2. 应用抗性减免
    int reducedDamage = damage * (100 - effectiveResist) / 100;
    
    // 3. 应用吸收%
    int absorbed = 0;
    if (absorbPercent > 0 && reducedDamage > 0) {
      absorbed = reducedDamage * absorbPercent / 100;
    }
    
    // 4. 应用固定吸收
    int finalDamage = reducedDamage - absorbed - absorbFlat;
    
    log.debug("Resistance calculation: baseDamage={}, resist={}%, pierce={}%, absorb={}%+{}, final={}", 
        damage, effectiveResist, pierce, absorbPercent, absorbFlat, finalDamage);
    
    return Math.max(finalDamage, 0);
  }

  /**
   * 判断是否暴击
   * 
   * <p>暴击判定优先级（来自 D2MOD SUNITDMG_FillDamageValues）：
   * <ol>
   *   <li>武器精通暴击（野蛮人被动）</li>
   *   <li>暴击技能（亚马逊Critical Strike）</li>
   *   <li>致命一击（装备属性Deadly Strike）</li>
   * </ol>
   * 
   * @param criticalStrikeChance  暴击几率%（技能）
   * @param deadlyStrikeChance    致命一击几率%（装备）
   * @param masteryChance         武器精通暴击几率%
   * @return 是否暴击
   */
  public boolean rollCriticalStrike(int criticalStrikeChance, int deadlyStrikeChance, int masteryChance) {
    // D2MOD: 按优先级检查暴击
    
    // 1. 武器精通暴击
    if (masteryChance > 0 && MathUtils.random(99) < masteryChance) {
      log.debug("Weapon mastery critical strike triggered: {}%", masteryChance);
      return true;
    }
    
    // 2. 技能暴击
    if (criticalStrikeChance > 0 && MathUtils.random(99) < criticalStrikeChance) {
      log.debug("Critical strike skill triggered: {}%", criticalStrikeChance);
      return true;
    }
    
    // 3. 装备致命一击
    if (deadlyStrikeChance > 0 && MathUtils.random(99) < deadlyStrikeChance) {
      log.debug("Deadly strike triggered: {}%", deadlyStrikeChance);
      return true;
    }
    
    return false;
  }

  /**
   * 计算生命偷取
   * 
   * <p>生命偷取公式（来自 D2MOD SUNITDMG_AddLeechedLife）：
   * <pre>
   * 偷取量 = 物理伤害 * 偷取% / 100
   * 实际偷取 = min(偷取量, 目标当前生命)
   * </pre>
   * 
   * @param physicalDamage  造成的物理伤害
   * @param leechPercent    生命偷取%
   * @param targetCurrentHp 目标当前生命
   * @return 实际偷取的生命值
   */
  public int calculateLifeLeech(int physicalDamage, int leechPercent, int targetCurrentHp) {
    if (physicalDamage <= 0 || leechPercent <= 0) return 0;
    
    int leechAmount = physicalDamage * leechPercent / 100;
    return Math.min(leechAmount, Math.max(targetCurrentHp, 0));
  }

  /**
   * 计算法力偷取
   * 
   * @param physicalDamage  造成的物理伤害
   * @param leechPercent    法力偷取%
   * @param targetCurrentMp 目标当前法力
   * @return 实际偷取的法力值
   */
  public int calculateManaLeech(int physicalDamage, int leechPercent, int targetCurrentMp) {
    if (physicalDamage <= 0 || leechPercent <= 0) return 0;
    
    int leechAmount = physicalDamage * leechPercent / 100;
    return Math.min(leechAmount, Math.max(targetCurrentMp, 0));
  }

  /**
   * 判断是否触发格挡
   * 
   * <p>格挡公式（来自 D2MOD SUNITDMG_ApplyBlockOrDodge）：
   * <pre>
   * 格挡率 = 盾牌格挡% + (DEX - 15) / (等级 * 2)
   * </pre>
   * 
   * @param baseBlockChance 盾牌基础格挡率
   * @param dexterity       敏捷值
   * @param level           角色等级
   * @return 是否格挡
   */
  public boolean rollBlock(int baseBlockChance, int dexterity, int level) {
    if (baseBlockChance <= 0) return false;
    
    // D2MOD: SUNITDMG_ApplyBlockOrDodge
    // 格挡公式：block% + (dex - 15) / (clvl * 2)
    int effectiveBlock = baseBlockChance + (dexterity - 15) / (Math.max(level, 1) * 2);
    effectiveBlock = Math.max(0, Math.min(75, effectiveBlock)); // 最大75%格挡
    
    boolean blocked = MathUtils.random(99) < effectiveBlock;
    if (blocked) {
      log.debug("Block successful: blockChance={}%", effectiveBlock);
    }
    return blocked;
  }

  /**
   * 计算对特殊怪物类型的伤害加成
   * 
   * <p>D2有针对特定怪物类型的伤害加成：
   * <ul>
   *   <li>对恶魔增伤（Damage to Demons）</li>
   *   <li>对亡灵增伤（Damage to Undead）</li>
   * </ul>
   * 
   * @param baseDamage       基础伤害
   * @param demonDamageBonus 对恶魔增伤%
   * @param undeadDamageBonus 对亡灵增伤%
   * @param isBluntWeapon    是否是钝器（对亡灵额外+50%）
   * @param isDemon          目标是否是恶魔
   * @param isUndead         目标是否是亡灵
   * @return 加成后的伤害
   */
  public int applyMonsterTypeDamageBonus(int baseDamage, 
      int demonDamageBonus, int undeadDamageBonus, boolean isBluntWeapon,
      boolean isDemon, boolean isUndead) {
    
    // D2MOD: SUNITDMG_FillDamageValues 中的 MONSTERS_IsDemon / MONSTERS_IsUndead 检查
    
    int bonusPercent = 0;
    
    if (isDemon && demonDamageBonus > 0) {
      bonusPercent += demonDamageBonus;
      log.debug("Demon damage bonus: +{}%", demonDamageBonus);
    }
    
    if (isUndead) {
      if (undeadDamageBonus > 0) {
        bonusPercent += undeadDamageBonus;
        log.debug("Undead damage bonus: +{}%", undeadDamageBonus);
      }
      // 钝器对亡灵有额外50%伤害
      if (isBluntWeapon) {
        bonusPercent += 50;
        log.debug("Blunt weapon extra undead damage bonus: +50%");
      }
    }
    
    if (bonusPercent > 0) {
      return baseDamage + baseDamage * bonusPercent / 100;
    }
    
    return baseDamage;
  }

  //==========================================================================
  // 综合伤害计算
  //==========================================================================

  /**
   * 完整的伤害计算流程
   * 
   * <p>这是最主要的伤害计算入口，整合了所有伤害计算步骤
   * 
   * @param result 伤害结果对象（输出）
   * @param attacker 攻击者属性
   * @param defender 防御者属性
   * @param skillDamagePercent 技能伤害系数（128=100%）
   */
  public void calculateFullDamage(DamageResult result, 
      Attributes attacker, Attributes defender, int skillDamagePercent) {
    
    // 1. 获取攻击者属性（缺失时使用安全默认值防止空指针）
    int attackerLevel = getInt(attacker, Stat.level, 1);
    int strength = getInt(attacker, Stat.strength, 0);
    int dexterity = getInt(attacker, Stat.dexterity, 0);
    
    // Calculate attack rating based on attacker type
    // Reference D2MOD: SUNITDMG_IsHitSuccessful (line 2440-2447) and UNITS_GetAttackRate (line 2395-2418)
    // For monsters: nAttackRate = nStatValue + 5 * DEX + STAT_TOHIT (nStatValue = 0 for basic attack)
    // For players: nAttackRate = UNITS_GetAttackRate(pAttacker) = STAT_TOHIT + 5 * (DEX - 7) + ToHitFactor
    // For now, we use a simplified version without ToHitFactor
    int baseToHit = getInt(attacker, Stat.tohit, 0);
    int attackRating;
    
    // Check if this is a player by checking if dexterity is reasonable (players typically have DEX > 0)
    // Monsters may not have DEX set, so we use a different formula
    // TODO: Use proper entity type check instead of heuristic
    if (dexterity > 0) {
      // Player formula: STAT_TOHIT + 5 * (DEX - 7)
      // This matches UNITS_GetAttackRate for players (without ToHitFactor)
      attackRating = baseToHit + 5 * Math.max(0, dexterity - 7);
    } else {
      // Monster formula: nStatValue + 5 * DEX + STAT_TOHIT
      // Since monsters may not have DEX set, we use baseToHit directly
      // In D2MOD, monsters without DEX would have DEX = 0, so: nAttackRate = 0 + 5 * 0 + STAT_TOHIT = STAT_TOHIT
      attackRating = baseToHit;
    }
    
    // 2. 获取防御者属性
    int defenderLevel = getInt(defender, Stat.level, 1);
    int defense = getInt(defender, Stat.armorclass, 0);
    
    // 3. 命中判定
    if (!isHitSuccessful(attackRating, defense, attackerLevel, defenderLevel)) {
      result.resultFlags = 0; // 未命中
      result.totalDamage = 0;
      return;
    }
    result.resultFlags |= RESULT_SUCCESSFUL_HIT;
    
    // 4. Calculate physical damage
    // Reference D2MOD: STAT_SECONDARY_MINDAMAGE/MAXDAMAGE is for two-handed weapons (WieldType == 2)
    // For one-handed weapons (including dual wielding), use STAT_MINDAMAGE/MAXDAMAGE
    // For two-handed weapons, use STAT_SECONDARY_MINDAMAGE/MAXDAMAGE
    // Dual wielding: both hands use mindamage/maxdamage, attack sequence determines which weapon is used (not added together)
    int minDmg = getInt(attacker, Stat.mindamage, 1);
    int maxDmg = getInt(attacker, Stat.maxdamage, 2);
    
    // Check if this is a two-handed weapon (has secondary damage values)
    int secondaryMinDmg = getInt(attacker, Stat.secondary_mindamage, 0);
    int secondaryMaxDmg = getInt(attacker, Stat.secondary_maxdamage, 0);
    
    // If secondary damage exists and is greater than primary, use it (two-handed weapon)
    // Reference D2MOD SUnitDmg.cpp: if WieldType == 2, use STAT_SECONDARY_MINDAMAGE/MAXDAMAGE
    if (secondaryMinDmg > 0 && secondaryMaxDmg > 0 && 
        (secondaryMinDmg > minDmg || secondaryMaxDmg > maxDmg)) {
      minDmg = secondaryMinDmg;
      maxDmg = secondaryMaxDmg;
    }
    
    // Check if damage values are too low (likely missing weapon damage)
    // In D2, base unarmed damage should be at least 1-2, but with weapon should be much higher
    // If damage is still at default values, log a warning
    if (minDmg <= 1 && maxDmg <= 2) {
      log.warn("Physical damage appears to be missing weapon damage! minDmg={}, maxDmg={}, " +
          "secondaryMinDmg={}, secondaryMaxDmg={}. " +
          "Stat.mindamage and Stat.maxdamage may not include weapon damage.",
          minDmg, maxDmg, secondaryMinDmg, secondaryMaxDmg);
    }
    
    // Ensure minimum damage values
    if (minDmg < 1) minDmg = 1;
    if (maxDmg < 2) maxDmg = 2;
    if (maxDmg <= minDmg) maxDmg = minDmg + 1;
    
    int damagePercent = getInt(attacker, Stat.damagepercent, 0);
    
    if (log.traceEnabled()) {
      log.trace("Physical damage calculation: minDmg={}, maxDmg={}, STR={}, DEX={}, damagePercent={}%",
          minDmg, maxDmg, strength, dexterity, damagePercent);
    }
    
    result.physicalDamage = calculatePhysicalDamage(
        minDmg, maxDmg, strength, dexterity, 
        100, 0, damagePercent, skillDamagePercent);
    
    // 5. 暴击判定
    int critChance = getInt(attacker, Stat.passive_critical_strike, 0);
    int deadlyStrike = 0; // TODO: 从装备获取
    if (rollCriticalStrike(critChance, deadlyStrike, 0)) {
      result.physicalDamage *= 2;
      result.resultFlags |= RESULT_CRITICAL_STRIKE;
      log.debug("Critical strike! Damage doubled: {}", result.physicalDamage);
    }
    
    // 6. 计算元素伤害
    int fireMin = getInt(attacker, Stat.firemindam, 0);
    int fireMax = getInt(attacker, Stat.firemaxdam, 0);
    int fireMastery = getInt(attacker, Stat.passive_fire_mastery, 0);
    result.fireDamage = calculateElementalDamage(fireMin, fireMax, fireMastery);
    
    int ltngMin = getInt(attacker, Stat.lightmindam, 0);
    int ltngMax = getInt(attacker, Stat.lightmaxdam, 0);
    int ltngMastery = getInt(attacker, Stat.passive_ltng_mastery, 0);
    result.lightningDamage = calculateElementalDamage(ltngMin, ltngMax, ltngMastery);
    
    int coldMin = getInt(attacker, Stat.coldmindam, 0);
    int coldMax = getInt(attacker, Stat.coldmaxdam, 0);
    int coldMastery = getInt(attacker, Stat.passive_cold_mastery, 0);
    result.coldDamage = calculateElementalDamage(coldMin, coldMax, coldMastery);
    
    int magMin = getInt(attacker, Stat.magicmindam, 0);
    int magMax = getInt(attacker, Stat.magicmaxdam, 0);
    result.magicDamage = calculateElementalDamage(magMin, magMax, 0);
    
    // 7. 应用防御者抗性
    int fireRes = getInt(defender, Stat.fireresist, 0);
    int ltngRes = getInt(defender, Stat.lightresist, 0);
    int coldRes = getInt(defender, Stat.coldresist, 0);
    int magRes = getInt(defender, Stat.magicresist, 0);
    int physRes = getInt(defender, Stat.damageresist, 0);
    
    int maxRes = 75; // 默认最大抗性
    
    result.physicalDamage = applyResistance(result.physicalDamage, physRes, maxRes, 0, 0, 0);
    result.fireDamage = applyResistance(result.fireDamage, fireRes, maxRes, 0, 0, 0);
    result.lightningDamage = applyResistance(result.lightningDamage, ltngRes, maxRes, 0, 0, 0);
    result.coldDamage = applyResistance(result.coldDamage, coldRes, maxRes, 0, 0, 0);
    result.magicDamage = applyResistance(result.magicDamage, magRes, maxRes, 0, 0, 0);
    
    // 8. 计算总伤害
    result.totalDamage = result.physicalDamage + result.fireDamage + 
        result.lightningDamage + result.coldDamage + result.magicDamage;
    
    // 9. 计算生命偷取
    int lifeLeechPercent = getInt(attacker, Stat.lifedrainmindam, 0); // 简化：使用最小值
    int defenderHp = getInt(defender, Stat.hitpoints, 0);
    result.lifeLeech = calculateLifeLeech(result.physicalDamage, lifeLeechPercent, defenderHp);
    
    // 10. 计算法力偷取
    int manaLeechPercent = getInt(attacker, Stat.manadrainmindam, 0);
    int defenderMana = getInt(defender, Stat.mana, 0);
    result.manaLeech = calculateManaLeech(result.physicalDamage, manaLeechPercent, defenderMana);
    
    log.debug("Damage calculation complete: physical={}, fire={}, lightning={}, cold={}, magic={}, total={}", 
        result.physicalDamage, result.fireDamage, result.lightningDamage, 
        result.coldDamage, result.magicDamage, result.totalDamage);
  }

  private static int getInt(Attributes attrs, short stat, int defaultValue) {
    StatRef ref = attrs.get(stat);
    return ref != null ? ref.asInt() : defaultValue;
  }

  /**
   * 简化的伤害计算（用于怪物攻击等场景）
   * 
   * @param minDamage 最小伤害
   * @param maxDamage 最大伤害
   * @param damageBonus 伤害加成%
   * @return 计算后的伤害值
   */
  public int calculateSimpleDamage(int minDamage, int maxDamage, int damageBonus) {
    if (minDamage < 1) minDamage = 1;
    if (maxDamage <= minDamage) maxDamage = minDamage + 1;
    
    int baseDamage = minDamage + MathUtils.random(maxDamage - minDamage);
    
    if (damageBonus != 0) {
      baseDamage = baseDamage * (100 + damageBonus) / 100;
    }
    
    return Math.max(baseDamage, 1);
  }
}
