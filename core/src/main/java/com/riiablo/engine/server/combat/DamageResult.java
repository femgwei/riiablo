package com.riiablo.engine.server.combat;

/**
 * 伤害计算结果 - 基于 D2MOD D2DamageStrc 结构
 * 
 * <p>该类存储一次攻击的完整伤害信息，包括各种伤害类型、
 * 状态效果持续时间、命中结果标志等。
 * 
 * <p>参考：D2MOD/source/D2Game/include/UNIT/SUnitDmg.h - D2DamageStrc
 * 
 * @author riiablo team
 */
public class DamageResult {

  //==========================================================================
  // 结果标志
  //==========================================================================
  
  /** 
   * 命中结果标志位
   * @see DamageCalculator#RESULT_SUCCESSFUL_HIT
   * @see DamageCalculator#RESULT_CRITICAL_STRIKE
   * @see DamageCalculator#RESULT_BLOCK
   */
  public int resultFlags;
  
  /**
   * 命中效果标志位
   * @see DamageCalculator#HIT_LIFE_DRAIN
   * @see DamageCalculator#HIT_MANA_DRAIN
   */
  public int hitFlags;

  //==========================================================================
  // 物理伤害
  //==========================================================================
  
  /** 物理伤害（定点数，实际值需要 >> 8） */
  public int physicalDamage;
  
  /** 伤害加成百分比 */
  public int enhancedDamagePercent;

  //==========================================================================
  // 元素伤害
  //==========================================================================
  
  /** 火焰伤害 */
  public int fireDamage;
  
  /** 灼烧伤害（持续火焰伤害） */
  public int burnDamage;
  
  /** 灼烧持续时间（帧数） */
  public int burnDuration;
  
  /** 闪电伤害 */
  public int lightningDamage;
  
  /** 魔法伤害 */
  public int magicDamage;
  
  /** 冰冷伤害 */
  public int coldDamage;
  
  /** 冰冷持续时间（减速效果，帧数） */
  public int coldDuration;
  
  /** 冰冻持续时间（完全冻结，帧数） */
  public int freezeDuration;
  
  /** 毒素伤害（每帧） */
  public int poisonDamage;
  
  /** 毒素持续时间（帧数） */
  public int poisonDuration;

  //==========================================================================
  // 偷取效果
  //==========================================================================
  
  /** 生命偷取量 */
  public int lifeLeech;
  
  /** 法力偷取量 */
  public int manaLeech;
  
  /** 体力偷取量 */
  public int staminaLeech;

  //==========================================================================
  // 其他效果
  //==========================================================================
  
  /** 眩晕持续时间（帧数） */
  public int stunDuration;
  
  /** 吸收的生命值 */
  public int absorbedLife;
  
  /** 总伤害（所有类型合计） */
  public int totalDamage;
  
  /** 穿透百分比 */
  public int piercePercent;
  
  /** 伤害速率（每帧伤害） */
  public int damageRate;
  
  /** 命中类别（用于确定音效） */
  public int hitClass;
  
  /** 伤害转换类型（元素类型转换） */
  public int conversionType;
  
  /** 伤害转换百分比 */
  public int conversionPercent;
  
  /** 覆盖层效果ID */
  public int overlayId;

  //==========================================================================
  // 方法
  //==========================================================================

  /**
   * 重置所有伤害值为0
   */
  public void reset() {
    resultFlags = 0;
    hitFlags = 0;
    physicalDamage = 0;
    enhancedDamagePercent = 0;
    fireDamage = 0;
    burnDamage = 0;
    burnDuration = 0;
    lightningDamage = 0;
    magicDamage = 0;
    coldDamage = 0;
    coldDuration = 0;
    freezeDuration = 0;
    poisonDamage = 0;
    poisonDuration = 0;
    lifeLeech = 0;
    manaLeech = 0;
    staminaLeech = 0;
    stunDuration = 0;
    absorbedLife = 0;
    totalDamage = 0;
    piercePercent = 0;
    damageRate = 0;
    hitClass = 0;
    conversionType = 0;
    conversionPercent = 0;
    overlayId = 0;
  }

  /**
   * 检查是否成功命中
   */
  public boolean isHit() {
    return (resultFlags & DamageCalculator.RESULT_SUCCESSFUL_HIT) != 0;
  }

  /**
   * 检查是否暴击
   */
  public boolean isCritical() {
    return (resultFlags & DamageCalculator.RESULT_CRITICAL_STRIKE) != 0;
  }

  /**
   * 检查是否被格挡
   */
  public boolean isBlocked() {
    return (resultFlags & DamageCalculator.RESULT_BLOCK) != 0;
  }

  /**
   * 检查目标是否会死亡
   */
  public boolean willKill() {
    return (resultFlags & DamageCalculator.RESULT_WILL_DIE) != 0;
  }

  /**
   * 检查是否有击退效果
   */
  public boolean hasKnockback() {
    return (resultFlags & DamageCalculator.RESULT_KNOCKBACK) != 0;
  }

  /**
   * 计算总元素伤害
   */
  public int getTotalElementalDamage() {
    return fireDamage + lightningDamage + coldDamage + magicDamage;
  }

  /**
   * 检查是否有持续伤害效果
   */
  public boolean hasDamageOverTime() {
    return burnDuration > 0 || poisonDuration > 0;
  }

  /**
   * 检查是否有控制效果
   */
  public boolean hasCrowdControl() {
    return coldDuration > 0 || freezeDuration > 0 || stunDuration > 0;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("DamageResult{");
    sb.append("总伤害=").append(totalDamage);
    if (physicalDamage > 0) sb.append(", 物理=").append(physicalDamage);
    if (fireDamage > 0) sb.append(", 火焰=").append(fireDamage);
    if (lightningDamage > 0) sb.append(", 闪电=").append(lightningDamage);
    if (coldDamage > 0) sb.append(", 冰冷=").append(coldDamage);
    if (magicDamage > 0) sb.append(", 魔法=").append(magicDamage);
    if (poisonDamage > 0) sb.append(", 毒素=").append(poisonDamage).append("/").append(poisonDuration).append("帧");
    if (isCritical()) sb.append(", 暴击!");
    if (isBlocked()) sb.append(", 被格挡");
    if (lifeLeech > 0) sb.append(", 吸血=").append(lifeLeech);
    sb.append("}");
    return sb.toString();
  }
}
