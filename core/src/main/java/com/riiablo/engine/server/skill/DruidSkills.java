package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 德鲁伊技能实现 - 基于 D2MOD SkillDruid.cpp 移植
 * 
 * <p>包含元素、变形、召唤三系技能的实现。
 * 
 * <p>参考：D2MOD/source/D2Game/src/SKILLS/SkillDruid.cpp
 * 
 * @author riiablo team
 */
public final class DruidSkills {
  private static final Logger log = LogManager.getLogger(DruidSkills.class);

  private DruidSkills() {} // 不可实例化

  //==========================================================================
  // 元素技能
  //==========================================================================

  /**
   * 火风暴 - 火焰旋风
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateFirestormDamage(int skillLevel) {
    // 基础 3-6，每级 +3-5
    int minDamage = 3 + (skillLevel - 1) * 3;
    int maxDamage = 6 + (skillLevel - 1) * 5;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 熔岩巨石 - 滚动的火焰巨石
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateMoltenBoulderDamage(int skillLevel) {
    // 基础 16-32，每级 +8-10
    int minDamage = 16 + (skillLevel - 1) * 8;
    int maxDamage = 32 + (skillLevel - 1) * 10;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 极地风暴 - 冰冷锥形攻击
   * 
   * @param skillLevel 技能等级
   * @return 冰冷伤害
   */
  public static int calculateArcticBlastDamage(int skillLevel) {
    // 基础 4-8，每级 +3-5
    int minDamage = 4 + (skillLevel - 1) * 3;
    int maxDamage = 8 + (skillLevel - 1) * 5;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 裂缝 - 地面火焰裂缝
   * 
   * @param skillLevel 技能等级
   * @return 每秒火焰伤害
   */
  public static int calculateFissureDamagePerSecond(int skillLevel) {
    // 基础 15-25，每级 +12-14
    int minDamage = 15 + (skillLevel - 1) * 12;
    int maxDamage = 25 + (skillLevel - 1) * 14;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 旋风护甲 - 吸收元素伤害
   * 
   * @param skillLevel 技能等级
   * @return 吸收量
   */
  public static int calculateCycloneArmorAbsorb(int skillLevel) {
    // 基础 40，每级 +20
    return 40 + (skillLevel - 1) * 20;
  }

  /**
   * 旋风 - 小型龙卷风
   * 
   * @param skillLevel 技能等级
   * @return 物理伤害
   */
  public static int calculateTwisterDamage(int skillLevel) {
    // 基础 6-12，每级 +4-5
    int minDamage = 6 + (skillLevel - 1) * 4;
    int maxDamage = 12 + (skillLevel - 1) * 5;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 火山 - 喷发火焰
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateVolcanoDamage(int skillLevel) {
    // 基础 20-40，每级 +15-18
    int minDamage = 20 + (skillLevel - 1) * 15;
    int maxDamage = 40 + (skillLevel - 1) * 18;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 龙卷风 - 强力龙卷风
   * 
   * @param skillLevel 技能等级
   * @return 物理伤害
   */
  public static int calculateTornadoDamage(int skillLevel) {
    // 基础 25-35，每级 +12-14
    int minDamage = 25 + (skillLevel - 1) * 12;
    int maxDamage = 35 + (skillLevel - 1) * 14;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 末日 - 天降流星雨
   * 
   * @param skillLevel 技能等级
   * @return 每颗流星伤害
   */
  public static int calculateArmageddonDamage(int skillLevel) {
    // 基础 50-100，每级 +25-30
    int minDamage = 50 + (skillLevel - 1) * 25;
    int maxDamage = 100 + (skillLevel - 1) * 30;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 飓风 - 围绕自身的风暴
   * 
   * @param skillLevel 技能等级
   * @return 冰冷伤害
   */
  public static int calculateHurricaneDamage(int skillLevel) {
    // 基础 25-50，每级 +10-12
    int minDamage = 25 + (skillLevel - 1) * 10;
    int maxDamage = 50 + (skillLevel - 1) * 12;
    return MathUtils.random(minDamage, maxDamage);
  }

  //==========================================================================
  // 变形技能
  //==========================================================================

  /**
   * 狼人形态 - 变身为狼人
   * 
   * @param skillLevel 技能等级
   * @return 攻击等级加成百分比
   */
  public static int calculateWerewolfAttackRatingBonus(int skillLevel) {
    // 基础 50%，每级 +20%
    return 50 + (skillLevel - 1) * 20;
  }

  /**
   * 狼人形态攻击速度加成
   * 
   * @param skillLevel 技能等级
   * @return 攻击速度加成百分比
   */
  public static int getWerewolfIasBonus(int skillLevel) {
    // 固定 +20%
    return 20;
  }

  /**
   * 变形学 - 增强变形持续时间和生命
   * 
   * @param skillLevel 技能等级
   * @return 生命加成百分比
   */
  public static int calculateLycanthropyLifeBonus(int skillLevel) {
    // 每级 +25%
    return 25 * skillLevel;
  }

  /**
   * 熊人形态 - 变身为熊人
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateWerebearDamageBonus(int skillLevel) {
    // 基础 50%，每级 +15%
    return 50 + (skillLevel - 1) * 15;
  }

  /**
   * 熊人防御加成
   * 
   * @param skillLevel 技能等级
   * @return 防御加成百分比
   */
  public static int calculateWerebearDefenseBonus(int skillLevel) {
    // 每级 +25%
    return 25 * skillLevel;
  }

  /**
   * 野性狂暴 - 狼人生命偷取攻击
   * 
   * @param skillLevel 技能等级
   * @return 生命偷取百分比
   */
  public static int getFeralRageLifeSteal(int skillLevel) {
    // 基础 5%，每级 +3%
    return 5 + (skillLevel - 1) * 3;
  }

  /**
   * 重击 - 熊人强力攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateMaulDamageBonus(int skillLevel) {
    // 基础 20%，每级 +10%
    return 20 + (skillLevel - 1) * 10;
  }

  /**
   * 狂犬病 - 毒素攻击
   * 
   * @param skillLevel 技能等级
   * @return 总毒素伤害
   */
  public static int calculateRabiesDamage(int skillLevel) {
    // 基础 100-150，每级 +50-75
    int minDamage = 100 + (skillLevel - 1) * 50;
    int maxDamage = 150 + (skillLevel - 1) * 75;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 火焰之爪 - 火焰攻击
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateFireClawsDamage(int skillLevel) {
    // 基础 15-20，每级 +10-12
    int minDamage = 15 + (skillLevel - 1) * 10;
    int maxDamage = 20 + (skillLevel - 1) * 12;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 饥饿 - 吸取生命和法力
   * 
   * @param skillLevel 技能等级
   * @return 吸取百分比
   */
  public static int getHungerStealPercent(int skillLevel) {
    // 基础 30%，每级 +5%
    return 30 + (skillLevel - 1) * 5;
  }

  /**
   * 冲击波 - 熊人眩晕攻击
   * 
   * @param skillLevel 技能等级
   * @return 眩晕持续时间（秒）
   */
  public static float getShockWaveStunDuration(int skillLevel) {
    // 基础 2 秒，每级 +0.2 秒
    return 2.0f + (skillLevel - 1) * 0.2f;
  }

  /**
   * 狂怒 - 狼人多目标攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateFuryDamageBonus(int skillLevel) {
    // 每级 +7%
    return 7 * skillLevel;
  }

  /**
   * 获取狂怒攻击次数
   * 
   * @return 攻击次数
   */
  public static int getFuryHitCount() {
    // 固定 5 次
    return 5;
  }

  //==========================================================================
  // 召唤技能
  //==========================================================================

  /**
   * 乌鸦 - 召唤乌鸦
   * 
   * @param skillLevel 技能等级
   * @return 最大乌鸦数
   */
  public static int getMaxRavens(int skillLevel) {
    // 基础 1，每 5 级 +1（最高 5）
    return Math.min(5, 1 + skillLevel / 5);
  }

  /**
   * 毒藤 - 召唤毒藤
   * 
   * @param skillLevel 技能等级
   * @return 毒素伤害
   */
  public static int calculatePoisonCreeperDamage(int skillLevel) {
    // 基础 10-20，每级 +5-10
    int minDamage = 10 + (skillLevel - 1) * 5;
    int maxDamage = 20 + (skillLevel - 1) * 10;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 橡木贤者 - 增加生命
   * 
   * @param skillLevel 技能等级
   * @return 生命加成百分比
   */
  public static int calculateOakSageLifeBonus(int skillLevel) {
    // 每级 +5%
    return 5 * skillLevel;
  }

  /**
   * 召唤灵狼
   * 
   * @param skillLevel 技能等级
   * @return 最大狼数
   */
  public static int getMaxSpiritWolves(int skillLevel) {
    // 基础 1，每 5 级 +1（最高 5）
    return Math.min(5, 1 + skillLevel / 5);
  }

  /**
   * 猎鹰藤 - 吸取生命
   * 
   * @param skillLevel 技能等级
   * @return 吸取生命百分比
   */
  public static int getCarrionVineLifeSteal(int skillLevel) {
    // 每级 +3%
    return 3 * skillLevel;
  }

  /**
   * 狼獾之心 - 增加伤害和攻击等级
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateHeartOfWolverineDamageBonus(int skillLevel) {
    // 每级 +4%
    return 4 * skillLevel;
  }

  /**
   * 召唤恐狼
   * 
   * @param skillLevel 技能等级
   * @return 最大恐狼数
   */
  public static int getMaxDireWolves(int skillLevel) {
    // 基础 1，每 4 级 +1（最高 3）
    return Math.min(3, 1 + skillLevel / 4);
  }

  /**
   * 太阳藤 - 吸取法力
   * 
   * @param skillLevel 技能等级
   * @return 吸取法力百分比
   */
  public static int getSolarCreeperManaSteal(int skillLevel) {
    // 每级 +3%
    return 3 * skillLevel;
  }

  /**
   * 荆棘之灵 - 返还伤害
   * 
   * @param skillLevel 技能等级
   * @return 返还伤害百分比
   */
  public static int calculateSpiritOfBarbsReflect(int skillLevel) {
    // 每级 +6%
    return 6 * skillLevel;
  }

  /**
   * 召唤灰熊 - 召唤强力灰熊
   * 
   * @param skillLevel 技能等级
   * @return 灰熊生命加成百分比
   */
  public static int calculateGrizzlyLifeBonus(int skillLevel) {
    // 每级 +15%
    return 15 * skillLevel;
  }
}
