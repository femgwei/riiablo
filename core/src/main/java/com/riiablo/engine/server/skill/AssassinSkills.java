package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
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
  public static final int MAX_PROGRESSIVE_CHARGES = 3;
  private static final int DEFAULT_PROGRESSIVE_DURATION = 250;

  private static final int[] PROGRESSIVE_STATES = {
      StateId.PROGRESSIVE_DAMAGE,
      StateId.PROGRESSIVE_STEAL,
      StateId.PROGRESSIVE_OTHER,
      StateId.PROGRESSIVE_FIRE,
      StateId.PROGRESSIVE_COLD,
      StateId.PROGRESSIVE_LIGHTNING
  };

  private AssassinSkills() {} // 不可实例化

  //==========================================================================
  // 武技 - 充能技
  //==========================================================================

  /** Returns whether the native server function is an Assassin charge-up strike. */
  public static boolean isProgressiveStrike(int srvDoFunc) {
    return srvDoFunc == 34 || srvDoFunc == 35;
  }

  /** Native Assassin finishing moves which call sub_6FCF77E0. */
  public static boolean isFinishingMove(int srvDoFunc) {
    return srvDoFunc == 42 || srvDoFunc == 46 || srvDoFunc == 50 || srvDoFunc == 52;
  }

  /**
   * Resolves Skills.txt AuraState to the native progressive state id.
   *
   * <p>The textual lookup is preferred because it preserves modded Skills.txt
   * rows. Skill-name fallbacks only cover the original rows whose older bin
   * cache did not expose AuraState.</p>
   */
  public static int progressiveStateId(Skills.Entry skill) {
    if (skill == null) return StateId.NONE;
    String auraState = normalize(skill.aurastate);
    if (auraState.contains("progressivedamage")) return StateId.PROGRESSIVE_DAMAGE;
    if (auraState.contains("progressivesteal")) return StateId.PROGRESSIVE_STEAL;
    if (auraState.contains("progressiveother")) return StateId.PROGRESSIVE_OTHER;
    if (auraState.contains("progressivefire")) return StateId.PROGRESSIVE_FIRE;
    if (auraState.contains("progressivecold")) return StateId.PROGRESSIVE_COLD;
    if (auraState.contains("progressivelightning")) return StateId.PROGRESSIVE_LIGHTNING;

    String name = normalize(skill.skill);
    if (name.contains("tigerstrike")) return StateId.PROGRESSIVE_DAMAGE;
    if (name.contains("cobrastrike")) return StateId.PROGRESSIVE_STEAL;
    if (name.contains("royalstrike") || name.contains("phoenixstrike")) {
      return StateId.PROGRESSIVE_OTHER;
    }
    if (name.contains("fistsoffire")) return StateId.PROGRESSIVE_FIRE;
    if (name.contains("bladesofice")) return StateId.PROGRESSIVE_COLD;
    if (name.contains("clawsofthunder")) return StateId.PROGRESSIVE_LIGHTNING;
    return StateId.NONE;
  }

  /**
   * D2MOO SrvDo034/SrvDo035 charge-list update.
   *
   * <p>The generic {@link UnitState#velocityModifier} network scalar carries
   * the charge count. Progressive states are explicitly excluded from movement
   * aggregation by {@code StateList}; this keeps the existing StateP wire
   * format compatible while making the authoritative stage available to
   * every client renderer.</p>
   */
  public static UnitState addProgressiveCharge(StateList states, Skills.Entry skill,
      int skillLevel, int sourceEntityId) {
    if (states == null || skill == null) return null;
    int stateId = progressiveStateId(skill);
    if (stateId == StateId.NONE) return null;
    int duration = SkillFormula.evaluate(skill.auralencalc, skill, skillLevel);
    if (duration <= 0) duration = DEFAULT_PROGRESSIVE_DURATION;
    UnitState old = states.getState(stateId);
    int oldCharges = old != null ? progressiveCharges(old) : 0;
    UnitState state = states.addState(stateId, duration, Math.max(1, skillLevel), sourceEntityId);
    if (state == null) return null;
    state.duration = duration;
    state.initialDuration = duration;
    state.sourceEntityId = sourceEntityId;
    state.skillId = skill.Id;
    state.velocityModifier = Math.min(MAX_PROGRESSIVE_CHARGES, oldCharges + 1);
    state.needsSync = true;
    log.info("[ASSASSIN_CHARGE] phase=apply source={} skill={} state={} level={} charges={} duration={}",
        sourceEntityId, skill.skill, StateId.getName(stateId), skillLevel,
        state.velocityModifier, duration);
    return state;
  }

  public static int progressiveCharges(UnitState state) {
    if (state == null || !isProgressiveState(state.stateId)) return 0;
    return Math.max(0, Math.min(MAX_PROGRESSIVE_CHARGES, state.velocityModifier));
  }

  public static int progressiveCharges(StateList states, int stateId) {
    return states == null ? 0 : progressiveCharges(states.getState(stateId));
  }

  public static boolean isProgressiveState(int stateId) {
    for (int progressiveState : PROGRESSIVE_STATES) {
      if (progressiveState == stateId) return true;
    }
    return false;
  }

  /** Removes every charge-up state after a successful finishing-move hit. */
  public static int consumeProgressiveCharges(StateList states) {
    if (states == null) return 0;
    int consumed = 0;
    for (int stateId : PROGRESSIVE_STATES) {
      UnitState state = states.getState(stateId);
      if (state == null) continue;
      consumed += progressiveCharges(state);
      states.removeState(stateId);
    }
    return consumed;
  }

  /** Immutable aggregate read before a successful finisher consumes all charge states. */
  public static final class ProgressiveRelease {
    public int totalCharges;
    public int tigerDamagePercent;
    public int lifeLeechPercent;
    public int manaLeechPercent;
    public int fireMinDamage;
    public int fireMaxDamage;
    public int fireConversionPercent;
    public int fireCharges;
    public int fireSkillId = -1;
    public int fireSkillLevel;
    public int firePhysicalMinDamage;
    public int firePhysicalMaxDamage;
    public int fireSourceDamageScale;
    public int fireAreaRange;
    public int fireFieldRange;
    public String fireStageMissile;

    public boolean hasEffects() {
      return totalCharges > 0;
    }
  }

  /**
   * D2MOO sub_6FCF5680/sub_6FCF5870 preparation for Tiger, Cobra and Fists.
   * State removal remains a separate operation so misses and blocks can never
   * consume charges.
   */
  public static ProgressiveRelease resolveProgressiveRelease(StateList states,
      IntFunction<Skills.Entry> skillResolver, IntUnaryOperator skillLevelResolver) {
    ProgressiveRelease release = new ProgressiveRelease();
    if (states == null || skillResolver == null) return release;
    for (int stateId : PROGRESSIVE_STATES) {
      UnitState state = states.getState(stateId);
      int charges = progressiveCharges(state);
      if (charges <= 0) continue;
      release.totalCharges += charges;
      Skills.Entry skill = skillResolver.apply(state.skillId);
      if (skill == null) continue;
      int currentLevel = skillLevelResolver != null
          ? skillLevelResolver.applyAsInt(state.skillId) : 0;
      int level = Math.max(1, Math.max(state.level, currentLevel));
      switch (stateId) {
        case StateId.PROGRESSIVE_DAMAGE:
          release.tigerDamagePercent += calculateTigerStrikeDamageBonus(
              skill, level, charges);
          break;
        case StateId.PROGRESSIVE_STEAL:
          int[] steal = calculateCobraStrikeSteal(skill, level, charges);
          release.lifeLeechPercent += steal[0];
          release.manaLeechPercent += steal[1];
          break;
        case StateId.PROGRESSIVE_FIRE:
          release.fireCharges = charges;
          release.fireSkillId = skill.Id;
          release.fireSkillLevel = level;
          release.fireMinDamage += shiftedSkillDamage(
              skill.EMin, skill.EMinLev, level, skill.HitShift);
          release.fireMaxDamage += shiftedSkillDamage(
              skill.EMax, skill.EMaxLev, level, skill.HitShift);
          release.firePhysicalMinDamage += shiftedSkillDamage(
              skill.MinDam, skill.MinLevDam, level, skill.HitShift);
          release.firePhysicalMaxDamage += shiftedSkillDamage(
              skill.MaxDam, skill.MaxLevDam, level, skill.HitShift);
          release.fireSourceDamageScale = Math.max(
              release.fireSourceDamageScale, Math.max(0, skill.SrcDam));
          release.fireConversionPercent = Math.max(release.fireConversionPercent,
              Math.min(100, Math.max(0, SkillFormula.evaluate(skill.calc1, skill, level))));
          if (charges >= 2) {
            release.fireAreaRange = progressiveRange(skill, level, 2);
          }
          if (charges >= 3) {
            release.fireFieldRange = progressiveRange(skill, level, 3);
            release.fireStageMissile = progressiveMissile(skill, 3);
          }
          break;
        default:
          // Cold, lightning and Phoenix stage functions are handled by the
          // following martial-arts module; they are still counted so the
          // native consume-all operation remains observable in logs/tests.
          break;
      }
    }
    release.fireMaxDamage = Math.max(release.fireMinDamage, release.fireMaxDamage);
    release.firePhysicalMaxDamage = Math.max(
        release.firePhysicalMinDamage, release.firePhysicalMaxDamage);
    return release;
  }

  /** D2MOO SKILLS_EvaluateProgressiveSkillCalc with AuraRangeCalc fallback. */
  public static int progressiveRange(Skills.Entry skill, int skillLevel, int chargeLevel) {
    if (skill == null) return 0;
    int index = Math.max(1, Math.min(MAX_PROGRESSIVE_CHARGES, chargeLevel)) - 1;
    String expression = skill.prgcalc != null && index < skill.prgcalc.length
        ? skill.prgcalc[index] : null;
    int range = SkillFormula.evaluate(expression, skill, skillLevel);
    if (range <= 0) range = SkillFormula.evaluate(skill.aurarangecalc, skill, skillLevel);
    // Original rows always provide one of these formulas. Keep legacy bin
    // caches playable until they are regenerated with PrgCalc columns.
    return range > 0 ? range : 3;
  }

  /** D2MOO SKILLS_GetProgressiveSkillMissileId stage lookup. */
  public static String progressiveMissile(Skills.Entry skill, int chargeLevel) {
    if (skill == null) return null;
    switch (Math.max(1, Math.min(MAX_PROGRESSIVE_CHARGES, chargeLevel))) {
      case 1: return firstNonEmpty(skill.srvmissilea, skill.srvmissile);
      case 2: return firstNonEmpty(skill.srvmissileb, skill.srvmissilea);
      default: return firstNonEmpty(skill.srvmissilec, skill.srvmissileb);
    }
  }

  public static int rollFireDamage(ProgressiveRelease release) {
    if (release == null || release.fireMaxDamage <= 0) return 0;
    return MathUtils.random(Math.max(0, release.fireMinDamage), release.fireMaxDamage);
  }

  static int shiftedSkillDamage(int base, int[] perLevel, int level, int hitShift) {
    long value = Math.max(0L, (long) base + damageBonusByLevel(level, perLevel));
    int shift = hitShift - 8;
    if (shift > 0) value <<= Math.min(shift, 30);
    else if (shift < 0) value >>= Math.min(-shift, 30);
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
  }

  static int damageBonusByLevel(int level, int[] values) {
    if (level <= 1 || values == null || values.length == 0) return 0;
    int l1 = arrayValue(values, 0);
    int l2 = arrayValue(values, 1);
    int l3 = arrayValue(values, 2);
    int l4 = arrayValue(values, 3);
    int l5 = arrayValue(values, 4);
    if (level > 28) return 7 * l1 + 8 * l2 + 6 * (l3 + l4) + (level - 28) * l5;
    if (level > 22) return 7 * l1 + 8 * l2 + 6 * l3 + (level - 22) * l4;
    if (level > 16) return 7 * l1 + 8 * l2 + (level - 16) * l3;
    if (level > 8) return 7 * l1 + (level - 8) * l2;
    return (level - 1) * l1;
  }

  private static int arrayValue(int[] values, int index) {
    return values != null && index >= 0 && index < values.length ? values[index] : 0;
  }

  private static String firstNonEmpty(String preferred, String fallback) {
    return preferred != null && !preferred.isEmpty() ? preferred : fallback;
  }

  /** Native Tiger Strike enhanced-damage contribution (calc1 per charge). */
  public static int calculateTigerStrikeDamageBonus(
      Skills.Entry skill, int skillLevel, int chargeLevel) {
    int perCharge = SkillFormula.evaluate(skill != null ? skill.calc1 : null, skill, skillLevel);
    if (perCharge <= 0) perCharge = 100 + (Math.max(1, skillLevel) - 1) * 20;
    return perCharge * Math.max(0, Math.min(MAX_PROGRESSIVE_CHARGES, chargeLevel));
  }

  /** Returns {lifeLeechPercent, manaLeechPercent} for Cobra Strike charges. */
  public static int[] calculateCobraStrikeSteal(
      Skills.Entry skill, int skillLevel, int chargeLevel) {
    int base = param(skill, 0, 40) + (Math.max(1, skillLevel) - 1) * param(skill, 1, 5);
    int charges = Math.max(0, Math.min(MAX_PROGRESSIVE_CHARGES, chargeLevel));
    switch (charges) {
      case 1: return new int[] {base, 0};
      case 2: return new int[] {base, base};
      case 3: return new int[] {base * 2, base * 2};
      default: return new int[] {0, 0};
    }
  }

  private static int param(Skills.Entry skill, int index, int fallback) {
    if (skill == null || skill.Param == null || index < 0 || index >= skill.Param.length) {
      return fallback;
    }
    return skill.Param[index];
  }

  private static String normalize(String value) {
    if (value == null) return "";
    return value.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace(" ", "");
  }

  /**
   * 虎击 - 累积充能
   * 
   * @param skillLevel 技能等级
   * @param chargeLevel 当前充能级别（1-3）
   * @return 伤害加成百分比
   */
  public static int calculateTigerStrikeDamageBonus(int skillLevel, int chargeLevel) {
    return calculateTigerStrikeDamageBonus(null, skillLevel, chargeLevel);
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
    return chargeLevel >= 3 ? baseSteal * 2 : chargeLevel > 0 ? baseSteal : 0;
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
