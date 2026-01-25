package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 野蛮人技能实现 - 基于 D2MOO SkillBar.cpp 移植
 * 
 * <p>包含战斗技能、战吼、战斗专精三系技能的实现。
 * 
 * <p>参考：D2MOO/source/D2Game/src/SKILLS/SkillBar.cpp
 * 
 * @author riiablo team
 */
public final class BarbarianSkills {
  private static final Logger log = LogManager.getLogger(BarbarianSkills.class);

  private BarbarianSkills() {} // 不可实例化

  //==========================================================================
  // 战斗技能
  //==========================================================================

  /**
   * 重击 - 基础攻击技能
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateBashDamageBonus(int skillLevel) {
    // 基础 50%，每级 +5%
    return 50 + (skillLevel - 1) * 5;
  }

  /**
   * 重击击退概率
   * 
   * @param skillLevel 技能等级
   * @return 击退概率百分比
   */
  public static int getBashKnockbackChance(int skillLevel) {
    // 固定 100% 击退
    return 100;
  }

  /**
   * 跳跃 - 跳跃到目标位置
   * 
   * @param skillLevel 技能等级
   * @return 最大跳跃距离
   */
  public static int getLeapDistance(int skillLevel) {
    // 基础 4 码，每级 +0.6 码
    return (int)(4.0f + (skillLevel - 1) * 0.6f);
  }

  /**
   * 双手挥击 - 同时使用双武器攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateDoubleSwingDamageBonus(int skillLevel) {
    // 每级 +5%
    return 5 * skillLevel;
  }

  /**
   * 眩晕 - 使敌人眩晕
   * 
   * @param skillLevel 技能等级
   * @return 眩晕持续时间（秒）
   */
  public static float getStunDuration(int skillLevel) {
    // 基础 1 秒，每级 +0.2 秒
    return 1.0f + (skillLevel - 1) * 0.2f;
  }

  /**
   * 双手投掷 - 同时投掷两把武器
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateDoubleThrowDamageBonus(int skillLevel) {
    // 每级 +5%
    return 5 * skillLevel;
  }

  /**
   * 跳跃攻击 - 跳跃到敌人并攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateLeapAttackDamageBonus(int skillLevel) {
    // 基础 100%，每级 +20%
    return 100 + (skillLevel - 1) * 20;
  }

  /**
   * 专心 - 不可中断的强力攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateConcentrateDamageBonus(int skillLevel) {
    // 基础 70%，每级 +10%
    return 70 + (skillLevel - 1) * 10;
  }

  /**
   * 专心防御加成
   * 
   * @param skillLevel 技能等级
   * @return 防御加成百分比
   */
  public static int calculateConcentrateDefenseBonus(int skillLevel) {
    // 基础 100%，每级 +10%
    return 100 + (skillLevel - 1) * 10;
  }

  /**
   * 狂乱 - 快速多次攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateFrenzyDamageBonus(int skillLevel) {
    // 每级 +5%
    return 5 * skillLevel;
  }

  /**
   * 狂乱速度加成
   * 
   * @param skillLevel 技能等级
   * @return 攻击/移动速度加成百分比（每次攻击叠加）
   */
  public static int getFrenzySpeedBonusPerHit(int skillLevel) {
    // 每次攻击 +7% 速度
    return 7;
  }

  /**
   * 获取狂乱最大叠加数
   * 
   * @return 最大叠加数
   */
  public static int getFrenzyMaxStacks() {
    // 最多叠加 8 次
    return 8;
  }

  /**
   * 旋风斩 - 旋转攻击周围所有敌人
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateWhirlwindDamageBonus(int skillLevel) {
    // 每级 +5%
    return 5 * skillLevel;
  }

  /**
   * 狂战士 - 魔法伤害攻击，降低自身防御
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateBerserkDamageBonus(int skillLevel) {
    // 基础 150%，每级 +15%
    return 150 + (skillLevel - 1) * 15;
  }

  /**
   * 获取狂战士攻击等级加成
   * 
   * @param skillLevel 技能等级
   * @return 攻击等级加成百分比
   */
  public static int getBerserkAttackRatingBonus(int skillLevel) {
    // 每级 +10%
    return 10 * skillLevel;
  }

  //==========================================================================
  // 战吼
  //==========================================================================

  /**
   * 嚎叫 - 使敌人逃跑
   * 
   * @param skillLevel 技能等级
   * @return 影响半径
   */
  public static int getHowlRadius(int skillLevel) {
    // 基础 4 码，每级 +0.6 码
    return (int)(4.0f + (skillLevel - 1) * 0.6f);
  }

  /**
   * 寻找药水 - 从尸体获得药水
   * 
   * @param skillLevel 技能等级
   * @return 成功概率百分比
   */
  public static int getFindPotionChance(int skillLevel) {
    // 基础 16%，每级 +3%
    return 16 + (skillLevel - 1) * 3;
  }

  /**
   * 嘲讽 - 吸引敌人攻击
   * 
   * @param skillLevel 技能等级
   * @return 持续时间（秒）
   */
  public static float getTauntDuration(int skillLevel) {
    // 基础 6 秒，每级 +0.6 秒
    return 6.0f + (skillLevel - 1) * 0.6f;
  }

