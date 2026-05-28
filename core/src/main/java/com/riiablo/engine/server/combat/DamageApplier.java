package com.riiablo.engine.server.combat;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 伤害应用器 - 基于 D2MOD SUNITDMG_CalculateTotalDamage 移植
 * 
 * <p>处理伤害的最终计算，包括：
 * <ul>
 *   <li>抗性计算（含最大抗性限制）</li>
 *   <li>元素穿透</li>
 *   <li>伤害吸收（百分比和固定值）</li>
 *   <li>伤害减免（物理和魔法）</li>
 *   <li>PvP 伤害修正</li>
 *   <li>状态效果免疫检查</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/UNIT/SUnitDmg.cpp - SUNITDMG_CalculateTotalDamage
 * 
 * @author riiablo team
 */
public class DamageApplier {
  private static final Logger log = LogManager.getLogger(DamageApplier.class);

  /** 单例实例 */
  public static final DamageApplier INSTANCE = new DamageApplier();

  private DamageApplier() {}

  //==========================================================================
  // 伤害类型常量
  //==========================================================================

  /** 伤害类型：物理 */
  public static final int DAMAGE_TYPE_PHYSICAL = 0;
  /** 伤害类型：火焰 */
  public static final int DAMAGE_TYPE_FIRE = 1;
  /** 伤害类型：闪电 */
  public static final int DAMAGE_TYPE_LIGHTNING = 2;
  /** 伤害类型：冰冷 */
  public static final int DAMAGE_TYPE_COLD = 3;
  /** 伤害类型：魔法 */
  public static final int DAMAGE_TYPE_MAGIC = 4;
  /** 伤害类型：毒素 */
  public static final int DAMAGE_TYPE_POISON = 5;

  //==========================================================================
  // 抗性上限
  //==========================================================================

  /** 默认最大抗性 */
  public static final int DEFAULT_MAX_RESIST = 75;
  /** 绝对最大抗性（任何加成都无法超过此值） */
  public static final int ABSOLUTE_MAX_RESIST = 95;
  /** 最小抗性（可以为负值，代表额外伤害） */
  public static final int MIN_RESIST = -100;

  //==========================================================================
  // PvP 伤害修正
  //==========================================================================

  /** 玩家对玩家伤害系数（17%） */
  public static final int PVP_DAMAGE_PERCENT = 17;
  /** 佣兵对佣兵伤害系数（25%） */
  public static final int HIRELING_VS_HIRELING_PERCENT = 25;

  //==========================================================================
  // 核心方法
  //==========================================================================

  /**
   * 应用抗性和吸收计算
   * 
   * <p>参考 D2MOD SUNITDMG_ApplyResistancesAndAbsorb
   * 
   * @param result 伤害结果（将被修改）
   * @param defenderAttrs 防御者属性
   * @param isPvP 是否为PvP
   */
  public void applyResistancesAndAbsorb(DamageResult result, Attributes defenderAttrs, boolean isPvP) {
    // 物理伤害：应用物理抗性和伤害减免
    result.physicalDamage = applyPhysicalResist(
        result.physicalDamage, 
        defenderAttrs,
        result.piercePercent);

    // 火焰伤害：应用火焰抗性和吸收
    result.fireDamage = applyElementalResist(
        result.fireDamage,
        defenderAttrs,
        Stat.fireresist, 
        Stat.maxfireresist,
        Stat.passive_fire_pierce,
        Stat.item_absorbfire_percent,
        Stat.item_absorbfire,
        result);

    // 闪电伤害
    result.lightningDamage = applyElementalResist(
        result.lightningDamage,
        defenderAttrs,
        Stat.lightresist,
        Stat.maxlightresist,
        Stat.passive_ltng_pierce,
        Stat.item_absorblight_percent,
        Stat.item_absorblight,
        result);

    // 冰冷伤害
    result.coldDamage = applyElementalResist(
        result.coldDamage,
        defenderAttrs,
        Stat.coldresist,
        Stat.maxcoldresist,
        Stat.passive_cold_pierce,
        Stat.item_absorbcold_percent,
        Stat.item_absorbcold,
        result);

    // 魔法伤害
    result.magicDamage = applyElementalResist(
        result.magicDamage,
        defenderAttrs,
        Stat.magicresist,
        Stat.maxmagicresist,
        (short) -1, // 魔法没有穿透
        Stat.item_absorbmagic_percent,
        Stat.item_absorbmagic,
        result);

    // 毒素伤害：应用毒素抗性
    result.poisonDamage = applyPoisonResist(result.poisonDamage, defenderAttrs);

    // 毒素持续时间减免
    result.poisonDuration = applyPoisonLengthResist(result.poisonDuration, defenderAttrs);

    // 冰冻免疫检查
    applyFreezeImmunity(result, defenderAttrs);

    // PvP 伤害修正
    if (isPvP) {
      applyPvPModifier(result);
    }

    // 计算总伤害
    result.totalDamage = result.physicalDamage + result.fireDamage + 
        result.lightningDamage + result.coldDamage + result.magicDamage;

    log.debug("Applied resistances: phys={}, fire={}, ltng={}, cold={}, mag={}, total={}",
        result.physicalDamage, result.fireDamage, result.lightningDamage,
        result.coldDamage, result.magicDamage, result.totalDamage);
  }

