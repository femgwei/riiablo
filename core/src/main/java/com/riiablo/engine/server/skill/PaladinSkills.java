package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 圣骑士技能实现 - 基于 D2MOD SkillPal.cpp 移植
 * 
 * <p>包含战斗技能、攻击光环、防御光环三系技能的实现。
 * 
 * <p>参考：D2MOD/source/D2Game/src/SKILLS/SkillPal.cpp
 * 
 * @author riiablo team
 */
public final class PaladinSkills {
  private static final Logger log = LogManager.getLogger(PaladinSkills.class);

  private PaladinSkills() {} // 不可实例化

  //==========================================================================
  // 战斗技能
  //==========================================================================

  /**
   * 牺牲 - 消耗生命造成额外伤害
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateSacrificeDamageBonus(int skillLevel) {
    // 基础 180%，每级 +15%
    return 180 + (skillLevel - 1) * 15;
  }

  /**
   * 牺牲自伤
   * 
   * @param damageDealt 造成的伤害
   * @return 自伤值
   */
  public static int calculateSacrificeSelfDamage(int damageDealt) {
    // 8% 自伤
    return damageDealt * 8 / 100;
  }

  /**
   * 重击 - 盾牌攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateSmiteDamageBonus(int skillLevel) {
    // 基础 15-25，每级 +5
    return 15 + (skillLevel - 1) * 5;
  }

  /**
   * 重击击退概率
   * 
   * @param skillLevel 技能等级
   * @return 击退概率百分比
   */
  public static int getSmiteKnockbackChance(int skillLevel) {
    // 固定 100% 击退
    return 100;
  }

