package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 法师技能实现 - 基于 D2MOD SkillSor.cpp 移植
 * 
 * <p>包含火焰、闪电、冰冷三系技能的实现。
 * 
 * <p>参考：D2MOD/source/D2Game/src/SKILLS/SkillSor.cpp
 * 
 * @author riiablo team
 */
public final class SorceressSkills {
  private static final Logger log = LogManager.getLogger(SorceressSkills.class);

  private SorceressSkills() {} // 不可实例化

  //==========================================================================
  // 火焰技能
  //==========================================================================

  /**
   * 火焰弹 - 基础火焰攻击技能
   * 
   * <p>发射一个火焰弹造成火焰伤害
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateFireBoltDamage(int skillLevel) {
    // 基础伤害 3-6，每级 +1-2
    int minDamage = 3 + (skillLevel - 1);
    int maxDamage = 6 + (skillLevel - 1) * 2;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 温暖 - 被动技能，增加法力恢复
   * 
   * @param skillLevel 技能等级
   * @return 法力恢复加成百分比
   */
  public static int calculateWarmthManaRegen(int skillLevel) {
    // 每级 +30% 法力恢复
    return 30 * skillLevel;
  }

  /**
   * 火焰弹 - 范围火焰攻击
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateFireBallDamage(int skillLevel) {
    // 基础伤害 6-14，每级 +2-3
    int minDamage = 6 + (skillLevel - 1) * 2;
    int maxDamage = 14 + (skillLevel - 1) * 3;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 火焰墙 - 在地面创建火焰墙
   * 
   * @param skillLevel 技能等级
   * @return 每秒火焰伤害
   */
  public static int calculateFireWallDamagePerSecond(int skillLevel) {
    // 基础 40-80/秒，每级 +17-18
    int minDamage = 40 + (skillLevel - 1) * 17;
    int maxDamage = 80 + (skillLevel - 1) * 18;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 流星 - 召唤流星撞击地面
   * 
   * @param skillLevel 技能等级
   * @return 撞击伤害
   */
  public static int calculateMeteorDamage(int skillLevel) {
    // 基础伤害 60-100，每级 +20-25
    int minDamage = 60 + (skillLevel - 1) * 20;
    int maxDamage = 100 + (skillLevel - 1) * 25;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 火焰精通 - 被动技能，增加火焰伤害
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害加成百分比
   */
  public static int calculateFireMasteryBonus(int skillLevel) {
    // 每级 +7% 火焰伤害
    return 23 + (skillLevel - 1) * 7;
  }

  //==========================================================================
  // 闪电技能
  //==========================================================================

  /**
   * 充能弹 - 发射多个闪电弹
   * 
   * @param skillLevel 技能等级
   * @return 每个弹的伤害
   */
  public static int calculateChargedBoltDamage(int skillLevel) {
    // 基础 2-4，每级 +1
    int minDamage = 2 + (skillLevel - 1);
    int maxDamage = 4 + (skillLevel - 1);
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 获取充能弹数量
   * 
   * @param skillLevel 技能等级
   * @return 弹数
   */
  public static int getChargedBoltCount(int skillLevel) {
    // 基础 3 个，每 3 级 +1
    return 3 + skillLevel / 3;
  }

  /**
   * 静态力场 - 降低范围内敌人当前生命值
   * 
   * @param skillLevel 技能等级
   * @return 降低百分比
   */
  public static int calculateStaticFieldPercent(int skillLevel) {
    // 固定降低 25% 当前生命
    return 25;
  }

  /**
   * 获取静态力场半径
   * 
   * @param skillLevel 技能等级
   * @return 半径（子格）
   */
  public static int getStaticFieldRadius(int skillLevel) {
    // 基础 3.3 码，每级 +0.6 码
    return (int)(3.3f + (skillLevel - 1) * 0.6f);
  }

  /**
   * 心灵传动 - 推开敌人并造成伤害
   * 
   * @param skillLevel 技能等级
   * @return 伤害
   */
  public static int calculateTelekinessDamage(int skillLevel) {
    // 基础 1-2，每级 +1
    int minDamage = 1 + (skillLevel - 1);
    int maxDamage = 2 + (skillLevel - 1);
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 新星 - 以自身为中心释放闪电
   * 
   * @param skillLevel 技能等级
   * @return 伤害
   */
  public static int calculateNovaDamage(int skillLevel) {
    // 基础 1-20，每级 +2-3
    int minDamage = 1 + (skillLevel - 1) * 2;
    int maxDamage = 20 + (skillLevel - 1) * 3;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 闪电 - 发射闪电束
   * 
   * @param skillLevel 技能等级
   * @return 伤害
   */
  public static int calculateLightningDamage(int skillLevel) {
    // 基础 1-40，每级 +1-8
    int minDamage = 1 + (skillLevel - 1);
    int maxDamage = 40 + (skillLevel - 1) * 8;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 连锁闪电 - 可在敌人间跳跃的闪电
   * 
   * @param skillLevel 技能等级
   * @return 伤害
   */
  public static int calculateChainLightningDamage(int skillLevel) {
    // 基础 1-40，每级 +1-6
    int minDamage = 1 + (skillLevel - 1);
    int maxDamage = 40 + (skillLevel - 1) * 6;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 获取连锁闪电跳跃次数
   * 
   * @param skillLevel 技能等级
   * @return 跳跃次数
   */
  public static int getChainLightningHits(int skillLevel) {
    // 基础 5 次，每 5 级 +1
    return 5 + skillLevel / 5;
  }

  /**
   * 传送 - 瞬间移动到目标位置
   * 
   * @param skillLevel 技能等级
   * @return 法力消耗
   */
  public static int calculateTeleportManaCost(int skillLevel) {
    // 基础 24，每级 -1（最低 6）
    return Math.max(6, 24 - (skillLevel - 1));
  }

  /**
   * 雷云风暴 - 持续召唤闪电打击周围敌人
   * 
   * @param skillLevel 技能等级
   * @return 每次打击伤害
   */
  public static int calculateThunderStormDamage(int skillLevel) {
    // 基础 1-100，每级 +1-10
    int minDamage = 1 + (skillLevel - 1);
    int maxDamage = 100 + (skillLevel - 1) * 10;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 能量护盾 - 将伤害转换为法力消耗
   * 
   * @param skillLevel 技能等级
   * @return 吸收百分比
   */
  public static int calculateEnergyShieldAbsorb(int skillLevel) {
    // 基础 20%，每级 +5%（最高 95%）
    return Math.min(95, 20 + (skillLevel - 1) * 5);
  }

  /**
   * 闪电精通 - 被动技能，增加闪电伤害
   * 
   * @param skillLevel 技能等级
   * @return 闪电伤害加成百分比
   */
  public static int calculateLightningMasteryBonus(int skillLevel) {
    // 每级 +7% 闪电伤害
    return 23 + (skillLevel - 1) * 7;
  }

  //==========================================================================
  // 冰冷技能
  //==========================================================================

  /**
   * 冰弹 - 基础冰冷攻击
   * 
   * @param skillLevel 技能等级
   * @return 冰冷伤害
   */
  public static int calculateIceBoltDamage(int skillLevel) {
    // 基础 3-5，每级 +2
    int minDamage = 3 + (skillLevel - 1) * 2;
    int maxDamage = 5 + (skillLevel - 1) * 2;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 冰封装甲 - 增加防御并冻结攻击者
   * 
   * @param skillLevel 技能等级
   * @return 防御加成
   */
  public static int calculateFrozenArmorDefense(int skillLevel) {
    // 基础 +30%，每级 +5%
    return 30 + (skillLevel - 1) * 5;
  }

  /**
   * 霜冻新星 - 以自身为中心释放冰霜
   * 
   * @param skillLevel 技能等级
   * @return 伤害
   */
  public static int calculateFrostNovaDamage(int skillLevel) {
    // 基础 6-9，每级 +4
    int minDamage = 6 + (skillLevel - 1) * 4;
    int maxDamage = 9 + (skillLevel - 1) * 4;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 冰封爆破 - 范围冰冷攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害
   */
  public static int calculateIceBlastDamage(int skillLevel) {
    // 基础 8-12，每级 +5
    int minDamage = 8 + (skillLevel - 1) * 5;
    int maxDamage = 12 + (skillLevel - 1) * 5;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 冰刺 - 爆炸性冰冷攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害
   */
  public static int calculateGlacialSpikeDamage(int skillLevel) {
    // 基础 16-24，每级 +5-6
    int minDamage = 16 + (skillLevel - 1) * 5;
    int maxDamage = 24 + (skillLevel - 1) * 6;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 暴风雪 - 在区域内降下冰雹
   * 
   * @param skillLevel 技能等级
   * @return 每击伤害
   */
  public static int calculateBlizzardDamage(int skillLevel) {
    // 基础 20-40，每级 +8-10
    int minDamage = 20 + (skillLevel - 1) * 8;
    int maxDamage = 40 + (skillLevel - 1) * 10;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 冰封球 - 释放一个会碎裂的冰球
   * 
   * @param skillLevel 技能等级
   * @return 主体伤害
   */
  public static int calculateFrozenOrbDamage(int skillLevel) {
    // 基础 10-15，每级 +3
    int minDamage = 10 + (skillLevel - 1) * 3;
    int maxDamage = 15 + (skillLevel - 1) * 3;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 冰冷精通 - 被动技能，降低敌人冰冷抗性
   * 
   * @param skillLevel 技能等级
   * @return 抗性降低百分比
   */
  public static int calculateColdMasteryPierce(int skillLevel) {
    // 每级 -5% 敌人冰冷抗性
    return 20 + (skillLevel - 1) * 5;
  }

  //==========================================================================
  // 冻结持续时间
  //==========================================================================

  /**
   * 计算冻结持续时间
   * 
   * @param skillLevel 技能等级
   * @param baseDuration 基础持续时间（帧）
   * @return 持续时间（帧）
   */
  public static int calculateFreezeDuration(int skillLevel, int baseDuration) {
    // 每级 +10%
    return baseDuration * (100 + skillLevel * 10) / 100;
  }

  /**
   * 计算冰冷减速百分比
   * 
   * @return 减速百分比
   */
  public static int getColdSlowPercent() {
    // 固定 50% 减速
    return 50;
  }
}
