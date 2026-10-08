package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
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

  /** Native SrvDo002/SrvDo011 skills that drain the equipped weapon on hit. */
  public static boolean usesNativeMeleeDurability(Skills.Entry skill) {
    return skill != null
        && (skill.Id == SkillId.POWER_STRIKE || skill.Id == SkillId.CHARGED_STRIKE);
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

  /** Native SKILLS_GetToHitFactor contribution for Amazon melee skills. */
  public static int getAttackRating(Skills.Entry skill, int skillLevel,
      Attributes attacker, boolean player) {
    int base = statInt(attacker, Stat.tohit);
    int level = Math.max(1, skillLevel);
    int factor = skill == null ? 0 : skill.ToHit + (level - 1) * skill.LevToHit;
    return player ? Math.max(1, base * Math.max(0, 100 + factor) / 100)
        : Math.max(1, base + factor);
  }

  /**
   * Native player attack rating used by SUNITDMG_IsHitSuccessful.  D2MOO
   * builds the player's base rate with dexterity and the class ToHitFactor
   * before applying Skills.txt ToHit as a percentage.
   */
  public static int getPlayerAttackRating(Skills.Entry skill, int skillLevel,
      Attributes attacker, int classToHitFactor) {
    int level = Math.max(1, skillLevel);
    int factor = skill == null ? 0 : skill.ToHit + (level - 1) * skill.LevToHit;
    int base = statInt(attacker, Stat.tohit)
        + 5 * (statInt(attacker, Stat.dexterity) - 7)
        + classToHitFactor;
    return Math.max(1, base * Math.max(0, 100 + factor) / 100);
  }

  /** Resolves the native physical percentage field from Skills.txt. */
  public static int getPhysicalDamagePercent(Skills.Entry skill, int skillLevel) {
    if (skill == null) return 0;
    String formula = skill.Id == SkillId.FEND ? skill.calc2 : skill.calc1;
    if (formula != null && !formula.trim().isEmpty()) {
      return SkillFormula.evaluate(formula, skill, Math.max(1, skillLevel));
    }
    switch (skill.Id) {
      case SkillId.JAB: return calculateJabDamageBonus(skillLevel);
      case SkillId.IMPALE: return calculateImpaleDamageBonus(skillLevel);
      case SkillId.FEND: return 70 + (Math.max(1, skillLevel) - 1) * 4;
      case SkillId.POWER_STRIKE:
      case SkillId.CHARGED_STRIKE:
        return SkillFormula.evaluate(skill.calc1, skill, Math.max(1, skillLevel));
      default: return 0;
    }
  }

  /** Complete weapon packet used by Jab, Impale and Fend. */
  public static int[] calculateWeaponDamage(Skills.Entry skill, int skillLevel,
      Attributes attacker, Item weapon, StateList states) {
    if (attacker == null) return new int[] {0, 0};
    int min;
    int max;
    int attributePercent;
    if (weapon != null && weapon.base instanceof Weapons.Entry) {
      Weapons.Entry base = (Weapons.Entry) weapon.base;
      min = itemStatInt(weapon, Stat.mindamage, base.mindam);
      max = itemStatInt(weapon, Stat.maxdamage, Math.max(min, base.maxdam));
      attributePercent = base.StrBonus * statInt(attacker, Stat.strength) / 100
          + base.DexBonus * statInt(attacker, Stat.dexterity) / 100;
    } else {
      min = Math.max(0, statInt(attacker, Stat.mindamage));
      max = Math.max(min, statInt(attacker, Stat.maxdamage));
      attributePercent = statInt(attacker, Stat.strength);
    }
    // D2MOO SUNITDMG_ApplyDamageBonuses seeds an empty weapon packet with
    // 1..2 damage before skill/source scaling.  Keeping this invariant here
    // avoids a zero-damage packet when an item row has no explicit damage.
    if (min < 1) min = 1;
    if (max <= min) max = min + 1;
    int percent = getPhysicalDamagePercent(skill, skillLevel) + attributePercent
        + statInt(attacker, Stat.damagepercent)
        + statInt(attacker, Stat.item_maxdamage_percent);
    if (states != null) {
      percent += states.getTotalDamageModifier();
      if (weapon != null) {
        percent += states.getWeaponMastery(weapon, false,
            new StateList.WeaponMasteryBonus()).damagePercent;
      }
    }
    int sourceDamage = skill == null || skill.SrcDam == 0 ? 128 : skill.SrcDam;
    return new int[] {
        scaleSource(scalePercent(min, percent), sourceDamage),
        scaleSource(scalePercent(Math.max(min, max), percent), sourceDamage)
    };
  }

  private static int statInt(Attributes attrs, short stat) {
    if (attrs == null) return 0;
    StatRef ref = attrs.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }

  private static int itemStatInt(Item item, short stat, int fallback) {
    if (item == null || item.attrs == null) return fallback;
    StatRef ref = item.attrs.get(stat, StatRef.obtain());
    if (ref == null) ref = item.attrs.base().get(stat, StatRef.obtain());
    return ref == null ? fallback : ref.asInt();
  }

  private static int scalePercent(int value, int percent) {
    percent = Math.max(-90, percent);
    return Math.max(0, (int) Math.min(Integer.MAX_VALUE,
        (long) Math.max(0, value) * (100L + percent) / 100L));
  }

  private static int scaleSource(int value, int sourceDamage) {
    return Math.max(0, (int) Math.min(Integer.MAX_VALUE,
        (long) Math.max(0, value) * Math.max(0, sourceDamage) / 128L));
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
    Skills.Entry skill = Riiablo.files == null ? null : Riiablo.files.skills.get("Inner Sight");
    return calculateInnerSightDefenseReduce(skill, skillLevel);
  }

  /** Evaluates Inner Sight's native {@code -edmn} segmented armor penalty. */
  public static int calculateInnerSightDefenseReduce(Skills.Entry skill, int skillLevel) {
    if (skill != null && skill.aurastatcalc != null && skill.aurastatcalc.length > 0) {
      int value = SkillFormula.evaluate(skill.aurastatcalc[0], skill, skillLevel);
      if (value < 0) return -value;
    }
    // Guard for stripped/modded rows that omit AuraStatCalc.
    return 40 + Math.max(0, skillLevel - 1) * 20;
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

  /** Native Fend attack count (calc1), capped by the target stream at start. */
  public static int getFendHitCount(Skills.Entry skill, int skillLevel) {
    if (skill == null || skill.Id != SkillId.FEND) return 0;
    int value = SkillFormula.evaluate(skill.calc1, skill, Math.max(1, skillLevel));
    return Math.max(1, value > 0 ? value : getFendHitCount(skillLevel));
  }

  /** Returns true for the native Slow Missiles row (SrvDo006). */
  public static boolean isSlowMissiles(Skills.Entry skill) {
    return skill != null && (skill.Id == SkillId.SLOW_MISSILES
        || skill.skill != null && "Slow Missiles".equalsIgnoreCase(skill.skill.trim()));
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
    // Native Skills.txt Calc1=ln34: 0% at level 1, then +5% per level.
    return 5 * (Math.max(1, skillLevel) - 1);
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
