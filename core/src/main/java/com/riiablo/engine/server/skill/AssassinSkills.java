package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 刺客技能实现 - 基于 D2MOD SkillAss.cpp 移植
 * 
 * <p>包含武技、陷阱、暗影三系技能的实现。
 * 
 * <p>参考：D2MOD/source/D2Game/src/SKILLS/SkillAss.cpp
 * 
 * @author riiablo team
 */
public final class AssassinSkills {
  private static final Logger log = LogManager.getLogger(AssassinSkills.class);

  private AssassinSkills() {} // 不可实例化

  //==========================================================================
  // 武技 - 充能技
  //==========================================================================

  /**
   * 虎击 - 累积充能
   * 
   * @param skillLevel 技能等级
   * @param chargeLevel 当前充能级别（1-3）
   * @return 伤害加成百分比
   */
  public static int calculateTigerStrikeDamageBonus(int skillLevel, int chargeLevel) {
    // 充能级别影响伤害
    int baseBonus = 100 + (skillLevel - 1) * 20;
    return baseBonus * chargeLevel;
  }

  /**
   * 烈焰拳 - 火焰充能攻击
   * 
   * @param skillLevel 技能等级
   * @param chargeLevel 当前充能级别
   * @return 火焰伤害
   */
  public static int calculateFistsOfFireDamage(int skillLevel, int chargeLevel) {
    int baseDamage = 6 + (skillLevel - 1) * 4;
    switch (chargeLevel) {
      case 1: return baseDamage; // 单目标
      case 2: return baseDamage * 2 / 3; // 范围较小
      case 3: return baseDamage / 2; // 火墙
      default: return baseDamage;
    }
  }

  /**
   * 眼镜蛇打击 - 吸取生命/法力
   * 
   * @param skillLevel 技能等级
   * @param chargeLevel 当前充能级别
   * @return 吸取百分比
   */
  public static int calculateCobraStrikeSteal(int skillLevel, int chargeLevel) {
    int baseSteal = 40 + (skillLevel - 1) * 5;
    return baseSteal * chargeLevel / 3;
  }

  /**
   * 雷电之爪 - 闪电充能攻击
   * 
   * @param skillLevel 技能等级
   * @param chargeLevel 当前充能级别
   * @return 闪电伤害
   */
  public static int calculateClawsOfThunderDamage(int skillLevel, int chargeLevel) {
    int minDamage = 1 + (skillLevel - 1);
    int maxDamage = 40 + (skillLevel - 1) * 10;
    return MathUtils.random(minDamage, maxDamage) * chargeLevel;
  }

  /**
   * 寒冰之刃 - 冰冷充能攻击
   * 
   * @param skillLevel 技能等级
   * @param chargeLevel 当前充能级别
   * @return 冰冷伤害
   */
  public static int calculateBladesOfIceDamage(int skillLevel, int chargeLevel) {
    int baseDamage = 15 + (skillLevel - 1) * 8;
    return baseDamage * chargeLevel;
  }

  /**
   * 凤凰打击 - 元素连击
   * 
   * @param skillLevel 技能等级
   * @param chargeLevel 当前充能级别
   * @return 元素伤害
   */
  public static int calculatePhoenixStrikeDamage(int skillLevel, int chargeLevel) {
    int baseDamage = 30 + (skillLevel - 1) * 15;
    return baseDamage * chargeLevel;
  }

  //==========================================================================
  // 武技 - 完成技
  //==========================================================================

  /**
   * 龙爪 - 双爪攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateDragonTalonDamageBonus(int skillLevel) {
    // 每级 +10%
    return 10 * skillLevel;
  }

  /**
   * 获取龙爪踢击次数
   * 
   * @param skillLevel 技能等级
   * @return 踢击次数
   */
  public static int getDragonTalonKickCount(int skillLevel) {
    // 基础 2 次，每 6 级 +1（最高 7）
    return Math.min(7, 2 + skillLevel / 6);
  }

