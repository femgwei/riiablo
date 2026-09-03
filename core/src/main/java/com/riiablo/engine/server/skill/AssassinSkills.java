package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Armor;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.item.Item;
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
    public int lightningCharges;
    public int lightningSkillId = -1;
    public int lightningSkillLevel;
    public int lightningMinDamage;
    public int lightningMaxDamage;
    public String lightningNovaMissile;
    public String lightningBoltMissile;
    public int lightningBoltStep;
    public int coldCharges;
    public int coldSkillId = -1;
    public int coldSkillLevel;
    public int coldMinDamage;
    public int coldMaxDamage;
    public int coldPhysicalMinDamage;
    public int coldPhysicalMaxDamage;
    public int coldSourceDamageScale;
    public int coldLength;
    public int coldFreezeDuration;
    public int coldAreaRange;
    public int coldCubeRange;
    public String coldCubeMissile;
    public int phoenixCharges;
    public int phoenixSkillId = -1;
    public int phoenixSkillLevel;
    public int phoenixStageValue;
    public String phoenixStageMissile;

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
        case StateId.PROGRESSIVE_LIGHTNING:
          release.lightningCharges = charges;
          release.lightningSkillId = skill.Id;
          release.lightningSkillLevel = level;
          release.lightningMinDamage += shiftedSkillDamage(
              skill.EMin, skill.EMinLev, level, skill.HitShift);
          release.lightningMaxDamage += shiftedSkillDamage(
              skill.EMax, skill.EMaxLev, level, skill.HitShift);
          if (charges >= 2) {
            release.lightningNovaMissile = progressiveMissile(skill, 2);
          }
          if (charges >= 3) {
            release.lightningBoltMissile = progressiveMissile(skill, 3);
            release.lightningBoltStep = progressiveRange(skill, level, 3);
          }
          break;
        case StateId.PROGRESSIVE_COLD:
          release.coldCharges = charges;
          release.coldSkillId = skill.Id;
          release.coldSkillLevel = level;
          release.coldMinDamage += shiftedSkillDamage(
              skill.EMin, skill.EMinLev, level, skill.HitShift);
          release.coldMaxDamage += shiftedSkillDamage(
              skill.EMax, skill.EMaxLev, level, skill.HitShift);
          release.coldPhysicalMinDamage += shiftedSkillDamage(
              skill.MinDam, skill.MinLevDam, level, skill.HitShift);
          release.coldPhysicalMaxDamage += shiftedSkillDamage(
              skill.MaxDam, skill.MaxLevDam, level, skill.HitShift);
          release.coldSourceDamageScale = Math.max(
              release.coldSourceDamageScale, Math.max(0, skill.SrcDam));
          release.coldLength = Math.max(release.coldLength,
              Math.max(0, skill.ELen + damageBonusByLevel(level, skill.ELevLen)));
          // sub_6FCF5BC0 freezes only the charge-three primary hit. Param5
          // divides cold length; the original Blades row uses one.
          if (charges == 3) {
            int divisor = param(skill, 4, 0);
            if (divisor > 0) release.coldFreezeDuration = release.coldLength / divisor;
          }
          if (charges >= 2) release.coldAreaRange = progressiveRange(skill, level, 2);
          if (charges >= 3) {
            release.coldCubeRange = progressiveRange(skill, level, 3);
            release.coldCubeMissile = progressiveMissile(skill, 3);
          }
          break;
        case StateId.PROGRESSIVE_OTHER:
          release.phoenixCharges = charges;
          release.phoenixSkillId = skill.Id;
          release.phoenixSkillLevel = level;
          release.phoenixStageValue = progressiveRange(skill, level, charges);
          release.phoenixStageMissile = progressiveMissile(skill, charges);
          break;
        default:
          // Unknown progressive states are still counted so the native
          // consume-all operation remains observable in logs/tests.
          break;
      }
    }
    release.fireMaxDamage = Math.max(release.fireMinDamage, release.fireMaxDamage);
    release.firePhysicalMaxDamage = Math.max(
        release.firePhysicalMinDamage, release.firePhysicalMaxDamage);
    release.lightningMaxDamage = Math.max(
        release.lightningMinDamage, release.lightningMaxDamage);
    release.coldMaxDamage = Math.max(release.coldMinDamage, release.coldMaxDamage);
    release.coldPhysicalMaxDamage = Math.max(
        release.coldPhysicalMinDamage, release.coldPhysicalMaxDamage);
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

  public static int rollLightningDamage(ProgressiveRelease release) {
    if (release == null || release.lightningMaxDamage <= 0) return 0;
    return MathUtils.random(
        Math.max(0, release.lightningMinDamage), release.lightningMaxDamage);
  }

  public static int rollColdDamage(ProgressiveRelease release) {
    if (release == null || release.coldMaxDamage <= 0) return 0;
    return MathUtils.random(Math.max(0, release.coldMinDamage), release.coldMaxDamage);
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

  /** D2MOO sub_6FCF7BC0: Param1 + (level - 1) * Param2. */
  public static int calculateDragonTalonDamageBonus(Skills.Entry skill, int skillLevel) {
    return param(skill, 0, 0)
        + (Math.max(1, skillLevel) - 1) * param(skill, 1, 0);
  }

  /** Kept for callers which only have the original Skills.txt constants. */
  public static int calculateDragonTalonDamageBonus(int skillLevel) {
    return 5 + (Math.max(1, skillLevel) - 1) * 7;
  }

  /**
   * 获取龙爪踢击次数
   * 
   * @param skillLevel 技能等级
   * @return 踢击次数
   */
  public static int getDragonTalonKickCount(Skills.Entry skill, int skillLevel) {
    int kicks = SkillFormula.evaluate(skill != null ? skill.calc1 : null,
        skill, Math.max(1, skillLevel));
    // The original row is lvl/6+1. A fallback keeps older bin caches usable.
    if (kicks <= 0) kicks = Math.max(1, skillLevel) / 6 + 1;
    return Math.max(1, kicks);
  }

  public static int getDragonTalonKickCount(int skillLevel) {
    return Math.max(1, skillLevel) / 6 + 1;
  }

  /** Native SKILLS_GetToHitFactor contribution for Dragon Talon. */
  public static int dragonTalonAttackRating(
      Skills.Entry skill, int skillLevel, Attributes attacker) {
    int base = statInt(attacker, Stat.tohit);
    int progressive = statInt(attacker, Stat.progressive_tohit);
    int level = Math.max(1, skillLevel);
    int skillFactor = skill == null ? 0 : skill.ToHit + (level - 1) * skill.LevToHit;
    return Math.max(1, base + progressive + skillFactor);
  }

  /**
   * D2MOO SKILLS_CalculateKickDamage plus sub_6FCF7CE0 physical composition.
   * Returned values are already fully enhanced and must use the combat
   * pipeline's precomputed-physical entry point.
   */
  public static int[] calculateDragonTalonKickDamage(
      Skills.Entry skill, int skillLevel, Attributes attacker, Armor.Entry boots) {
    int level = Math.max(1, skillLevel);
    return calculateNativeKickDamage(
        attacker, boots, calculateDragonTalonDamageBonus(skill, level));
  }

  /**
   * D2Common SKILLS_GetMin/MaxPhysDamage(KICK) plus SKILLS_CalculateKickDamage.
   * Dragon Tail enters this helper with zero skill ED; its calc1 belongs to
   * the later fire explosion, not the primary kick.
   */
  public static int[] calculateDragonTailKickDamage(
      Skills.Entry skill, int skillLevel, Attributes attacker, Armor.Entry boots) {
    return calculateNativeKickDamage(attacker, boots, 0);
  }

  private static int[] calculateNativeKickDamage(
      Attributes attacker, Armor.Entry boots, int initialEnhancedPercent) {
    int strength = statInt(attacker, Stat.strength);
    int dexterity = statInt(attacker, Stat.dexterity);
    int kickAttribute = Math.max(1, strength + dexterity - 20);
    int baseMin = kickAttribute / 4;
    int baseMax = kickAttribute / 3;
    int itemKick = statInt(attacker, Stat.item_kickdamage);

    // SKILLS_CalculateKickDamage adds ITEM_KICKDAMAGE once unconditionally
    // and a second time as part of the equipped boot damage record.
    int kickMin = itemKick;
    int kickMax = itemKick;
    int kickPercent = initialEnhancedPercent;
    if (boots != null) {
      kickMin += itemKick + boots.mindam;
      kickMax += itemKick + boots.maxdam;
      int attributePercent = boots.StrBonus * statInt(attacker, Stat.strength) / 100
          + boots.DexBonus * statInt(attacker, Stat.dexterity) / 100
          + statInt(attacker, Stat.damagepercent);
      attributePercent = Math.max(-90, attributePercent);
      kickPercent += statInt(attacker, Stat.item_maxdamage_percent) + attributePercent;
    }

    int min = scale(baseMin, initialEnhancedPercent) + scale(kickMin, kickPercent);
    int max = scale(Math.max(baseMin, baseMax), initialEnhancedPercent)
        + scale(Math.max(kickMin, kickMax), kickPercent);
    return new int[] {Math.max(0, min), Math.max(Math.max(0, min), max)};
  }

  /** Native last-kick target class selects calc2/calc3/calc4. */
  public static int dragonTalonKnockbackChance(
      Skills.Entry skill, int skillLevel, boolean playerOrHireling,
      boolean boss, boolean unique) {
    if (skill == null) return 0;
    String expression = playerOrHireling ? skill.calc4
        : boss ? skill.calc3 : unique ? skill.calc2 : null;
    if (expression == null || expression.isEmpty()) return 100;
    return Math.max(0, Math.min(100,
        SkillFormula.evaluate(expression, skill, Math.max(1, skillLevel))));
  }

  private static int scale(int damage, int percent) {
    long result = (long) damage + (long) damage * percent / 100L;
    return result <= 0 ? 0 : result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
  }

  private static int statInt(Attributes attrs, short stat) {
    if (attrs == null) return 0;
    StatRef ref = attrs.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }

  /**
   * 双龙爪 - 双武器攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateDragonClawDamageBonus(int skillLevel) {
    return 50 + Math.max(1, skillLevel) * 5;
  }

  /** D2MOO sub_6FCF8C70: calc1 is added to the selected claw's ED. */
  public static int calculateDragonClawDamageBonus(Skills.Entry skill, int skillLevel) {
    int value = SkillFormula.evaluate(skill != null ? skill.calc1 : null,
        skill, Math.max(1, skillLevel));
    return value != 0 ? value : calculateDragonClawDamageBonus(skillLevel);
  }

  public static int dragonClawAttackRating(
      Skills.Entry skill, int skillLevel, Attributes attacker) {
    return dragonTalonAttackRating(skill, skillLevel, attacker);
  }

  /** Fully scaled physical range for one hand of native Dragon Claw. */
  public static int[] calculateDragonClawDamage(
      Skills.Entry skill, int skillLevel, Attributes attacker, Item claw) {
    if (claw == null || !(claw.base instanceof Weapons.Entry)) return null;
    Weapons.Entry weapon = (Weapons.Entry) claw.base;
    int min = itemStatInt(claw, Stat.mindamage, weapon.mindam);
    int max = itemStatInt(claw, Stat.maxdamage, Math.max(min, weapon.maxdam));
    int percent = calculateDragonClawDamageBonus(skill, skillLevel)
        + weapon.StrBonus * statInt(attacker, Stat.strength) / 100
        + weapon.DexBonus * statInt(attacker, Stat.dexterity) / 100
        + statInt(attacker, Stat.damagepercent)
        + statInt(attacker, Stat.item_maxdamage_percent);
    percent = Math.max(-90, percent);
    return new int[] {scale(min, percent), scale(Math.max(min, max), percent)};
  }

  private static int itemStatInt(Item item, short stat, int fallback) {
    if (item == null || item.attrs == null) return fallback;
    StatRef ref = item.attrs.get(stat, StatRef.obtain());
    if (ref == null) ref = item.attrs.base().get(stat, StatRef.obtain());
    return ref == null ? fallback : ref.asInt();
  }

  /**
   * 龙尾 - 范围火焰踢击
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害百分比（物理伤害的）
   */
  public static int getDragonTailFirePercent(int skillLevel) {
    // Native calc1=ln12 with Param1=50 and Param2=10.
    return 50 + (Math.max(1, skillLevel) - 1) * 10;
  }

  public static int getDragonTailFirePercent(Skills.Entry skill, int skillLevel) {
    int value = SkillFormula.evaluate(skill != null ? skill.calc1 : null,
        skill, Math.max(1, skillLevel));
    return value != 0 ? value : getDragonTailFirePercent(skillLevel);
  }

  public static int getDragonTailRadius(Skills.Entry skill, int skillLevel) {
    return Math.max(0, SkillFormula.evaluate(
        skill != null ? skill.aurarangecalc : null, skill, Math.max(1, skillLevel)));
  }

  public static int dragonTailAttackRating(
      Skills.Entry skill, int skillLevel, Attributes attacker) {
    return dragonTalonAttackRating(skill, skillLevel, attacker);
  }

  /** SrvDo050: (resolved physical kick damage * (calc1 + Fire Mastery)) / 100. */
  public static int calculateDragonTailExplosionDamage(
      Skills.Entry skill, int skillLevel, Attributes attacker, int physicalDamage) {
    int percentage = getDragonTailFirePercent(skill, skillLevel)
        + statInt(attacker, Stat.passive_fire_mastery);
    long damage = (long) Math.max(0, physicalDamage) * Math.max(0, percentage) / 100L;
    return damage >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) damage;
  }

  /**
   * 龙飞 - 传送踢击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateDragonFlightDamageBonus(int skillLevel) {
    return 100 + (Math.max(1, skillLevel) - 1) * 25;
  }

  /** D2MOO SrvDo052: Param1 + (level - 1) * Param2 kick ED. */
  public static int calculateDragonFlightDamageBonus(Skills.Entry skill, int skillLevel) {
    return param(skill, 0, 100)
        + (Math.max(1, skillLevel) - 1) * param(skill, 1, 25);
  }

  /** Native Dragon Flight uses the shared Assassin KICK damage composition. */
  public static int[] calculateDragonFlightKickDamage(
      Skills.Entry skill, int skillLevel, Attributes attacker, Armor.Entry boots) {
    return calculateNativeKickDamage(attacker, boots,
        calculateDragonFlightDamageBonus(skill, skillLevel));
  }

  /** Native SrvDo052 includes progressive_tohit plus SKILLS_GetToHitFactor. */
  public static int dragonFlightAttackRating(
      Skills.Entry skill, int skillLevel, Attributes attacker) {
    return dragonTalonAttackRating(skill, skillLevel, attacker);
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

  /** D2MOO SrvSt28: replace the old Blade Shield stat list on cast start. */
  public static UnitState applyBladeShieldState(StateList states, Skills.Entry skill,
      int skillLevel, int sourceEntityId) {
    if (states == null || skill == null || skill.srvstfunc != 28
        || !hasText(progressiveMissile(skill, 1))) return null;
    int level = Math.max(1, skillLevel);
    int duration = SkillFormula.evaluate(skill.auralencalc, skill, level);
    if (duration <= 0) return null;
    states.removeState(StateId.BLADESHIELD);
    UnitState state = states.addState(
        StateId.BLADESHIELD, duration, level, sourceEntityId);
    if (state == null) return null;
    state.skillId = skill.Id;
    state.periodicDelayFrames = Math.max(5,
        SkillFormula.evaluate(skill.perdelay, skill, level));
    state.periodicCountdownFrames = -1;
    state.needsSync = true;
    return state;
  }

  public static int bladeShieldRange(Skills.Entry skill, int skillLevel) {
    return Math.max(0, SkillFormula.evaluate(
        skill != null ? skill.aurarangecalc : null, skill, Math.max(1, skillLevel)));
  }

  /** Returns the native flat physical damage before SrvDo142 applies SrcDam. */
  public static int[] bladeShieldDamageRange(Skills.Entry skill, int skillLevel) {
    if (skill == null) return new int[] {0, 0};
    int level = Math.max(1, skillLevel);
    int min = shiftedSkillDamage(skill.MinDam, skill.MinLevDam, level, skill.HitShift);
    int max = shiftedSkillDamage(skill.MaxDam, skill.MaxLevDam, level, skill.HitShift);
    return new int[] {Math.max(0, min), Math.max(Math.max(0, min), max)};
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

  /** D2MOO SrvDo018: build Venom's three AuraStat entries on the caster. */
  public static UnitState applyVenomState(StateList states, Skills.Entry skill,
      int skillLevel, int sourceEntityId) {
    if (states == null || skill == null || skill.srvdofunc != 18
        || !"venomclaws".equals(normalizeState(skill.aurastate))) return null;
    int level = Math.max(1, skillLevel);
    int duration = SkillFormula.evaluate(skill.auralencalc, skill, level);
    int[] damage = venomDamageRange(skill, level);
    int poisonLength = venomPoisonLength(skill, level);
    if (duration <= 0 || damage[1] <= 0 || poisonLength <= 0) return null;
    states.removeState(StateId.VENOMCLAWS);
    UnitState state = states.addState(
        StateId.VENOMCLAWS, duration, level, sourceEntityId);
    if (state == null) return null;
    state.skillId = skill.Id;
    state.poisonMinDamage = damage[0];
    state.poisonMaxDamage = damage[1];
    state.poisonLengthOverride = poisonLength;
    state.needsSync = true;
    return state;
  }

  /** Venom poison damage is stored as per-frame fixed damage in Skills.txt. */
  public static int[] venomDamageRange(Skills.Entry skill, int skillLevel) {
    if (skill == null) return new int[] {0, 0};
    int level = Math.max(1, skillLevel);
    int min = shiftedSkillDamage(skill.EMin, skill.EMinLev, level, skill.HitShift);
    int max = shiftedSkillDamage(skill.EMax, skill.EMaxLev, level, skill.HitShift);
    return new int[] {Math.max(0, min), Math.max(Math.max(0, min), max)};
  }

  public static int venomPoisonLength(Skills.Entry skill, int skillLevel) {
    if (skill == null) return 0;
    return Math.max(0, skill.ELen
        + damageBonusByLevel(Math.max(1, skillLevel), skill.ELevLen));
  }

  private static String normalizeState(String value) {
    return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
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