  /**
   * 圣光弹 - 对不死和恶魔造成额外伤害
   * 
   * @param skillLevel 技能等级
   * @return 基础伤害
   */
  public static int calculateHolyBoltDamage(int skillLevel) {
    // 基础 8-16，每级 +4
    int minDamage = 8 + (skillLevel - 1) * 4;
    int maxDamage = 16 + (skillLevel - 1) * 4;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 圣光弹治疗量
   * 
   * @param skillLevel 技能等级
   * @return 治疗量
   */
  public static int calculateHolyBoltHeal(int skillLevel) {
    // 基础 12，每级 +4
    return 12 + (skillLevel - 1) * 4;
  }

  /**
   * 热忱 - 快速连续攻击
   * 
   * @param skillLevel 技能等级
   * @return 攻击次数
   */
  public static int getZealAttackCount(int skillLevel) {
    // 固定 5 次攻击
    return 5;
  }

  /**
   * 热忱伤害加成
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateZealDamageBonus(int skillLevel) {
    // 每级 +10%
    return 10 * skillLevel;
  }

  /**
   * 热忱攻击等级加成
   * 
   * @param skillLevel 技能等级
   * @return 攻击等级加成
   */
  public static int calculateZealAttackRatingBonus(int skillLevel) {
    // 每级 +10%
    return 10 * skillLevel;
  }

  /**
   * 冲锋 - 冲向敌人
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateChargeDamageBonus(int skillLevel) {
    // 基础 100%，每级 +25%
    return 100 + (skillLevel - 1) * 25;
  }

  /**
   * 复仇 - 元素伤害攻击
   * 
   * @param skillLevel 技能等级
   * @param elementType 元素类型（0=火，1=冰，2=电）
   * @return 元素伤害
   */
  public static int calculateVengeanceDamage(int skillLevel, int elementType) {
    // 每种元素各加 40-60 基础，每级 +20
    int minDamage = 40 + (skillLevel - 1) * 20;
    int maxDamage = 60 + (skillLevel - 1) * 20;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 祝福之锤 - 旋转的魔法锤
   * 
   * @param skillLevel 技能等级
   * @return 伤害
   */
  public static int calculateBlessedHammerDamage(int skillLevel) {
    // 基础 12-16，每级 +6-8
    int minDamage = 12 + (skillLevel - 1) * 6;
    int maxDamage = 16 + (skillLevel - 1) * 8;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 祝福之锤对不死/恶魔额外伤害
   * 
   * @param skillLevel 技能等级
   * @return 额外伤害百分比
   */
  public static int getBlessedHammerBonusPercent(int skillLevel) {
    // 对不死 +50%
    return 50;
  }

  /**
   * 皈依 - 使敌人变成友方
   * 
   * @param skillLevel 技能等级
   * @return 转化概率百分比
   */
  public static int getConversionChance(int skillLevel) {
    // 每级 +4%
    return 4 * skillLevel;
  }

  /**
   * 神圣之盾 - 增加格挡
   * 
   * @param skillLevel 技能等级
   * @return 防御加成百分比
   */
  public static int calculateHolyShieldDefenseBonus(int skillLevel) {
    // 每级 +15%
    return 15 * skillLevel;
  }

  /**
   * 神圣之盾格挡加成
   * 
   * @param skillLevel 技能等级
   * @return 格挡加成百分比
   */
  public static int calculateHolyShieldBlockBonus(int skillLevel) {
    // 每级 +7%（封顶 75%）
    return Math.min(40, 7 * skillLevel);
  }

  /**
   * 天堂之拳 - 召唤闪电攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害
   */
  public static int calculateFistOfHeavensDamage(int skillLevel) {
    // 基础 30-50，每级 +15-20
    int minDamage = 30 + (skillLevel - 1) * 15;
    int maxDamage = 50 + (skillLevel - 1) * 20;
    return MathUtils.random(minDamage, maxDamage);
  }

  //==========================================================================
  // 攻击光环
  //==========================================================================

  /**
   * 力量 - 增加物理伤害
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateMightDamageBonus(int skillLevel) {
    // 基础 40%，每级 +20%
    return 40 + (skillLevel - 1) * 20;
  }

  /**
   * 圣火 - 火焰伤害光环
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateHolyFireDamage(int skillLevel) {
    // 基础 6-8，每级 +4-5
    int minDamage = 6 + (skillLevel - 1) * 4;
    int maxDamage = 8 + (skillLevel - 1) * 5;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 荆棘 - 返还伤害
   * 
   * @param skillLevel 技能等级
   * @return 返还百分比
   */
  public static int calculateThornsReflectPercent(int skillLevel) {
    // 基础 250%，每级 +50%
    return 250 + (skillLevel - 1) * 50;
  }

  /**
   * 祝福瞄准 - 增加攻击等级
   * 
   * @param skillLevel 技能等级
   * @return 攻击等级加成百分比
   */
  public static int calculateBlessedAimBonus(int skillLevel) {
    // 基础 75%，每级 +15%
    return 75 + (skillLevel - 1) * 15;
  }

  /**
   * 专注 - 增加伤害和不可打断
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateConcentrationDamageBonus(int skillLevel) {
    // 基础 60%，每级 +15%
    return 60 + (skillLevel - 1) * 15;
  }

  /**
   * 圣冻 - 冰冷伤害光环
   * 
   * @param skillLevel 技能等级
   * @return 冰冷伤害
   */
  public static int calculateHolyFreezeDamage(int skillLevel) {
    // 基础 6-8，每级 +3-4
    int minDamage = 6 + (skillLevel - 1) * 3;
    int maxDamage = 8 + (skillLevel - 1) * 4;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 圣冻减速
   * 
   * @param skillLevel 技能等级
   * @return 减速百分比
   */
  public static int getHolyFreezeSlowPercent(int skillLevel) {
    // 固定 50%
    return 50;
  }

  /**
   * 圣击 - 闪电伤害光环
   * 
   * @param skillLevel 技能等级
   * @return 闪电伤害
   */
  public static int calculateHolyShockDamage(int skillLevel) {
    // 基础 1-10，每级 +1-8
    int minDamage = 1 + (skillLevel - 1);
    int maxDamage = 10 + (skillLevel - 1) * 8;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 狂热 - 增加伤害、攻击速度和攻击等级
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateFanaticismDamageBonus(int skillLevel) {
    // 基础 180%，每级 +21%
    return 180 + (skillLevel - 1) * 21;
  }

  /**
   * 狂热攻击速度加成
   * 
   * @param skillLevel 技能等级
   * @return 攻击速度加成百分比
   */
  public static int calculateFanaticismIasBonus(int skillLevel) {
    // 固定 35%
    return 35;
  }

  /**
   * 信念 - 降低敌人抗性
   * 
   * @param skillLevel 技能等级
   * @return 抗性降低量
   */
  public static int calculateConvictionResistReduce(int skillLevel) {
    // 基础 -30%，每级 -5%（最高 -150%）
    return Math.min(150, 30 + (skillLevel - 1) * 5);
  }

  //==========================================================================
  // 防御光环
  //==========================================================================

  /**
   * 祈祷 - 恢复生命
   * 
   * @param skillLevel 技能等级
   * @return 每秒恢复生命
   */
  public static int calculatePrayerHealPerSecond(int skillLevel) {
    // 基础 2，每级 +1
    return 2 + (skillLevel - 1);
  }

  /**
   * 抵御火焰/冰冷/闪电 - 增加抗性
   * 
   * @param skillLevel 技能等级
   * @return 抗性加成
   */
  public static int calculateResistAuraBonus(int skillLevel) {
    // 基础 30%，每级 +5%（最高 +75%）
    return Math.min(75, 30 + (skillLevel - 1) * 5);
  }

  /**
   * 反抗 - 增加防御
   * 
   * @param skillLevel 技能等级
   * @return 防御加成百分比
   */
  public static int calculateDefianceDefenseBonus(int skillLevel) {
    // 基础 70%，每级 +15%
    return 70 + (skillLevel - 1) * 15;
  }

  /**
   * 净化 - 降低诅咒和毒素持续时间
   * 
   * @param skillLevel 技能等级
   * @return 持续时间降低百分比
   */
  public static int calculateCleansingReducePercent(int skillLevel) {
    // 基础 -50%，每级 -6%
    return 50 + (skillLevel - 1) * 6;
  }

  /**
   * 活力 - 增加移动速度和耐力恢复
   * 
   * @param skillLevel 技能等级
   * @return 速度加成百分比
   */
  public static int calculateVigorSpeedBonus(int skillLevel) {
    // 固定 +40% 速度
    return 40;
  }

  /**
   * 冥想 - 增加法力恢复
   * 
   * @param skillLevel 技能等级
   * @return 法力恢复加成百分比
   */
  public static int calculateMeditationManaRegen(int skillLevel) {
    // 基础 100%，每级 +40%
    return 100 + (skillLevel - 1) * 40;
  }

  /**
   * 救赎 - 从尸体恢复生命/法力
   * 
   * @param skillLevel 技能等级
   * @return 恢复概率百分比
   */
  public static int getRedemptionChance(int skillLevel) {
    // 基础 20%，每级 +4%
    return 20 + (skillLevel - 1) * 4;
  }

  /**
   * 救赎恢复量
   * 
   * @param skillLevel 技能等级
   * @return 生命/法力恢复量
   */
  public static int calculateRedemptionHeal(int skillLevel) {
    // 基础 25，每级 +7
    return 25 + (skillLevel - 1) * 7;
  }

  /**
   * 救世 - 增加所有抗性
   * 
   * @param skillLevel 技能等级
   * @return 所有抗性加成
   */
  public static int calculateSalvationResistBonus(int skillLevel) {
    // 基础 25%，每级 +5%
    return 25 + (skillLevel - 1) * 5;
  }
}