  /**
   * 双龙爪 - 双武器攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateDragonClawDamageBonus(int skillLevel) {
    // 每级 +5%
    return 5 * skillLevel;
  }

  /**
   * 龙尾 - 范围火焰踢击
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害百分比（物理伤害的）
   */
  public static int getDragonTailFirePercent(int skillLevel) {
    // 基础 50%，每级 +15%
    return 50 + (skillLevel - 1) * 15;
  }

  /**
   * 龙飞 - 传送踢击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateDragonFlightDamageBonus(int skillLevel) {
    // 每级 +20%
    return 20 * skillLevel;
  }

  //==========================================================================
  // 陷阱技能
  //==========================================================================

  /**
   * 火焰爆震 - 基础火焰陷阱
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateFireBlastDamage(int skillLevel) {
    // 基础 3-6，每级 +3-4
    int minDamage = 3 + (skillLevel - 1) * 3;
    int maxDamage = 6 + (skillLevel - 1) * 4;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 电击网 - 闪电网
   * 
   * @param skillLevel 技能等级
   * @return 闪电伤害
   */
  public static int calculateShockWebDamage(int skillLevel) {
    // 基础 6-10，每级 +4-5
    int minDamage = 6 + (skillLevel - 1) * 4;
    int maxDamage = 10 + (skillLevel - 1) * 5;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 刃之守卫 - 旋转飞刃
   * 
   * @param skillLevel 技能等级
   * @return 物理伤害
   */
  public static int calculateBladeSentinelDamage(int skillLevel) {
    // 基础 6-10，每级 +3-4
    int minDamage = 6 + (skillLevel - 1) * 3;
    int maxDamage = 10 + (skillLevel - 1) * 4;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 电光哨兵 - 发射充能弹的哨兵
   * 
   * @param skillLevel 技能等级
   * @return 闪电伤害
   */
  public static int calculateChargedBoltSentryDamage(int skillLevel) {
    // 基础 10-20，每级 +5-8
    int minDamage = 10 + (skillLevel - 1) * 5;
    int maxDamage = 20 + (skillLevel - 1) * 8;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 火焰苏醒 - 发射火焰的陷阱
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateWakeOfFireDamage(int skillLevel) {
    // 基础 12-22，每级 +8-10
    int minDamage = 12 + (skillLevel - 1) * 8;
    int maxDamage = 22 + (skillLevel - 1) * 10;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 刃之狂怒 - 远程飞刃攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害百分比（武器伤害的）
   */
  public static int getBladeFuryDamagePercent(int skillLevel) {
    // 基础 75%，每级 +6%
    return 75 + (skillLevel - 1) * 6;
  }

  /**
   * 闪电哨兵 - 发射闪电的哨兵
   * 
   * @param skillLevel 技能等级
   * @return 闪电伤害
   */
  public static int calculateLightningSentryDamage(int skillLevel) {
    // 基础 10-30，每级 +5-12
    int minDamage = 10 + (skillLevel - 1) * 5;
    int maxDamage = 30 + (skillLevel - 1) * 12;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 烈焰苏醒 - 发射火墙的陷阱
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateWakeOfInfernoDamage(int skillLevel) {
    // 基础 40-70，每级 +15-20
    int minDamage = 40 + (skillLevel - 1) * 15;
    int maxDamage = 70 + (skillLevel - 1) * 20;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 死亡哨兵 - 引爆尸体的哨兵
   * 
   * @param skillLevel 技能等级
   * @return 尸体爆炸伤害百分比
   */
  public static int getDeathSentryCorpseExplosionPercent(int skillLevel) {
    // 40-80% 尸体生命
    return 40 + (skillLevel - 1) * 4;
  }

  /**
   * 刃之护盾 - 旋转飞刃护盾
   * 
   * @param skillLevel 技能等级
   * @return 每刃伤害
   */
  public static int calculateBladeShieldDamage(int skillLevel) {
    // 基础 10-15，每级 +5-6
    int minDamage = 10 + (skillLevel - 1) * 5;
    int maxDamage = 15 + (skillLevel - 1) * 6;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 获取最大陷阱数量
   * 
   * @return 最大陷阱数
   */
  public static int getMaxTraps() {
    // 固定 5 个
    return 5;
  }

  //==========================================================================
  // 暗影技能
  //==========================================================================

  /**
   * 利爪专精 - 增加利爪伤害和攻击等级
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateClawMasteryDamageBonus(int skillLevel) {
    // 基础 35%，每级 +5%
    return 35 + (skillLevel - 1) * 5;
  }

  /**
   * 利爪专精暴击概率
   * 
   * @param skillLevel 技能等级
   * @return 暴击概率百分比
   */
  public static int getClawMasteryCriticalChance(int skillLevel) {
    // 基础 3%，每级 +0.8%
    return (int)(3.0f + (skillLevel - 1) * 0.8f);
  }

  /**
   * 心灵爆震 - 击退并造成伤害
   * 
   * @param skillLevel 技能等级
   * @return 物理伤害
   */
  public static int calculatePsychicHammerDamage(int skillLevel) {
    // 基础 1-4，每级 +1-2
    int minDamage = 1 + (skillLevel - 1);
    int maxDamage = 4 + (skillLevel - 1) * 2;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 爆发速度 - 增加攻击和移动速度
   * 
   * @param skillLevel 技能等级
   * @return 速度加成百分比
   */
  public static int calculateBurstOfSpeedBonus(int skillLevel) {
    // 每级 +4%
    return 4 * skillLevel;
  }

  /**
   * 获取爆发速度持续时间
   * 
   * @param skillLevel 技能等级
   * @return 持续时间（秒）
   */
  public static float getBurstOfSpeedDuration(int skillLevel) {
    // 基础 120 秒，每级 +12 秒
    return 120.0f + (skillLevel - 1) * 12.0f;
  }

  /**
   * 武器格挡 - 双爪格挡概率
   * 
   * @param skillLevel 技能等级
   * @return 格挡概率百分比
   */
  public static int getWeaponBlockChance(int skillLevel) {
    // 基础 26%，每级 +3%
    return Math.min(60, 26 + (skillLevel - 1) * 3);
  }

  /**
   * 暗影斗篷 - 降低敌人防御和视野
   * 
   * @param skillLevel 技能等级
   * @return 防御降低百分比
   */
  public static int calculateCloakOfShadowsDefenseReduce(int skillLevel) {
    // 每级 +5%
    return 5 * skillLevel;
  }

  /**
   * 消退 - 增加抗性并降低诅咒持续时间
   * 
   * @param skillLevel 技能等级
   * @return 抗性加成
   */
  public static int calculateFadeResistBonus(int skillLevel) {
    // 每级 +1%
    return skillLevel;
  }

  /**
   * 消退物理减伤
   * 
   * @param skillLevel 技能等级
   * @return 物理减伤百分比
   */
  public static int calculateFadePhysicalReduce(int skillLevel) {
    // 每级 +1%
    return skillLevel;
  }

  /**
   * 影子战士 - 召唤影子
   * 
   * @param skillLevel 技能等级
   * @return 影子等级
   */
  public static int getShadowWarriorLevel(int skillLevel) {
    return skillLevel;
  }

  /**
   * 心灵爆破 - 范围眩晕
   * 
   * @param skillLevel 技能等级
   * @return 眩晕持续时间（秒）
   */
  public static float getMindBlastStunDuration(int skillLevel) {
    // 基础 0.4 秒，每级 +0.2 秒
    return 0.4f + (skillLevel - 1) * 0.2f;
  }

  /**
   * 毒素蔓延 - 武器附加毒素伤害
   * 
   * @param skillLevel 技能等级
   * @return 总毒素伤害
   */
  public static int calculateVenomDamage(int skillLevel) {
    // 基础 100-125，每级 +20-25
    int minDamage = 100 + (skillLevel - 1) * 20;
    int maxDamage = 125 + (skillLevel - 1) * 25;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 获取毒素蔓延持续时间
   * 
   * @return 持续时间（秒）
   */
  public static float getVenomDuration() {
    // 固定 0.4 秒
    return 0.4f;
  }

  /**
   * 影子大师 - 召唤强力影子
   * 
   * @param skillLevel 技能等级
   * @return 影子等级
   */
  public static int getShadowMasterLevel(int skillLevel) {
    return skillLevel;
  }
}
