package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.Riiablo;
import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

import java.util.Locale;

/**
 * 亚马逊技能实现 - 基于 D2MOD SkillAma.cpp 移植
 * 
 * <p>包含标枪和长矛、被动和魔法、弓和弩三系技能的实现。
 * 
 * <p>参考：D2MOD/source/D2Game/src/SKILLS/SkillAma.cpp
 * 
 * @author riiablo team
 */
public final class AmazonSkills {
  private static final Logger log = LogManager.getLogger(AmazonSkills.class);

  private AmazonSkills() {} // 不可实例化

  /** Returns true for the two Amazon poison-javelin cast-delay skills. */
  public static boolean isPoisonJavelin(Skills.Entry skill) {
    if (skill == null) return false;
    if (skill.Id == SkillId.POISON_JAVELIN || skill.Id == SkillId.PLAGUE_JAVELIN) {
      return true;
    }
    if (skill.skill == null) return false;
    String name = skill.skill.trim();
    return "Poison Javelin".equalsIgnoreCase(name)
        || "Plague Javelin".equalsIgnoreCase(name);
  }

  //==========================================================================
  // 标枪和长矛技能
  //==========================================================================

  /**
   * 刺击 - 快速多次攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateJabDamageBonus(int skillLevel) {
    // 每级 +8%
    return 8 * skillLevel;
  }

  /**
   * 获取刺击攻击次数
   * 
   * @return 攻击次数
   */
  public static int getJabHitCount() {
    // 固定 3 次
    return 3;
  }

