package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 死灵法师技能实现 - 基于 D2MOO SkillNec.cpp 移植
 * 
 * <p>包含诅咒、毒素和骨、召唤三系技能的实现。
 * 
 * <p>参考：D2MOO/source/D2Game/src/SKILLS/SkillNec.cpp
 * 
 * @author riiablo team
 */
public final class NecromancerSkills {
  private static final Logger log = LogManager.getLogger(NecromancerSkills.class);

  private NecromancerSkills() {} // 不可实例化

  //==========================================================================
  // 诅咒技能
  //==========================================================================

  /**
   * 伤害加深 - 增加目标受到的物理伤害
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateAmplifyDamagePercent(int skillLevel) {
    // 固定 100% 伤害加深
    return 100;
  }

  /**
   * 获取伤害加深持续时间
   * 
   * @param skillLevel 技能等级
   * @return 持续时间（秒）
   */
  public static float getAmplifyDamageDuration(int skillLevel) {
    // 基础 8 秒，每级 +1.6 秒
    return 8.0f + (skillLevel - 1) * 1.6f;
  }

  /**
   * 昏暗视野 - 降低敌人视野
   * 
   * @param skillLevel 技能等级
   * @return 视野降低半径
   */
  public static int calculateDimVisionRadius(int skillLevel) {
    // 每级影响更多敌人
    return 4 + skillLevel / 2;
  }

  /**
   * 虚弱 - 降低目标物理伤害
   * 
   * @param skillLevel 技能等级
   * @return 伤害降低百分比
   */
  public static int calculateWeakenPercent(int skillLevel) {
    // 基础 -33%，不随等级变化
    return 33;
  }

  /**
   * 钢铁处女 - 返还物理伤害给攻击者
   * 
   * @param skillLevel 技能等级
   * @return 返还百分比
   */
  public static int calculateIronMaidenPercent(int skillLevel) {
    // 基础 200%，每级 +25%
    return 200 + (skillLevel - 1) * 25;
  }

  /**
   * 恐惧 - 使敌人逃跑
   * 
   * @param skillLevel 技能等级
   * @return 影响半径
   */
  public static int getTerrorRadius(int skillLevel) {
    return 2 + skillLevel / 3;
  }

  /**
   * 混乱 - 使敌人攻击其他敌人
   * 
   * @param skillLevel 技能等级
   * @return 持续时间（秒）
   */
  public static float getConfuseDuration(int skillLevel) {
    return 10.0f + skillLevel * 0.6f;
  }

  /**
   * 生命分流 - 攻击者从目标处吸取生命
   * 
   * @param skillLevel 技能等级
   * @return 吸取百分比
   */
  public static int calculateLifeTapPercent(int skillLevel) {
    // 固定 50% 生命偷取
    return 50;
  }

  /**
   * 衰老 - 减速并降低物理抗性
   * 
   * @param skillLevel 技能等级
   * @return 减速百分比
   */
  public static int calculateDecrepifySlowPercent(int skillLevel) {
    // 固定 50% 减速
    return 50;
  }

  /**
   * 衰老 - 物理抗性降低
   * 
   * @param skillLevel 技能等级
   * @return 抗性降低百分比
   */
  public static int calculateDecrepifyResistReduce(int skillLevel) {
    // 固定 -50% 物理抗性
    return 50;
  }

  /**
   * 降低抵抗 - 降低所有元素抗性
   * 
   * @param skillLevel 技能等级
   * @return 抗性降低百分比
   */
  public static int calculateLowerResistPercent(int skillLevel) {
    // 基础 -31%，每级 -5%（最高 -70%）
    return Math.min(70, 31 + (skillLevel - 1) * 5);
  }

  //==========================================================================
  // 毒素和骨技能
  //==========================================================================