  /**
   * 呐喊 - 增加防御
   * 
   * @param skillLevel 技能等级
   * @return 防御加成百分比
   */
  public static int calculateShoutDefenseBonus(int skillLevel) {
    // 基础 100%，每级 +10%
    return 100 + (skillLevel - 1) * 10;
  }

  /**
   * 获取呐喊持续时间
   * 
   * @param skillLevel 技能等级
   * @return 持续时间（秒）
   */
  public static float getShoutDuration(int skillLevel) {
    // 基础 20 秒，每级 +10 秒
    return 20.0f + (skillLevel - 1) * 10.0f;
  }

  /**
   * 寻找物品 - 从尸体获得物品
   * 
   * @param skillLevel 技能等级
   * @return 成功概率百分比
   */
  public static int getFindItemChance(int skillLevel) {
    // 基础 13%，每级 +2%
    return 13 + (skillLevel - 1) * 2;
  }

  /**
   * 战斗怒吼 - 降低敌人伤害和防御
   * 
   * @param skillLevel 技能等级
   * @return 降低百分比
   */
  public static int calculateBattleCryReducePercent(int skillLevel) {
    // 基础 25%，每级 +4%
    return 25 + (skillLevel - 1) * 4;
  }

  /**
   * 战斗指令 - 增加生命和法力
   * 
   * @param skillLevel 技能等级
   * @return 生命/法力加成百分比
   */
  public static int calculateBattleOrdersBonus(int skillLevel) {
    // 基础 35%，每级 +3%
    return 35 + (skillLevel - 1) * 3;
  }

  /**
   * 获取战斗指令持续时间
   * 
   * @param skillLevel 技能等级
   * @return 持续时间（秒）
   */
  public static float getBattleOrdersDuration(int skillLevel) {
    // 基础 20 秒，每级 +10 秒
    return 20.0f + (skillLevel - 1) * 10.0f;
  }

  /**
   * 严肃守护 - 使敌人逃跑并降低伤害
   * 
   * @param skillLevel 技能等级
   * @return 降低伤害百分比
   */
  public static int calculateGrimWardDamageReduce(int skillLevel) {
    // 基础 25%，每级 +4%
    return 25 + (skillLevel - 1) * 4;
  }

  /**
   * 战争嚎叫 - 眩晕范围内敌人
   * 
   * @param skillLevel 技能等级
   * @return 眩晕持续时间（秒）
   */
  public static float getWarCryStunDuration(int skillLevel) {
    // 基础 1.2 秒，每级 +0.2 秒
    return 1.2f + (skillLevel - 1) * 0.2f;
  }

  /**
   * 战争嚎叫伤害
   * 
   * @param skillLevel 技能等级
   * @return 伤害
   */
  public static int calculateWarCryDamage(int skillLevel) {
    // 基础 10-15，每级 +4-5
    int minDamage = 10 + (skillLevel - 1) * 4;
    int maxDamage = 15 + (skillLevel - 1) * 5;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 战斗命令 - 增加所有技能等级
   * 
   * @param skillLevel 技能等级
   * @return 技能等级加成
   */
  public static int getBattleCommandSkillBonus(int skillLevel) {
    // 固定 +1 所有技能
    return 1;
  }

  //==========================================================================
  // 战斗专精
  //==========================================================================

  /**
   * 武器专精（剑/斧/钉锤等）- 增加伤害和攻击等级
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateWeaponMasteryDamageBonus(int skillLevel) {
    // 基础 28%，每级 +5%
    return 28 + (skillLevel - 1) * 5;
  }

  /**
   * 武器专精攻击等级加成
   * 
   * @param skillLevel 技能等级
   * @return 攻击等级加成百分比
   */
  public static int calculateWeaponMasteryAttackRatingBonus(int skillLevel) {
    // 基础 28%，每级 +8%
    return 28 + (skillLevel - 1) * 8;
  }

  /**
   * 武器专精暴击概率
   * 
   * @param skillLevel 技能等级
   * @return 暴击概率百分比
   */
  public static int getWeaponMasteryCriticalChance(int skillLevel) {
    // 基础 3%，每级 +0.8%
    return (int)(3.0f + (skillLevel - 1) * 0.8f);
  }

  /**
   * 增强耐力 - 增加耐力恢复和最大耐力
   * 
   * @param skillLevel 技能等级
   * @return 耐力加成百分比
   */
  public static int calculateIncreasedStaminaBonus(int skillLevel) {
    // 基础 30%，每级 +15%
    return 30 + (skillLevel - 1) * 15;
  }

  /**
   * 钢铁皮肤 - 增加防御
   * 
   * @param skillLevel 技能等级
   * @return 防御加成百分比
   */
  public static int calculateIronSkinDefenseBonus(int skillLevel) {
    // 基础 30%，每级 +10%
    return 30 + (skillLevel - 1) * 10;
  }

  /**
   * 增强速度 - 增加移动和攻击速度
   * 
   * @param skillLevel 技能等级
   * @return 速度加成百分比
   */
  public static int calculateIncreasedSpeedBonus(int skillLevel) {
    // 基础 13%，每级 +4%
    return 13 + (skillLevel - 1) * 4;
  }

  /**
   * 自然抵抗 - 增加所有抗性
   * 
   * @param skillLevel 技能等级
   * @return 所有抗性加成
   */
  public static int calculateNaturalResistanceBonus(int skillLevel) {
    // 基础 12%，每级 +1%
    return 12 + (skillLevel - 1);
  }
}