  /**
   * 能量一击 - 闪电伤害攻击
   * 
   * @param skillLevel 技能等级
   * @return 闪电伤害
   */
  public static int calculatePowerStrikeDamage(int skillLevel) {
    // 基础 1-40，每级 +1-8
    int minDamage = 1 + (skillLevel - 1);
    int maxDamage = 40 + (skillLevel - 1) * 8;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 刺穿 - 高伤害单次攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateImpaleDamageBonus(int skillLevel) {
    // 基础 70%，每级 +25%
    return 70 + (skillLevel - 1) * 25;
  }

  /**
   * 闪电矛 - 闪电伤害标枪
   * 
   * @param skillLevel 技能等级
   * @return 闪电伤害
   */
  public static int calculateLightningBoltDamage(int skillLevel) {
    // 基础 1-40，每级 +1-10
    int minDamage = 1 + (skillLevel - 1);
    int maxDamage = 40 + (skillLevel - 1) * 10;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 充电一击 - 释放闪电
   * 
   * @param skillLevel 技能等级
   * @return 每个闪电伤害
   */
  public static int calculateChargedStrikeDamage(int skillLevel) {
    // 基础 1-40，每级 +1-10
    int minDamage = 1 + (skillLevel - 1);
    int maxDamage = 40 + (skillLevel - 1) * 10;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 获取充电一击闪电数量
   * 
   * @param skillLevel 技能等级
   * @return 闪电数量
   */
  public static int getChargedStrikeBoltCount(int skillLevel) {
    // 基础 3，每 4 级 +1
    return 3 + skillLevel / 4;
  }

  /**
   * 回避 - 多次攻击
   * 
   * @param skillLevel 技能等级
   * @return 攻击次数
   */
  public static int getFendHitCount(int skillLevel) {
    // 基础 4 次，每 3 级 +1
    return 4 + skillLevel / 3;
  }

  /**
   * 闪电之怒 - 分裂闪电标枪
   * 
   * @param skillLevel 技能等级
   * @return 每支闪电伤害
   */
  public static int calculateLightningFuryDamage(int skillLevel) {
    // 基础 1-60，每级 +1-15
    int minDamage = 1 + (skillLevel - 1);
    int maxDamage = 60 + (skillLevel - 1) * 15;
    return MathUtils.random(minDamage, maxDamage);
  }

  //==========================================================================
  // 被动和魔法技能
  //==========================================================================

  /**
   * 内视 - 降低敌人防御
   * 
   * @param skillLevel 技能等级
   * @return 防御降低量
   */
  public static int calculateInnerSightDefenseReduce(int skillLevel) {
    // 基础 -40，每级 -20
    return 40 + (skillLevel - 1) * 20;
  }

  /**
   * 致命一击 - 双倍伤害概率
   * 
   * @param skillLevel 技能等级
   * @return 暴击概率百分比
   */
  public static int getCriticalStrikeChance(int skillLevel) {
    return nativePassiveValue("Critical Strike", skillLevel);
  }

  /**
   * 闪避 - 闪避近战攻击
   * 
   * @param skillLevel 技能等级
   * @return 闪避概率百分比
   */
  public static int getDodgeChance(int skillLevel) {
    return nativePassiveValue("Dodge", skillLevel);
  }

  /**
   * 减速飞弹 - 减慢敌人飞弹
   * 
   * @param skillLevel 技能等级
   * @return 减速百分比
   */
  public static int getSlowMissilesPercent(int skillLevel) {
    // 固定 33% 减速
    return 33;
  }

  /**
   * 躲避 - 闪避远程攻击
   * 
   * @param skillLevel 技能等级
   * @return 闪避概率百分比
   */
  public static int getAvoidChance(int skillLevel) {
    return nativePassiveValue("Avoid", skillLevel);
  }

  /**
   * 穿刺 - 增加攻击等级
   * 
   * @param skillLevel 技能等级
   * @return 攻击等级加成百分比
   */
  public static int calculatePenetrateBonus(int skillLevel) {
    return nativePassiveValue("Penetrate", skillLevel);
  }

  /**
   * 诱饵 - 创建诱饵吸引敌人
   * 
   * @param skillLevel 技能等级
   * @return 诱饵生命百分比（玩家生命的）
   */
  public static int getDecoyHpPercent(int skillLevel) {
    // 基础 50%，每级 +10%
    return 50 + (skillLevel - 1) * 10;
  }

  /**
   * 逃避 - 移动时闪避攻击
   * 
   * @param skillLevel 技能等级
   * @return 闪避概率百分比
   */
  public static int getEvadeChance(int skillLevel) {
    return nativePassiveValue("Evade", skillLevel);
  }

  /**
   * 女武神 - 召唤女武神
   * 
   * @param skillLevel 技能等级
   * @return 女武神等级
   */
  public static int getValkyrieLevel(int skillLevel) {
    return skillLevel;
  }

  /**
   * 穿透 - 攻击穿透概率
   * 
   * @param skillLevel 技能等级
   * @return 穿透概率百分比
   */
  public static int getPierceChance(int skillLevel) {
    return nativePassiveValue("Pierce", skillLevel);
  }

  /** Evaluates a native Amazon passive row through its Skills.txt formula. */
  private static int nativePassiveValue(String skillName, int skillLevel) {
    if (skillLevel <= 0 || Riiablo.files == null || Riiablo.files.skills == null) return 0;
    Skills.Entry skill = Riiablo.files.skills.get(skillName);
    if (skill == null || !skill.passive || skill.passivecalc == null) return 0;
    for (int i = 0; i < skill.passivecalc.length; i++) {
      if (skill.passivecalc[i] == null || skill.passivecalc[i].trim().isEmpty()) continue;
      return Math.max(0, SkillFormula.evaluate(
          skill.passivecalc[i], skill, Math.max(1, skillLevel)));
    }
    return 0;
  }

  /** Maps the six permanent Amazon passive stat-list states from States.txt. */
  public static int getPassiveStateId(Skills.Entry skill) {
    if (skill == null || skill.passivestate == null) return StateId.NONE;
    switch (skill.passivestate.trim().toLowerCase(Locale.ROOT)) {
      case "criticalstrike": return StateId.CRITICALSTRIKE;
      case "dodge": return StateId.DODGE;
      case "avoid": return StateId.AVOID;
      case "penetrate": return StateId.PENETRATE;
      case "evade": return StateId.EVADE;
      case "pierce": return StateId.PIERCE;
      default: return StateId.NONE;
    }
  }

  /** Builds the D2Common passive stat-list directly from passivestat/passivecalc. */
  public static UnitState applyPassiveState(
      StateList states, Skills.Entry skill, int skillLevel, int ownerId) {
    int stateId = getPassiveStateId(skill);
    if (states == null || skill == null || !skill.passive || skillLevel <= 0
        || stateId == StateId.NONE) return null;
    UnitState state = states.addStateLayer(stateId, 0, skillLevel, ownerId, skill.Id);
    if (state == null) return null;
    state.duration = 0;
    state.initialDuration = 0;
    state.level = skillLevel;
    state.sourceEntityId = ownerId;
    state.skillId = skill.Id;
    state.basicStatList = true;
    state.clearModifiers();
    int count = Math.min(skill.passivestat != null ? skill.passivestat.length : 0,
        skill.passivecalc != null ? skill.passivecalc.length : 0);
    for (int i = 0; i < count; i++) {
      String statName = skill.passivestat[i];
      String formula = skill.passivecalc[i];
      if (statName == null || statName.trim().isEmpty()
          || formula == null || formula.trim().isEmpty()) continue;
      int statId = Stat.index(statName.trim());
      if (statId < 0) continue;
      int value = SkillFormula.evaluate(formula, skill, skillLevel);
      state.setStatContribution(statId, 0, NativeStatResolver.Operation.ADD, value);
    }
    state.needsSync = true;
    return state;
  }

  //==========================================================================
  // 弓和弩技能
  //==========================================================================

  /**
   * 魔法箭 - 无消耗弹药的魔法攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateMagicArrowDamageBonus(int skillLevel) {
    // 每级 +4%
    return 4 * skillLevel;
  }

  /**
   * 火焰箭 - 火焰伤害攻击
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateFireArrowDamage(int skillLevel) {
    // 基础 3-7，每级 +2-3
    int minDamage = 3 + (skillLevel - 1) * 2;
    int maxDamage = 7 + (skillLevel - 1) * 3;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 冰箭 - 冰冷伤害攻击
   * 
   * @param skillLevel 技能等级
   * @return 冰冷伤害
   */
  public static int calculateColdArrowDamage(int skillLevel) {
    // 基础 6-10，每级 +3-4
    int minDamage = 6 + (skillLevel - 1) * 3;
    int maxDamage = 10 + (skillLevel - 1) * 4;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 多重箭 - 发射多支箭矢
   * 
   * @param skillLevel 技能等级
   * @return 箭矢数量
   */
  public static int getMultipleShotCount(int skillLevel) {
    // 基础 4，每 2 级 +1
    return 4 + skillLevel / 2;
  }

  /**
   * 爆炸箭 - 爆炸火焰伤害
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateExplodingArrowDamage(int skillLevel) {
    // 基础 10-18，每级 +5-6
    int minDamage = 10 + (skillLevel - 1) * 5;
    int maxDamage = 18 + (skillLevel - 1) * 6;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 冰封箭 - 冻结并造成冰冷伤害
   * 
   * @param skillLevel 技能等级
   * @return 冰冷伤害
   */
  public static int calculateIceArrowDamage(int skillLevel) {
    // 基础 18-26，每级 +8-10
    int minDamage = 18 + (skillLevel - 1) * 8;
    int maxDamage = 26 + (skillLevel - 1) * 10;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 导引箭 - 追踪敌人的箭矢
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateGuidedArrowDamageBonus(int skillLevel) {
    // 每级 +5%
    return 5 * skillLevel;
  }

  /**
   * 扫射 - 快速连续射击
   * 
   * @param skillLevel 技能等级
   * @return 箭矢数量
   */
  public static int getStrafeArrowCount(int skillLevel) {
    return getStrafeMaxArrows(skillLevel);
  }

  /** Native minimum arrow count when fewer hostile units are available. */
  public static int getStrafeMinArrows(int skillLevel) {
    int level = Math.max(1, skillLevel);
    return Math.min(getStrafeMaxArrows(level), 2 + level / 4);
  }

  /** Native maximum arrow count: five at level 1, +1 per level, capped at ten. */
  public static int getStrafeMaxArrows(int skillLevel) {
    int level = Math.max(1, skillLevel);
    return Math.min(10, 4 + level);
  }

  /**
   * 祭火之箭 - 高伤害火焰箭
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateImmolationArrowDamage(int skillLevel) {
    // 基础 50-70，每级 +12-15
    int minDamage = 50 + (skillLevel - 1) * 12;
    int maxDamage = 70 + (skillLevel - 1) * 15;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 冰冻箭 - 范围冻结
   * 
   * @param skillLevel 技能等级
   * @return 冰冷伤害
   */
  public static int calculateFreezingArrowDamage(int skillLevel) {
    // 基础 40-60，每级 +12-15
    int minDamage = 40 + (skillLevel - 1) * 12;
    int maxDamage = 60 + (skillLevel - 1) * 15;
    return MathUtils.random(minDamage, maxDamage);
  }
}
