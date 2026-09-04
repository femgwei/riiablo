package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToIntFunction;

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

  /** Result of native SrvDo116: an existing group state is toggled off first. */
  public static final class ShapeShiftResult {
    public final int removedStateId;
    public final UnitState appliedState;

    ShapeShiftResult(int removedStateId, UnitState appliedState) {
      this.removedStateId = removedStateId;
      this.appliedState = appliedState;
    }

    public boolean transformed() {
      return appliedState != null;
    }
  }

  public static int getShapeStateId(Skills.Entry skill) {
    if (skill == null || skill.aurastate == null) return StateId.NONE;
    switch (skill.aurastate.trim().toLowerCase(Locale.ROOT)) {
      case "wolf": return StateId.WOLF;
      case "bear": return StateId.BEAR;
      default: return StateId.NONE;
    }
  }

  /** D2Game SrvDo116 evaluates AuraLenCalc using hard-point synergies. */
  public static int getShapeDuration(
      Skills.Entry skill, int skillLevel,
      ToIntFunction<String> baseSkillLevel,
      Function<String, Skills.Entry> skillResolver) {
    if (skill == null) return 0;
    return Math.max(1, SkillFormula.evaluate(skill.auralencalc, skill,
        Math.max(1, skillLevel), baseSkillLevel, skillResolver));
  }

  /**
   * D2Game {@code SKILLS_SrvDo116_Wearwolf_Wearbear}. Wolf and bear belong
   * to one States.txt group: casting either while transformed removes the
   * current form; a subsequent cast establishes the requested form.
   */
  public static ShapeShiftResult applyShapeShiftState(
      StateList states, Skills.Entry skill, int skillLevel, int sourceEntityId,
      ToIntFunction<String> baseSkillLevel,
      Function<String, Skills.Entry> skillResolver) {
    if (states == null || skill == null || skill.srvdofunc != 116) {
      return new ShapeShiftResult(StateId.NONE, null);
    }

    int active = states.hasState(StateId.WOLF) ? StateId.WOLF
        : states.hasState(StateId.BEAR) ? StateId.BEAR : StateId.NONE;
    if (active != StateId.NONE) {
      states.removeState(active);
      removeInvalidFeralMaulStates(states);
      return new ShapeShiftResult(active, null);
    }

    int stateId = getShapeStateId(skill);
    if (stateId == StateId.NONE) return new ShapeShiftResult(StateId.NONE, null);
    int level = Math.max(1, skillLevel);
    int duration = getShapeDuration(skill, level, baseSkillLevel, skillResolver);
    UnitState state = states.addState(stateId, duration, level, sourceEntityId);
    if (state == null) return new ShapeShiftResult(StateId.NONE, null);

    // D2COMMON_10476 replaces the expire frame and sub_6FCFE0E0 rebuilds the
    // stat list. Never retain stronger values from an earlier cast.
    state.duration = duration;
    state.initialDuration = duration;
    state.level = level;
    state.sourceEntityId = sourceEntityId;
    state.skillId = skill.Id;
    state.clearModifiers();
    int count = Math.min(
        skill.aurastat != null ? skill.aurastat.length : 0,
        skill.aurastatcalc != null ? skill.aurastatcalc.length : 0);
    for (int i = 0; i < count; i++) {
      String stat = skill.aurastat[i];
      if (stat == null || stat.isEmpty()) continue;
      int value = SkillFormula.evaluate(skill.aurastatcalc[i], skill, level,
          baseSkillLevel, skillResolver);
      switch (stat.toLowerCase(Locale.ROOT)) {
        case "damagepercent": state.damageModifier += value; break;
        case "skill_armor_percent":
        case "item_armor_percent": state.defenseModifier += value; break;
        case "item_tohit_percent": state.attackModifier += value; break;
        case "attackrate": state.animationRateModifier += value; break;
        case "item_maxhp_percent": state.maxLifeModifier += value; break;
        case "skill_staminapercent": state.maxStaminaModifier += value; break;
        default:
          log.warn("[DRUID_SHAPE] ignored AuraStat skill={} stat={}", skill.skill, stat);
          break;
      }
    }
    state.needsSync = true;
    return new ShapeShiftResult(StateId.NONE, state);
  }

  /**
   * 狼人形态 - 变身为狼人
   * 
   * @param skillLevel 技能等级
   * @return 攻击等级加成百分比
   */
  public static int calculateWerewolfAttackRatingBonus(int skillLevel) {
    // Skills.txt ToHit=50, LevToHit=15 (the native "toht" operand).
    return 50 + (Math.max(1, skillLevel) - 1) * 15;
  }

  /**
   * 狼人形态攻击速度加成
   * 
   * @param skillLevel 技能等级
   * @return 攻击速度加成百分比
   */
  public static int getWerewolfIasBonus(int skillLevel) {
    // Skills.txt dm34 with Param3=10 and Param4=80.
    int level = Math.max(1, skillLevel);
    return 10 + (int) (110L * level * 70 / (100L * (level + 6)));
  }

  /**
   * 变形学 - 增强变形持续时间和生命
   * 
   * @param skillLevel 技能等级
   * @return 生命加成百分比
   */
  public static int calculateLycanthropyLifeBonus(int skillLevel) {
    // Shape Shifting (Lycanthropy) Skills.txt ln34: 20 + 5/level.
    return skillLevel <= 0 ? 0 : 20 + (skillLevel - 1) * 5;
  }

  /**
   * 熊人形态 - 变身为熊人
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateWerebearDamageBonus(int skillLevel) {
    // Skills.txt ln12: 55 + 8/level.
    return 55 + (Math.max(1, skillLevel) - 1) * 8;
  }

  /**
   * 熊人防御加成
   * 
   * @param skillLevel 技能等级
   * @return 防御加成百分比
   */
  public static int calculateWerebearDefenseBonus(int skillLevel) {
    // Skills.txt ln34: 25 + 6/level.
    return 25 + (Math.max(1, skillLevel) - 1) * 6;
  }

  public static boolean isFeralRageOrMaul(Skills.Entry skill) {
    return skill != null && skill.srvstfunc == 56 && skill.srvdofunc == 120;
  }

  /** Removes charge states whose required transform is no longer active. */
  public static int removeInvalidFeralMaulStates(StateList states) {
    if (states == null) return 0;
    int removed = 0;
    if (!states.hasState(StateId.WOLF) && states.removeState(StateId.FERALRAGE)) removed++;
    if (!states.hasState(StateId.BEAR) && states.removeState(StateId.MAUL)) removed++;
    return removed;
  }

  public static int getFeralMaulStateId(Skills.Entry skill) {
    if (skill == null || skill.aurastate == null) return StateId.NONE;
    switch (skill.aurastate.trim().toLowerCase(Locale.ROOT)) {
      case "feralrage": return StateId.FERALRAGE;
      case "maul": return StateId.MAUL;
      default: return StateId.NONE;
    }
  }

  /**
   * D2Common_SKILLS_CheckShapeRestriction. This project currently models the
   * native restrict state mask through the two player shape states.
   */
  public static boolean isSkillAllowedInCurrentShape(Skills.Entry skill, StateList states) {
    if (skill == null) return false;
    boolean wolf = states != null && states.hasState(StateId.WOLF);
    boolean bear = states != null && states.hasState(StateId.BEAR);
    boolean restrictedState = wolf || bear;
    if (skill.restrict == 0) return !restrictedState;
    if (skill.restrict != 2) return true;
    if (!restrictedState) return false;
    return matchesShape(skill.state1, wolf, bear)
        || matchesShape(skill.state2, wolf, bear)
        || matchesShape(skill.state3, wolf, bear);
  }

  private static boolean matchesShape(String state, boolean wolf, boolean bear) {
    if (state == null || state.isEmpty()) return false;
    return wolf && "wolf".equalsIgnoreCase(state)
        || bear && "bear".equalsIgnoreCase(state);
  }

  /** Native SrvDo120 calc2: the maximum STAT_SKILL_FRENZY value. */
  public static int getFeralMaulMaxStacks(Skills.Entry skill, int skillLevel) {
    if (!isFeralRageOrMaul(skill)) return 0;
    return Math.max(0, SkillFormula.evaluate(skill.calc2, skill, Math.max(1, skillLevel)));
  }

  public static int getFeralMaulDuration(Skills.Entry skill, int skillLevel) {
    if (!isFeralRageOrMaul(skill)) return 0;
    return Math.max(1,
        SkillFormula.evaluate(skill.auralencalc, skill, Math.max(1, skillLevel)));
  }

  public static int getFeralMaulAuraStat(
      Skills.Entry skill, int stacks, String statName) {
    if (skill == null || skill.aurastat == null || skill.aurastatcalc == null
        || statName == null || stacks <= 0) return 0;
    int count = Math.min(skill.aurastat.length, skill.aurastatcalc.length);
    for (int i = 0; i < count; i++) {
      if (statName.equalsIgnoreCase(skill.aurastat[i])) {
        return SkillFormula.evaluate(skill.aurastatcalc[i], skill, stacks);
      }
    }
    return 0;
  }

  /**
   * D2Game SKILLS_SrvDo120_FeralRage_Maul. The caller invokes this only for
   * an unblocked successful SrvSt56 combat record. AuraStatCalc intentionally
   * receives the stack count rather than the learned skill level.
   */
  public static UnitState applyFeralMaulState(
      StateList states, Skills.Entry skill, int skillLevel, int sourceEntityId) {
    int stateId = getFeralMaulStateId(skill);
    if (states == null || stateId == StateId.NONE
        || !isSkillAllowedInCurrentShape(skill, states)) return null;
    UnitState existing = states.getState(stateId);
    int previous = existing != null ? Math.max(0, existing.runtimeValue) : 0;
    int stacks = Math.min(getFeralMaulMaxStacks(skill, skillLevel), previous + 1);
    if (stacks <= 0) return null;
    int duration = getFeralMaulDuration(skill, skillLevel);
    UnitState state = states.addState(
        stateId, duration, Math.max(1, skillLevel), sourceEntityId);
    if (state == null) return null;
    state.duration = duration;
    state.initialDuration = duration;
    state.level = Math.max(1, skillLevel);
    state.sourceEntityId = sourceEntityId;
    state.skillId = skill.Id;
    state.clearModifiers();
    state.runtimeValue = stacks;

    int count = Math.min(
        skill.aurastat != null ? skill.aurastat.length : 0,
        skill.aurastatcalc != null ? skill.aurastatcalc.length : 0);
    for (int i = 0; i < count; i++) {
      String stat = skill.aurastat[i];
      if (stat == null || stat.isEmpty()) continue;
      int value = SkillFormula.evaluate(skill.aurastatcalc[i], skill, stacks);
      switch (stat.toLowerCase(Locale.ROOT)) {
        case "velocitypercent": state.velocityModifier += value; break;
        case "lifedrainmindam":
        case "lifedrainmaxdam":
          state.lifeLeechModifier = Math.max(state.lifeLeechModifier, value);
          break;
        case "damagepercent": state.damageModifier += value; break;
        case "stunlength": state.stunLength = Math.max(state.stunLength, value); break;
        default:
          log.warn("[DRUID_FERAL_MAUL] ignored AuraStat skill={} stat={}", skill.skill, stat);
          break;
      }
    }
    state.needsSync = true;
    return state;
  }

  /** SrvSt56 SKILLS_GetToHitFactor applied to the player's base AR. */
  public static int getFeralMaulAttackRating(
      Skills.Entry skill, int skillLevel, Attributes attacker, boolean player) {
    int base = statInt(attacker, Stat.tohit);
    int level = Math.max(1, skillLevel);
    int factor = skill == null ? 0 : skill.ToHit + (level - 1) * skill.LevToHit;
    if (player) return Math.max(1, base * Math.max(0, 100 + factor) / 100);
    return Math.max(1, base + factor);
  }

  /** Complete physical packet stored by SrvSt56 before SrvDo120 executes. */
  public static int[] calculateFeralMaulWeaponDamage(
      Skills.Entry skill, int skillLevel, Attributes attacker, Item weapon,
      StateList states) {
    return calculateShapeWeaponDamageWithCalc(
        skill, skillLevel, attacker, weapon, states, skill != null ? skill.calc1 : null);
  }

  /** SrvDo013 uses calc2 (ln34), while the other shape attacks use calc1. */
  public static int[] calculateFuryWeaponDamage(
      Skills.Entry skill, int skillLevel, Attributes attacker, Item weapon,
      StateList states) {
    if (!isFury(skill)) return new int[] {0, 0};
    return calculateShapeWeaponDamageWithCalc(
        skill, skillLevel, attacker, weapon, states, skill.calc2);
  }

  private static int[] calculateShapeWeaponDamageWithCalc(
      Skills.Entry skill, int skillLevel, Attributes attacker, Item weapon,
      StateList states, String damageCalc) {
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
    int percent = SkillFormula.evaluate(damageCalc, skill, Math.max(1, skillLevel))
        + attributePercent
        + statInt(attacker, Stat.damagepercent)
        + statInt(attacker, Stat.item_maxdamage_percent);
    if (states != null) {
      percent += states.getTotalDamageModifier();
      StateList.WeaponMasteryBonus mastery = states.getWeaponMastery(
          weapon, false, new StateList.WeaponMasteryBonus());
      percent += mastery.damagePercent;
    }
    int sourceDamage = skill == null || skill.SrcDam == 0 ? 128 : skill.SrcDam;
    return new int[] {
        scale(scale(min, percent), sourceDamage, 128),
        scale(scale(max, percent), sourceDamage, 128)
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

  private static int scale(int value, int enhancedPercent) {
    long result = (long) Math.max(0, value) * Math.max(0, 100 + enhancedPercent) / 100L;
    return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
  }

  private static int scale(int value, int numerator, int denominator) {
    if (value <= 0 || numerator <= 0 || denominator <= 0) return 0;
    long result = (long) value * numerator / denominator;
    return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
  }

  /**
   * 狂犬病 - 毒素攻击
   * 
   * @param skillLevel 技能等级
   * @return 总毒素伤害
   */
  public static int calculateRabiesDamage(int skillLevel) {
    Skills.Entry skill = com.riiablo.Riiablo.files != null
        ? com.riiablo.Riiablo.files.skills.get(SkillId.RABIES) : null;
    int[] damage = getRabiesPoisonDamage(skill, skillLevel, name -> 0);
    int duration = getRabiesPoisonDuration(skill, skillLevel, name -> 0);
    return Math.round(MathUtils.random(damage[0], damage[1]) / 256f * duration);
  }

  public static boolean isRabies(Skills.Entry skill) {
    return skill != null && skill.srvstfunc == 57 && skill.srvdofunc == 121;
  }

  public static boolean isFireClaws(Skills.Entry skill) {
    return skill != null && skill.srvstfunc == 58 && skill.srvdofunc == 2;
  }

  /** Native Skills.txt EMin/EMax + level increments + EDmgSymPerCalc. */
  public static int[] getRabiesPoisonDamage(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    if (!isRabies(skill)) return new int[] {0, 0};
    return elementalFixedDamageRange(skill, skillLevel, baseSkillLevel);
  }

  /** SKILLS_GetElementalLength, including ELenSymPerCalc when present. */
  public static int getRabiesPoisonDuration(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    if (!isRabies(skill)) return 0;
    int level = Math.max(1, skillLevel);
    return Math.max(10, skill.ELen + damageBonusByLevel(level, skill.ELevLen)
        + SkillFormula.evaluate(skill.ELenSymPerCalc, skill, level, baseSkillLevel));
  }

  /**
   * 火焰之爪 - 火焰攻击
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateFireClawsDamage(int skillLevel) {
    Skills.Entry skill = com.riiablo.Riiablo.files != null
        ? com.riiablo.Riiablo.files.skills.get(SkillId.FIRE_CLAWS) : null;
    int[] damage = getFireClawsFireDamage(skill, skillLevel, name -> 0);
    return MathUtils.random(damage[0], damage[1]);
  }

  public static int[] getFireClawsFireDamage(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    if (!isFireClaws(skill)) return new int[] {0, 0};
    return elementalDamageRange(skill, skillLevel, baseSkillLevel);
  }

  public static int getDruidElementalSkillLevel(
      ToIntFunction<String> baseSkillLevel, String skillName) {
    return baseSkillLevel == null || skillName == null ? 0
        : Math.max(0, baseSkillLevel.applyAsInt(skillName));
  }

  public static int getFireClawsSynergyLevel(ToIntFunction<String> baseSkillLevel) {
    return getDruidElementalSkillLevel(baseSkillLevel, "Firestorm")
        + getDruidElementalSkillLevel(baseSkillLevel, "Molten Boulder")
        + getDruidElementalSkillLevel(baseSkillLevel, "Volcano")
        + getDruidElementalSkillLevel(baseSkillLevel, "Eruption");
  }

  /** Native player AR applies ToHit/LevToHit as item_tohit_percent. */
  public static int getShapeAttackRating(
      Skills.Entry skill, int skillLevel, Attributes attacker, boolean player) {
    int base = statInt(attacker, Stat.tohit);
    int factor = skill == null ? 0
        : skill.ToHit + (Math.max(1, skillLevel) - 1) * skill.LevToHit;
    return player ? Math.max(1, base * Math.max(0, 100 + factor) / 100)
        : Math.max(1, base + factor);
  }

  public static int[] calculateShapeWeaponDamage(
      Skills.Entry skill, int skillLevel, Attributes attacker, Item weapon, StateList states) {
    return calculateFeralMaulWeaponDamage(skill, skillLevel, attacker, weapon, states);
  }

  private static int[] elementalDamageRange(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    int level = Math.max(1, skillLevel);
    int min = shiftedDamage(skill.EMin, skill.EMinLev, level, skill.HitShift);
    int max = shiftedDamage(skill.EMax, skill.EMaxLev, level, skill.HitShift);
    int synergy = SkillFormula.evaluate(
        skill.EDmgSymPerCalc, skill, level, baseSkillLevel);
    min += min * Math.max(0, synergy) / 100;
    max += max * Math.max(0, synergy) / 100;
    return new int[] {min, Math.max(min, max)};
  }

  /** D2Common SKILLS_GetMin/MaxElemDamage result in native 8.8 fixed units. */
  private static int[] elementalFixedDamageRange(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    int level = Math.max(1, skillLevel);
    long min = Math.max(0L,
        (long) skill.EMin + damageBonusByLevel(level, skill.EMinLev));
    long max = Math.max(min,
        (long) skill.EMax + damageBonusByLevel(level, skill.EMaxLev));
    min <<= Math.min(Math.max(0, skill.HitShift), 30);
    max <<= Math.min(Math.max(0, skill.HitShift), 30);
    int synergy = Math.max(0, SkillFormula.evaluate(
        skill.EDmgSymPerCalc, skill, level, baseSkillLevel));
    min += min * synergy / 100;
    max += max * synergy / 100;
    return new int[] {
        min > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) min,
        max > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) max};
  }

  private static int damageBonusByLevel(int level, int[] values) {
    if (level <= 1 || values == null || values.length == 0) return 0;
    int l1 = values.length > 0 ? values[0] : 0;
    int l2 = values.length > 1 ? values[1] : 0;
    int l3 = values.length > 2 ? values[2] : 0;
    int l4 = values.length > 3 ? values[3] : 0;
    int l5 = values.length > 4 ? values[4] : 0;
    if (level > 28) return 7 * l1 + 8 * l2 + 6 * (l3 + l4) + (level - 28) * l5;
    if (level > 22) return 7 * l1 + 8 * l2 + 6 * l3 + (level - 22) * l4;
    if (level > 16) return 7 * l1 + 8 * l2 + (level - 16) * l3;
    if (level > 8) return 7 * l1 + (level - 8) * l2;
    return (level - 1) * l1;
  }

  private static int shiftedDamage(int base, int[] perLevel, int level, int hitShift) {
    long value = Math.max(0L, (long) base + damageBonusByLevel(level, perLevel));
    int shift = hitShift - 8;
    if (shift > 0) value <<= Math.min(shift, 30);
    else if (shift < 0) value >>= Math.min(-shift, 30);
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
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

  public static boolean isHunger(Skills.Entry skill) {
    return skill != null && skill.srvdofunc == 122;
  }

  /** Native D2Game SrvDo008 Shock Wave discriminator. */
  public static boolean isShockWave(Skills.Entry skill) {
    return skill != null && skill.srvdofunc == 8
        && ("shockwave".equalsIgnoreCase(skill.srvmissilea)
            || "shockwave".equalsIgnoreCase(skill.srvmissileb));
  }

  /** SrvDo008 evaluates Shock Wave's calc1 as a fixed five-projectile count. */
  public static int getShockWaveMissileCount(Skills.Entry skill, int skillLevel) {
    if (!isShockWave(skill)) return 0;
    int count = SkillFormula.evaluate(skill.calc1, skill, Math.max(1, skillLevel));
    return Math.max(0, Math.min(64, count));
  }

  /**
   * Native SrvDmg07 reads Shock Wave's Param1/Param2 in animation frames.
   * Values are deliberately kept in frames (25 Hz), not converted to seconds.
   */
  public static int getShockWaveStunDuration(Skills.Entry skill, int skillLevel) {
    return getShockWaveStunDuration(null, skill, skillLevel);
  }

  /** SrvDmg07 gives a positive Missiles.txt dParam1 precedence over Skills.txt. */
  public static int getShockWaveStunDuration(
      Missiles.Entry missile, Skills.Entry skill, int skillLevel) {
    if (!isShockWave(skill)) return 0;
    if (missile != null && missile.dParam != null && missile.dParam.length > 0
        && missile.dParam[0] > 0) return missile.dParam[0];
    int level = Math.max(1, skillLevel);
    if (skill.Param != null && skill.Param.length >= 2) {
      return Math.max(0, skill.Param[0] + (level - 1) * skill.Param[1]);
    }
    return 0;
  }

  /** Native physical damage packet carried by each Shock Wave missile. */
  public static int[] getShockWaveDamageRange(Skills.Entry skill, int skillLevel) {
    if (!isShockWave(skill)) return new int[] {0, 0};
    int level = Math.max(1, skillLevel);
    int min = shiftedDamage(skill.MinDam, skill.MinLevDam, level, skill.HitShift);
    int max = shiftedDamage(skill.MaxDam, skill.MaxLevDam, level, skill.HitShift);
    return new int[] {Math.max(0, min), Math.max(Math.max(0, min), max)};
  }

  public static int getHungerLifeLeech(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    return isHunger(skill) ? Math.max(0, SkillFormula.evaluate(
        skill.calc2, skill, Math.max(1, skillLevel), baseSkillLevel)) : 0;
  }

  public static int getHungerManaLeech(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    return isHunger(skill) ? Math.max(0, SkillFormula.evaluate(
        skill.calc3, skill, Math.max(1, skillLevel), baseSkillLevel)) : 0;
  }

  /**
   * Compatibility API returning the native data-driven duration in seconds.
   * @deprecated use {@link #getShockWaveStunDuration(Skills.Entry, int)} for server frames.
   */
  @Deprecated
  public static float getShockWaveStunDuration(int skillLevel) {
    Skills.Entry skill = com.riiablo.Riiablo.files != null
        ? com.riiablo.Riiablo.files.skills.get(SkillId.SHOCK_WAVE) : null;
    int frames = getShockWaveStunDuration(skill, skillLevel);
    return frames > 0 ? frames / 25f : 0f;
  }

  /** Native SrvSt37/SrvDo013 Fury discriminator. */
  public static boolean isFury(Skills.Entry skill) {
    return skill != null && skill.srvstfunc == 37 && skill.srvdofunc == 13
        && "wolf".equalsIgnoreCase(skill.state1);
  }

  /** Native calc1: min(Param5 + level - 1, Param6), yielding 2..5 strikes. */
  public static int getFuryHitCount(Skills.Entry skill, int skillLevel) {
    if (!isFury(skill)) return 0;
    return Math.max(0, Math.min(64,
        SkillFormula.evaluate(skill.calc1, skill, Math.max(1, skillLevel))));
  }

  /** Native calc2 (ln34) enhanced physical damage percentage. */
  public static int getFuryDamagePercent(Skills.Entry skill, int skillLevel) {
    return isFury(skill) ? SkillFormula.evaluate(
        skill.calc2, skill, Math.max(1, skillLevel)) : 0;
  }

  /** Temporary attack-rate stat installed between native Fury strikes. */
  public static int getFuryRepeatAttackRate(Skills.Entry skill) {
    return isFury(skill) && skill.Param != null && skill.Param.length > 1
        ? skill.Param[1] : 0;
  }

  /**
   * 狂怒 - 狼人多目标攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateFuryDamageBonus(int skillLevel) {
    Skills.Entry skill = com.riiablo.Riiablo.files != null
        ? com.riiablo.Riiablo.files.skills.get(SkillId.FURY) : null;
    return getFuryDamagePercent(skill, skillLevel);
  }

  /**
   * 获取狂怒攻击次数
   * 
   * @return 攻击次数
   */
  public static int getFuryHitCount() {
    Skills.Entry skill = com.riiablo.Riiablo.files != null
        ? com.riiablo.Riiablo.files.skills.get(SkillId.FURY) : null;
    return getFuryHitCount(skill, 20);
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