  /**
   * 应用物理抗性和伤害减免
   * 
   * @param damage 原始伤害
   * @param attrs 防御者属性
   * @param piercePercent 穿透百分比
   * @return 减免后的伤害
   */
  private int applyPhysicalResist(int damage, Attributes attrs, int piercePercent) {
    if (damage <= 0) return 0;

    // 获取物理抗性
    int resist = getInt(attrs, Stat.damageresist, 0);
    
    // 限制抗性范围
    resist = Math.max(MIN_RESIST, Math.min(ABSOLUTE_MAX_RESIST, resist));

    // 应用抗性
    int reducedDamage = damage * (100 - resist) / 100;

    // 应用伤害减免（固定值）
    int damageReduction = getInt(attrs, Stat.normal_damage_reduction, 0) << 8;
    if (damageReduction > 0 && piercePercent > 0) {
      // 穿透降低伤害减免效果
      damageReduction = damageReduction * piercePercent / 1024;
    }
    reducedDamage = Math.max(0, reducedDamage - damageReduction);

    return reducedDamage;
  }

  /**
   * 应用元素抗性和吸收
   * 
   * @param damage 原始伤害
   * @param attrs 防御者属性
   * @param resistStat 抗性属性ID
   * @param maxResistStat 最大抗性属性ID
   * @param pierceStat 穿透属性ID（攻击者）
   * @param absorbPctStat 百分比吸收属性ID
   * @param absorbStat 固定值吸收属性ID
   * @param result 伤害结果（用于记录吸收的生命）
   * @return 减免后的伤害
   */
  private int applyElementalResist(int damage, Attributes attrs,
      short resistStat, short maxResistStat, short pierceStat,
      short absorbPctStat, short absorbStat, DamageResult result) {
    
    if (damage <= 0) return 0;

    // 获取抗性
    int resist = getInt(attrs, resistStat, 0);
    
    // 获取最大抗性
    int maxResist = DEFAULT_MAX_RESIST;
    if (maxResistStat > 0) {
      maxResist = DEFAULT_MAX_RESIST + getInt(attrs, maxResistStat, 0);
      maxResist = Math.min(maxResist, ABSOLUTE_MAX_RESIST);
    }

    // TODO: 应用攻击者的穿透（需要从攻击者属性获取）
    // 这里简化处理，穿透在攻击者计算时已应用

    // 限制抗性范围
    resist = Math.max(MIN_RESIST, Math.min(maxResist, resist));

    // 应用抗性
    int reducedDamage = damage * (100 - resist) / 100;

    // 应用百分比吸收
    int absorbPct = getInt(attrs, absorbPctStat, 0);
    if (absorbPct > 0) {
      int absorbed = reducedDamage * absorbPct / 100;
      reducedDamage -= absorbed;
      result.absorbedLife += absorbed;
    }

    // 应用固定值吸收
    int absorbFlat = getInt(attrs, absorbStat, 0) << 8;
    if (absorbFlat > 0) {
      int absorbed = Math.min(reducedDamage, absorbFlat);
      reducedDamage -= absorbed;
      result.absorbedLife += absorbed;
    }

    return Math.max(0, reducedDamage);
  }

  /**
   * 应用毒素抗性
   */
  private int applyPoisonResist(int damage, Attributes attrs) {
    if (damage <= 0) return 0;

    int resist = getInt(attrs, Stat.poisonresist, 0);
    int maxResist = DEFAULT_MAX_RESIST + getInt(attrs, Stat.maxpoisonresist, 0);
    maxResist = Math.min(maxResist, ABSOLUTE_MAX_RESIST);
    resist = Math.max(MIN_RESIST, Math.min(maxResist, resist));

    return damage * (100 - resist) / 100;
  }

  /**
   * 应用毒素持续时间减免
   */
  private int applyPoisonLengthResist(int duration, Attributes attrs) {
    if (duration <= 0) return 0;

    int lengthResist = getInt(attrs, Stat.item_poisonlengthresist, 0);
    if (lengthResist > 0) {
      duration = duration * (100 - Math.min(lengthResist, 75)) / 100;
    }

    return Math.max(0, duration);
  }