  /**
   * 牙齿 - 发射多个骨齿
   * 
   * @param skillLevel 技能等级
   * @return 每个骨齿伤害
   */
  public static int calculateTeethDamage(int skillLevel) {
    // 基础 2-3，每级 +1
    int minDamage = 2 + (skillLevel - 1);
    int maxDamage = 3 + (skillLevel - 1);
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 获取牙齿数量
   * 
   * @param skillLevel 技能等级
   * @return 骨齿数量
   */
  public static int getTeethCount(int skillLevel) {
    // 基础 3 个，每 2 级 +1（最高 24）
    return Math.min(24, 3 + skillLevel / 2);
  }

  /**
   * 骨甲 - 吸收物理伤害
   * 
   * @param skillLevel 技能等级
   * @return 吸收量
   */
  public static int calculateBoneArmorAbsorb(int skillLevel) {
    // 基础 20，每级 +10
    return 20 + (skillLevel - 1) * 10;
  }

  /**
   * 毒匕首 - 毒素伤害攻击
   * 
   * @param skillLevel 技能等级
   * @return 总毒素伤害
   */
  public static int calculatePoisonDaggerDamage(int skillLevel) {
    // 基础 18-50，每级 +18-25
    int minDamage = 18 + (skillLevel - 1) * 18;
    int maxDamage = 50 + (skillLevel - 1) * 25;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 获取毒匕首持续时间
   * 
   * @param skillLevel 技能等级
   * @return 持续时间（秒）
   */
  public static float getPoisonDaggerDuration(int skillLevel) {
    // 固定 2 秒
    return 2.0f;
  }

  /**
   * 尸体爆炸 - 引爆尸体造成范围伤害
   * 
   * @param corpseMaxHp 尸体最大生命值
   * @return 伤害（60-100% 尸体最大生命）
   */
  public static int calculateCorpseExplosionDamage(int corpseMaxHp) {
    // 60-100% 尸体最大生命值
    float percent = MathUtils.random(0.6f, 1.0f);
    return (int)(corpseMaxHp * percent);
  }

  /**
   * 获取尸体爆炸半径
   * 
   * @param skillLevel 技能等级
   * @return 半径（子格）
   */
  public static int getCorpseExplosionRadius(int skillLevel) {
    // 基础 2.6 码，每级 +0.3 码
    return (int)(2.6f + (skillLevel - 1) * 0.3f);
  }

  /**
   * 骨墙 - 创建骨墙阻挡敌人
   * 
   * @param skillLevel 技能等级
   * @return 骨墙生命值
   */
  public static int calculateBoneWallHp(int skillLevel) {
    // 基础 20，每级 +10
    return 20 + (skillLevel - 1) * 10;
  }

  /**
   * 骨矛 - 发射骨矛穿透敌人
   * 
   * @param skillLevel 技能等级
   * @return 伤害
   */
  public static int calculateBoneSpearDamage(int skillLevel) {
    // 基础 16-24，每级 +10-12
    int minDamage = 16 + (skillLevel - 1) * 10;
    int maxDamage = 24 + (skillLevel - 1) * 12;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 骨牢 - 困住敌人的骨墙
   * 
   * @param skillLevel 技能等级
   * @return 骨牢生命值
   */
  public static int calculateBonePrisonHp(int skillLevel) {
    // 基础 22，每级 +12
    return 22 + (skillLevel - 1) * 12;
  }

  /**
   * 毒素新星 - 以自身为中心释放毒雾
   * 
   * @param skillLevel 技能等级
   * @return 总毒素伤害
   */
  public static int calculatePoisonNovaDamage(int skillLevel) {
    // 基础 125-150，每级 +20-25
    int minDamage = 125 + (skillLevel - 1) * 20;
    int maxDamage = 150 + (skillLevel - 1) * 25;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 骨灵 - 追踪敌人的骨魂
   * 
   * @param skillLevel 技能等级
   * @return 伤害
   */
  public static int calculateBoneSpiritDamage(int skillLevel) {
    // 基础 20-30，每级 +10-12
    int minDamage = 20 + (skillLevel - 1) * 10;
    int maxDamage = 30 + (skillLevel - 1) * 12;
    return MathUtils.random(minDamage, maxDamage);
  }

  //==========================================================================
  // 召唤技能
  //==========================================================================

  /**
   * 骷髅掌握 - 增强骷髅属性
   * 
   * @param skillLevel 技能等级
   * @return 生命/伤害加成百分比
   */
  public static int calculateSkeletonMasteryBonus(int skillLevel) {
    // 每级 +8% 生命和伤害
    return 8 * skillLevel;
  }

  /**
   * 获取骷髅最大数量
   * 
   * @param skillLevel 技能等级
   * @return 最大骷髅数
   */
  public static int getMaxSkeletons(int skillLevel) {
    // 每级 +1，最高等于等级
    return skillLevel;
  }

  /**
   * 粘土石魔 - 召唤粘土傀儡
   * 
   * @param skillLevel 技能等级
   * @return 石魔生命值
   */
  public static int calculateClayGolemHp(int skillLevel) {
    // 基础 100，每级 +50
    return 100 + (skillLevel - 1) * 50;
  }

  /**
   * 粘土石魔减速
   * 
   * @param skillLevel 技能等级
   * @return 减速百分比
   */
  public static int calculateClayGolemSlowPercent(int skillLevel) {
    // 基础 40%，每级 +3%
    return Math.min(75, 40 + (skillLevel - 1) * 3);
  }

  /**
   * 石魔掌握 - 增强石魔属性
   * 
   * @param skillLevel 技能等级
   * @return 生命/伤害/速度加成百分比
   */
  public static int calculateGolemMasteryBonus(int skillLevel) {
    // 每级 +20% 生命，+5% 速度
    return 20 * skillLevel;
  }

  /**
   * 骷髅法师 - 召唤骷髅法师
   * 
   * @param skillLevel 技能等级
   * @return 法师伤害加成
   */
  public static int calculateSkeletalMageDamageBonus(int skillLevel) {
    // 每级 +5% 伤害
    return 5 * skillLevel;
  }

  /**
   * 鲜血石魔 - 召唤鲜血傀儡
   * 
   * @param skillLevel 技能等级
   * @return 石魔生命值
   */
  public static int calculateBloodGolemHp(int skillLevel) {
    // 基础 200，每级 +75
    return 200 + (skillLevel - 1) * 75;
  }

  /**
   * 鲜血石魔生命偷取
   * 
   * @param skillLevel 技能等级
   * @return 生命偷取百分比
   */
  public static int calculateBloodGolemLifeSteal(int skillLevel) {
    // 固定偷取造成伤害的一定百分比
    return 30 + skillLevel * 5;
  }

  /**
   * 召唤抗性 - 增强召唤物抗性
   * 
   * @param skillLevel 技能等级
   * @return 所有抗性加成
   */
  public static int calculateSummonResistBonus(int skillLevel) {
    // 每级 +5% 所有抗性
    return Math.min(75, 5 * skillLevel);
  }

  /**
   * 钢铁石魔 - 从装备创建石魔
   * 
   * @param skillLevel 技能等级
   * @param itemDefense 装备防御值
   * @return 石魔生命值
   */
  public static int calculateIronGolemHp(int skillLevel, int itemDefense) {
    // 基础值 + 装备防御 * 2
    return 100 + skillLevel * 50 + itemDefense * 2;
  }

  /**
   * 烈火石魔 - 召唤火焰傀儡
   * 
   * @param skillLevel 技能等级
   * @return 石魔生命值
   */
  public static int calculateFireGolemHp(int skillLevel) {
    // 基础 400，每级 +100
    return 400 + (skillLevel - 1) * 100;
  }

  /**
   * 烈火石魔火焰伤害
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateFireGolemDamage(int skillLevel) {
    int minDamage = 6 + (skillLevel - 1) * 5;
    int maxDamage = 22 + (skillLevel - 1) * 8;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 复活 - 复活死亡怪物为己用
   * 
   * @param skillLevel 技能等级
   * @return 最大复活数
   */
  public static int getMaxRevives(int skillLevel) {
    // 每级 +1
    return skillLevel;
  }

  /**
   * 获取复活持续时间
   * 
   * @param skillLevel 技能等级
   * @return 持续时间（秒）
   */
  public static float getReviveDuration(int skillLevel) {
    // 固定 180 秒
    return 180.0f;
  }
}