  /**
   * 应用冰冻免疫
   */
  private void applyFreezeImmunity(DamageResult result, Attributes attrs) {
    // 检查无法冰冻
    if (getInt(attrs, Stat.item_cannotbefrozen, 0) > 0) {
      result.coldDuration = 0;
      result.freezeDuration = 0;
      return;
    }

    // 检查冰冻时间减半
    if (getInt(attrs, Stat.item_halffreezeduration, 0) > 0) {
      result.coldDuration /= 2;
      result.freezeDuration /= 2;
    }
  }

  /**
   * 应用PvP伤害修正
   * 
   * <p>玩家对玩家伤害只有17%
   */
  private void applyPvPModifier(DamageResult result) {
    result.physicalDamage = result.physicalDamage * PVP_DAMAGE_PERCENT / 100;
    result.fireDamage = result.fireDamage * PVP_DAMAGE_PERCENT / 100;
    result.lightningDamage = result.lightningDamage * PVP_DAMAGE_PERCENT / 100;
    result.coldDamage = result.coldDamage * PVP_DAMAGE_PERCENT / 100;
    result.magicDamage = result.magicDamage * PVP_DAMAGE_PERCENT / 100;
    result.poisonDamage = result.poisonDamage * PVP_DAMAGE_PERCENT / 100;
  }

  //==========================================================================
  // 伤害转换
  //==========================================================================

  /**
   * 应用伤害转换
   * 
   * <p>参考 D2MOD 伤害转换逻辑，将物理伤害的一部分转换为元素伤害
   * 
   * @param result 伤害结果
   * @param conversionType 转换类型（元素类型）
   * @param conversionPct 转换百分比
   */
  public void applyDamageConversion(DamageResult result, int conversionType, int conversionPct) {
    if (conversionPct <= 0 || result.physicalDamage <= 0) {
      return;
    }

    // 限制转换百分比
    conversionPct = Math.min(conversionPct, 100);

    // 计算转换的伤害量
    int convertedDamage = result.physicalDamage * conversionPct / 100;
    result.physicalDamage -= convertedDamage;

    // 根据类型添加到对应元素伤害
    switch (conversionType) {
      case DAMAGE_TYPE_FIRE:
        result.fireDamage += convertedDamage;
        break;
      case DAMAGE_TYPE_LIGHTNING:
        result.lightningDamage += convertedDamage;
        break;
      case DAMAGE_TYPE_COLD:
        result.coldDamage += convertedDamage;
        break;
      case DAMAGE_TYPE_MAGIC:
        result.magicDamage += convertedDamage;
        break;
      case DAMAGE_TYPE_POISON:
        // 毒素转换特殊处理：伤害除以8，但有持续时间
        result.poisonDamage += convertedDamage / 8;
        if (result.poisonDuration < 50) {
          result.poisonDuration = 50;
        }
        break;
    }

    result.conversionType = conversionType;
    result.conversionPercent = conversionPct;

    log.debug("Damage conversion: {}% to type {}, converted={}", 
        conversionPct, conversionType, convertedDamage);
  }

  //==========================================================================
  // 生命偷取计算
  //==========================================================================

  /**
   * 计算实际偷取的生命值
   * 
   * <p>偷取上限为目标当前生命值
   * 
   * @param physicalDamage 物理伤害
   * @param leechPercent 偷取百分比
   * @param targetCurrentHp 目标当前生命值
   * @return 实际偷取量
   */
  public int calculateLifeLeech(int physicalDamage, int leechPercent, int targetCurrentHp) {
    if (physicalDamage <= 0 || leechPercent <= 0) {
      return 0;
    }

    int leechAmount = physicalDamage * leechPercent / 100;
    
    // 偷取不能超过目标当前生命值
    return Math.min(leechAmount, targetCurrentHp);
  }

  /**
   * 计算实际偷取的法力值
   */
  public int calculateManaLeech(int physicalDamage, int leechPercent, int targetCurrentMana) {
    if (physicalDamage <= 0 || leechPercent <= 0) {
      return 0;
    }

    int leechAmount = physicalDamage * leechPercent / 100;
    return Math.min(leechAmount, targetCurrentMana);
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 安全获取整数属性值
   */
  private int getInt(Attributes attrs, short stat, int defaultValue) {
    if (stat < 0) return defaultValue;
    StatRef ref = attrs.get(stat);
    return ref != null ? ref.asInt() : defaultValue;
  }
}
