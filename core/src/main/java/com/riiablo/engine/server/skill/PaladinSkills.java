package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * 圣骑士技能实现 - 基于 D2MOD SkillPal.cpp 移植
 * 
 * <p>包含战斗技能、攻击光环、防御光环三系技能的实现。
 * 
 * <p>参考：D2MOD/source/D2Game/src/SKILLS/SkillPal.cpp
 * 
 * @author riiablo team
 */
public final class PaladinSkills {
  private static final Logger log = LogManager.getLogger(PaladinSkills.class);

  private PaladinSkills() {} // 不可实例化

  //==========================================================================
  // 战斗技能
  //==========================================================================

  /**
   * 牺牲 - 消耗生命造成额外伤害
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateSacrificeDamageBonus(int skillLevel) {
    // 基础 180%，每级 +15%
    return 180 + (skillLevel - 1) * 15;
  }

  /**
   * 牺牲自伤
   * 
   * @param damageDealt 造成的伤害
   * @return 自伤值
   */
  public static int calculateSacrificeSelfDamage(int damageDealt) {
    // 8% 自伤
    return damageDealt * 8 / 100;
  }

  /**
   * 重击 - 盾牌攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateSmiteDamageBonus(int skillLevel) {
    // 基础 15-25，每级 +5
    return 15 + (skillLevel - 1) * 5;
  }

  /**
   * 重击击退概率
   * 
   * @param skillLevel 技能等级
   * @return 击退概率百分比
   */
  public static int getSmiteKnockbackChance(int skillLevel) {
    // 固定 100% 击退
    return 100;
  }

  /**
   * 圣光弹 - 对不死和恶魔造成额外伤害
   * 
   * @param skillLevel 技能等级
   * @return 基础伤害
   */
  public static int calculateHolyBoltDamage(int skillLevel) {
    // 基础 8-16，每级 +4
    int minDamage = 8 + (skillLevel - 1) * 4;
    int maxDamage = 16 + (skillLevel - 1) * 4;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 圣光弹治疗量
   * 
   * @param skillLevel 技能等级
   * @return 治疗量
   */
  public static int calculateHolyBoltHeal(int skillLevel) {
    // 基础 12，每级 +4
    return 12 + (skillLevel - 1) * 4;
  }

  /** True for the native 1.10f Holy Bolt row handled by SrvHit07. */
  public static boolean isHolyBolt(Skills.Entry skill) {
    return skill != null && skill.skill != null
        && "Holy Bolt".equalsIgnoreCase(skill.skill);
  }

  /** True only for the native 1.10f SrvDo080 Fist of the Heavens row. */
  public static boolean isFistOfTheHeavens(Skills.Entry skill) {
    return skill != null && skill.srvdofunc == 80 && skill.skill != null
        && "Fist of the Heavens".equalsIgnoreCase(skill.skill);
  }

  /** Native Holy Bolt magic packet, including hard-point skill synergies. */
  public static int[] getHolyBoltMagicDamage(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    if (!isHolyBolt(skill)) return new int[] {0, 0};
    return skillElementalDamage(skill, skillLevel, baseSkillLevel);
  }

  /** Native FoH center lightning packet, including the Holy Shock synergy. */
  public static int[] getFistOfHeavensLightningDamage(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    if (!isFistOfTheHeavens(skill)) return new int[] {0, 0};
    return skillElementalDamage(skill, skillLevel, baseSkillLevel);
  }

  /**
   * Native FoH Holy Bolt packet comes from Missiles.txt rather than the FoH
   * Skills.txt elemental columns. Its EDmgSymPerCalc still reads the caster's
   * hard-point Holy Bolt level.
   */
  public static int[] getFistOfHeavensBoltMagicDamage(
      Missiles.Entry missile, Skills.Entry fist, int skillLevel,
      ToIntFunction<String> baseSkillLevel) {
    if (missile == null || !isFistOfTheHeavens(fist)) return new int[] {0, 0};
    int level = Math.max(1, skillLevel);
    long min = shiftedDamage(missile.EMin, missile.MinELev, level, missile.HitShift);
    long max = Math.max(min,
        shiftedDamage(missile.Emax, missile.MaxELev, level, missile.HitShift));
    int synergy = Math.max(0, SkillFormula.evaluate(
        missile.EDmgSymPerCalc, fist, level,
        baseSkillLevel == null ? name -> 0 : baseSkillLevel));
    min += min * synergy / 100L;
    max += max * synergy / 100L;
    return new int[] {saturated(min), saturated(max)};
  }

  /** SrvHit07 healing range from the missile's stored casting skill. */
  public static int[] getHolyBoltHealing(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    if (skill == null) return new int[] {0, 0};
    int level = Math.max(1, skillLevel);
    ToIntFunction<String> levels = baseSkillLevel == null ? name -> 0 : baseSkillLevel;
    int min = Math.max(0, SkillFormula.evaluate(skill.calc1, skill, level, levels));
    int max = Math.max(min, SkillFormula.evaluate(skill.calc2, skill, level, levels));
    return new int[] {min, max};
  }

  /** SrvHit22 uses HitPar1 first, then Skills.txt AuraRangeCalc. */
  public static int getFistOfHeavensRange(
      Missiles.Entry delay, Skills.Entry skill, int skillLevel) {
    int configured = arrayValue(delay != null ? delay.sHitPar : null, 0);
    int calculated = configured > 0 ? configured
        : SkillFormula.evaluate(skill != null ? skill.aurarangecalc : null,
            skill, Math.max(1, skillLevel));
    return Math.max(1, calculated);
  }

  /** SrvHit22 uses HitPar2 first, then FoH Calc4, with a native minimum of one. */
  public static int getFistOfHeavensBoltCount(
      Missiles.Entry delay, Skills.Entry skill, int skillLevel) {
    int configured = arrayValue(delay != null ? delay.sHitPar : null, 1);
    int calculated = configured > 0 ? configured
        : SkillFormula.evaluate(skill != null ? skill.calc4 : null,
            skill, Math.max(1, skillLevel));
    return Math.max(1, calculated);
  }

  private static int[] skillElementalDamage(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    int level = Math.max(1, skillLevel);
    long min = shiftedDamage(skill.EMin, skill.EMinLev, level, skill.HitShift);
    long max = Math.max(min, shiftedDamage(skill.EMax, skill.EMaxLev, level, skill.HitShift));
    int synergy = Math.max(0, SkillFormula.evaluate(
        skill.EDmgSymPerCalc, skill, level,
        baseSkillLevel == null ? name -> 0 : baseSkillLevel));
    min += min * synergy / 100L;
    max += max * synergy / 100L;
    return new int[] {saturated(min), saturated(max)};
  }

  /** Native elemental aura pulse/melee packet including hard-point synergies. */
  public static int[] getAuraElementalDamage(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    if (skill == null || skill.EType == null || skill.EType.trim().isEmpty()) {
      return new int[] {0, 0};
    }
    return skillElementalDamage(skill, skillLevel, baseSkillLevel);
  }

  /** Maps Paladin aura hard-point passive lists from the native States table. */
  public static int getHardPointPassiveStateId(Skills.Entry skill) {
    if (skill == null || skill.passivestate == null) return StateId.NONE;
    switch (skill.passivestate.trim().toLowerCase(Locale.ROOT)) {
      case "passive_resistfire": return StateId.PASSIVE_RESISTFIRE;
      case "passive_resistcold": return StateId.PASSIVE_RESISTCOLD;
      case "passive_resistltng": return StateId.PASSIVE_RESISTLTNG;
      case "penetrate": return StateId.PENETRATE;
      default: return StateId.NONE;
    }
  }

  /** Builds a permanent passive list strictly from owned hard skill points. */
  public static UnitState applyHardPointPassiveState(
      StateList states, Skills.Entry skill, int hardLevel, int ownerId) {
    int stateId = getHardPointPassiveStateId(skill);
    if (states == null || hardLevel <= 0 || stateId == StateId.NONE) return null;
    UnitState state = states.addState(stateId, 0, hardLevel, ownerId);
    if (state == null) return null;
    state.duration = 0;
    state.initialDuration = 0;
    state.level = hardLevel;
    state.sourceEntityId = ownerId;
    state.skillId = skill.Id;
    state.basicStatList = true;
    state.clearModifiers();
    int count = Math.min(skill.passivestat != null ? skill.passivestat.length : 0,
        skill.passivecalc != null ? skill.passivecalc.length : 0);
    ToIntFunction<String> hardPoints = name -> skill.skill != null
        && skill.skill.equalsIgnoreCase(name) ? hardLevel : 0;
    for (int i = 0; i < count; i++) {
      int statId = skill.passivestat[i] == null ? -1 : Stat.index(skill.passivestat[i].trim());
      if (statId < 0) continue;
      int value = SkillFormula.evaluate(
          skill.passivecalc[i], skill, hardLevel, hardPoints);
      state.setStatContribution(statId, 0, NativeStatResolver.Operation.ADD, value);
    }
    state.needsSync = true;
    return state;
  }

  /** Compatibility bridge for the three resistance aura passive callers. */
  public static int getResistancePassiveStateId(Skills.Entry skill) {
    int stateId = getHardPointPassiveStateId(skill);
    return stateId == StateId.PENETRATE ? StateId.NONE : stateId;
  }

  /** Compatibility bridge for existing resistance aura tests and callers. */
  public static UnitState applyResistancePassiveState(
      StateList states, Skills.Entry skill, int hardLevel, int ownerId) {
    if (getResistancePassiveStateId(skill) == StateId.NONE) return null;
    return applyHardPointPassiveState(states, skill, hardLevel, ownerId);
  }

  /**
   * 热忱 - 快速连续攻击
   * 
   * @param skillLevel 技能等级
   * @return 攻击次数
   */
  public static int getZealAttackCount(int skillLevel) {
    // 固定 5 次攻击
    return 5;
  }

  /** Native Zeal strike count from calc1, clamped to the 1.10f five-hit cap. */
  public static int getZealAttackCount(Skills.Entry skill, int skillLevel) {
    if (skill == null || skill.Id != SkillId.ZEAL) return 0;
    int value = SkillFormula.evaluate(skill.calc1, skill, Math.max(1, skillLevel));
    return Math.max(1, Math.min(5, value > 0 ? value : getZealAttackCount(skillLevel)));
  }

  /** Native Zeal physical damage bonus (calc2). */
  public static int getZealDamagePercent(Skills.Entry skill, int skillLevel) {
    if (skill == null || skill.Id != SkillId.ZEAL) return 0;
    int value = SkillFormula.evaluate(skill.calc2, skill, Math.max(1, skillLevel));
    return value != 0 ? value : calculateZealDamageBonus(skillLevel);
  }

  /** Native Zeal attack-rating bonus (calc3). */
  public static int getZealAttackRatingPercent(Skills.Entry skill, int skillLevel) {
    if (skill == null || skill.Id != SkillId.ZEAL) return 0;
    int value = SkillFormula.evaluate(skill.calc3, skill, Math.max(1, skillLevel));
    return value != 0 ? value : calculateZealAttackRatingBonus(skillLevel);
  }

  /**
   * 热忱伤害加成
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateZealDamageBonus(int skillLevel) {
    // 每级 +10%
    return 10 * skillLevel;
  }

  /**
   * 热忱攻击等级加成
   * 
   * @param skillLevel 技能等级
   * @return 攻击等级加成
   */
  public static int calculateZealAttackRatingBonus(int skillLevel) {
    // 每级 +10%
    return 10 * skillLevel;
  }

  /**
   * 冲锋 - 冲向敌人
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateChargeDamageBonus(int skillLevel) {
    // 基础 100%，每级 +25%
    return 100 + (skillLevel - 1) * 25;
  }

  /** Native Charge SrvDo067 damage percentage (Skills.txt calc1). */
  public static int getChargeDamagePercent(Skills.Entry skill, int skillLevel) {
    if (skill == null || skill.Id != SkillId.CHARGE) return 0;
    int value = SkillFormula.evaluate(skill.calc1, skill, Math.max(1, skillLevel));
    return value != 0 ? value : calculateChargeDamageBonus(skillLevel);
  }

  /** Native Charge attack-rating contribution from ToHit/LevToHit. */
  public static int getChargeAttackRating(Skills.Entry skill, int skillLevel, int baseAttackRating) {
    if (skill == null || (skill.Id != SkillId.CHARGE && skill.Id != SkillId.VENGEANCE)) {
      return baseAttackRating;
    }
    int level = Math.max(1, skillLevel);
    return Math.max(0, baseAttackRating + skill.ToHit + (level - 1) * skill.LevToHit);
  }

  /** Native SrvSt31 movement bonus (Param1, percentage points). */
  public static int getChargeVelocityBonus(Skills.Entry skill) {
    if (skill == null || skill.Id != SkillId.CHARGE || skill.Param == null
        || skill.Param.length == 0) return 0;
    return Math.max(0, skill.Param[0]);
  }

  /**
   * 复仇 - 元素伤害攻击
   * 
   * @param skillLevel 技能等级
   * @param elementType 元素类型（0=火，1=冰，2=电）
   * @return 元素伤害
   */
  public static int calculateVengeanceDamage(int skillLevel, int elementType) {
    // 每种元素各加 40-60 基础，每级 +20
    int minDamage = 40 + (skillLevel - 1) * 20;
    int maxDamage = 60 + (skillLevel - 1) * 20;
    return MathUtils.random(minDamage, maxDamage);
  }

  /** Native Vengeance SrvSt35 elemental percentage (calc1/calc2/calc3). */
  public static int getVengeanceElementPercent(Skills.Entry skill, int skillLevel,
      int elementType) {
    if (skill == null || skill.Id != SkillId.VENGEANCE) return 0;
    String formula;
    switch (Math.max(0, Math.min(2, elementType))) {
      case 1: formula = skill.calc2; break; // cold
      case 2: formula = skill.calc3; break; // lightning
      default: formula = skill.calc1; break; // fire
    }
    int value = SkillFormula.evaluate(formula, skill, Math.max(1, skillLevel));
    // 1.10f's Vengeance row is percentage based; retain a conservative
    // compatibility fallback for reduced/custom tables.
    return value > 0 ? value : 70 + 6 * (Math.max(1, skillLevel) - 1);
  }

  /** Converts the physical weapon range into one elemental Vengeance packet. */
  public static int[] getVengeanceElementalDamage(Skills.Entry skill, int skillLevel,
      int elementType, int physicalMin, int physicalMax) {
    int percent = getVengeanceElementPercent(skill, skillLevel, elementType);
    long min = Math.max(0L, (long) Math.max(0, physicalMin) * percent / 100L);
    long max = Math.max(min, (long) Math.max(0, physicalMax) * percent / 100L);
    return new int[] {saturated(min), saturated(max)};
  }

  /** Native SKILLS_GetElementalLength used by Vengeance's cold packet. */
  public static int getVengeanceColdLength(Skills.Entry skill, int skillLevel,
      ToIntFunction<String> baseSkillLevel) {
    if (skill == null || skill.Id != SkillId.VENGEANCE) return 0;
    int level = Math.max(1, skillLevel);
    return Math.max(0, skill.ELen + damageBonusByLevel(level, skill.ELevLen)
        + SkillFormula.evaluate(skill.ELenSymPerCalc, skill, level,
            baseSkillLevel == null ? name -> 0 : baseSkillLevel));
  }

  /**
   * 祝福之锤 - 旋转的魔法锤
   * 
   * @param skillLevel 技能等级
   * @return 伤害
   */
  public static int calculateBlessedHammerDamage(int skillLevel) {
    // 基础 12-16，每级 +6-8
    int minDamage = 12 + (skillLevel - 1) * 6;
    int maxDamage = 16 + (skillLevel - 1) * 8;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 祝福之锤对不死/恶魔额外伤害
   * 
   * @param skillLevel 技能等级
   * @return 额外伤害百分比
   */
  public static int getBlessedHammerBonusPercent(int skillLevel) {
    // 对不死 +50%
    return 50;
  }

  /** True only for the native 1.10f SrvDo073 Blessed Hammer row. */
  public static boolean isBlessedHammer(Skills.Entry skill) {
    return skill != null && skill.srvdofunc == 73
        && skill.skill != null && "Blessed Hammer".equalsIgnoreCase(skill.skill);
  }

  /**
   * Native Skills.txt magic packet, including hard-point Vigor/Blessed Aim
   * synergy. Concentration is deliberately applied afterwards because D2Game
   * snapshots it onto the created missile rather than evaluating it here.
   */
  public static int[] getBlessedHammerMagicDamage(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    if (!isBlessedHammer(skill)) return new int[] {0, 0};
    int level = Math.max(1, skillLevel);
    long min = Math.max(0L, (long) skill.EMin + damageBonusByLevel(level, skill.EMinLev));
    long max = Math.max(min, (long) skill.EMax + damageBonusByLevel(level, skill.EMaxLev));
    int shift = skill.HitShift - 8;
    if (shift > 0) {
      min <<= Math.min(30, shift);
      max <<= Math.min(30, shift);
    } else if (shift < 0) {
      min >>= Math.min(30, -shift);
      max >>= Math.min(30, -shift);
    }
    int synergy = Math.max(0, SkillFormula.evaluate(
        skill.EDmgSymPerCalc, skill, level,
        baseSkillLevel == null ? name -> 0 : baseSkillLevel));
    min += min * synergy / 100L;
    max += max * synergy / 100L;
    return new int[] {saturated(min), saturated(max)};
  }

  /** D2Common #11047: concentration damagepercent * Blessed Hammer Param1 / 8. */
  public static int getBlessedHammerConcentrationPercent(
      Skills.Entry skill, int concentrationDamagePercent) {
    if (!isBlessedHammer(skill) || concentrationDamagePercent <= 0) return 0;
    int scale = skill.Param != null && skill.Param.length > 0 ? skill.Param[0] : 0;
    return Math.max(0, concentrationDamagePercent * scale / 8);
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
    if (shift > 0) value <<= Math.min(30, shift);
    else if (shift < 0) value >>= Math.min(30, -shift);
    return saturated(value);
  }

  private static int arrayValue(int[] values, int index) {
    return values != null && index >= 0 && index < values.length ? values[index] : 0;
  }

  private static int saturated(long value) {
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
  }

  /**
   * 皈依 - 使敌人变成友方
   * 
   * @param skillLevel 技能等级
   * @return 转化概率百分比
   */
  public static int getConversionChance(int skillLevel) {
    // 每级 +4%
    return 4 * skillLevel;
  }

  /** Native Skills.txt SrvSt36/SrvDo018 Holy Shield gate and state payload. */
  public static boolean isHolyShield(Skills.Entry skill) {
    return skill != null && (skill.Id == SkillId.HOLY_SHIELD || skill.srvstfunc == 36);
  }

  /** Evaluates the native Holy Shield duration (ln12) in simulation frames. */
  public static int getHolyShieldDuration(Skills.Entry skill, int skillLevel) {
    if (!isHolyShield(skill)) return 0;
    return Math.max(1, SkillFormula.evaluate(skill.auralencalc, skill,
        Math.max(1, skillLevel)));
  }

  /** Native aurastat1=toblock (dm56), retained as a state-owned stat-list entry. */
  public static int getHolyShieldBlockBonus(Skills.Entry skill, int skillLevel) {
    if (!isHolyShield(skill) || skill.aurastatcalc == null
        || skill.aurastatcalc.length == 0) return 0;
    return Math.max(0, SkillFormula.evaluate(skill.aurastatcalc[0], skill,
        Math.max(1, skillLevel)));
  }

  /** Native Units_GetDefense contribution (Holy Shield calc1). */
  public static int getHolyShieldDefenseBonus(Skills.Entry skill, int skillLevel,
      ToIntFunction<String> baseSkillLevel) {
    if (!isHolyShield(skill)) return 0;
    return Math.max(0, SkillFormula.evaluate(skill.calc1, skill,
        Math.max(1, skillLevel), baseSkillLevel));
  }

  /** Compatibility overload for callers without synergy context. */
  public static int calculateHolyShieldDefenseBonus(int skillLevel) {
    return 25 + Math.max(0, skillLevel - 1) * 15;
  }

  public static int calculateHolyShieldBlockBonus(int skillLevel) {
    // Fallback for synthetic rows; real casts always use Skills.txt dm56.
    return Math.max(0, 14 + Math.max(0, skillLevel - 1) * 3);
  }

  /**
   * Installs the single source-owned Holy Shield stat list. Recasting replaces
   * the old layer instead of stacking block/defense indefinitely.
   */
  public static UnitState applyHolyShieldState(StateList states, Skills.Entry skill,
      int skillLevel, int sourceEntityId,
      ToIntFunction<String> baseSkillLevel) {
    if (states == null || !isHolyShield(skill)) return null;
    int level = Math.max(1, skillLevel);
    int duration = getHolyShieldDuration(skill, level);
    if (duration <= 0) return null;
    states.removeState(StateId.HOLYSHIELD);
    UnitState state = states.addState(StateId.HOLYSHIELD, duration, level, sourceEntityId);
    if (state == null) return null;
    state.skillId = skill.Id;
    state.duration = duration;
    state.initialDuration = duration;
    state.level = level;
    state.sourceEntityId = sourceEntityId;
    state.clearModifiers();
    int block = getHolyShieldBlockBonus(skill, level);
    if (block > 0) {
      state.setStatContribution(Stat.toblock, 0,
          NativeStatResolver.Operation.ADD, block);
    }
    int defense = getHolyShieldDefenseBonus(skill, level,
        baseSkillLevel == null ? name -> 0 : baseSkillLevel);
    if (defense > 0) state.setNativeModifier(Stat.skill_armor_percent, defense);
    // StateP has no generic stat-contribution vectors; retain the evaluated
    // defense value in its replicated runtime scalar so clients with a
    // different local hard-point view still reproduce the server result.
    state.runtimeValue = defense;
    state.needsSync = true;
    log.info("[PALADIN_HOLY_SHIELD] phase=apply source={} skill={} level={} duration={} "
            + "block={} defense={} status=PASS",
        sourceEntityId, skill.Id, level, duration, block, defense);
    return state;
  }

  /**
   * 天堂之拳 - 召唤闪电攻击
   * 
   * @param skillLevel 技能等级
   * @return 伤害
   */
  public static int calculateFistOfHeavensDamage(int skillLevel) {
    // 基础 30-50，每级 +15-20
    int minDamage = 30 + (skillLevel - 1) * 15;
    int maxDamage = 50 + (skillLevel - 1) * 20;
    return MathUtils.random(minDamage, maxDamage);
  }

  //==========================================================================
  // 攻击光环
  //==========================================================================

  /**
   * 力量 - 增加物理伤害
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateMightDamageBonus(int skillLevel) {
    // 基础 40%，每级 +20%
    return 40 + (skillLevel - 1) * 20;
  }

  /**
   * 圣火 - 火焰伤害光环
   * 
   * @param skillLevel 技能等级
   * @return 火焰伤害
   */
  public static int calculateHolyFireDamage(int skillLevel) {
    // 基础 6-8，每级 +4-5
    int minDamage = 6 + (skillLevel - 1) * 4;
    int maxDamage = 8 + (skillLevel - 1) * 5;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 荆棘 - 返还伤害
   * 
   * @param skillLevel 技能等级
   * @return 返还百分比
   */
  public static int calculateThornsReflectPercent(int skillLevel) {
    // 基础 250%，每级 +50%
    return 250 + (skillLevel - 1) * 50;
  }

  /**
   * 祝福瞄准 - 增加攻击等级
   * 
   * @param skillLevel 技能等级
   * @return 攻击等级加成百分比
   */
  public static int calculateBlessedAimBonus(int skillLevel) {
    // 基础 75%，每级 +15%
    return 75 + (skillLevel - 1) * 15;
  }

  /**
   * 专注 - 增加伤害和不可打断
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateConcentrationDamageBonus(int skillLevel) {
    // 基础 60%，每级 +15%
    return 60 + (skillLevel - 1) * 15;
  }

  /**
   * 圣冻 - 冰冷伤害光环
   * 
   * @param skillLevel 技能等级
   * @return 冰冷伤害
   */
  public static int calculateHolyFreezeDamage(int skillLevel) {
    // 基础 6-8，每级 +3-4
    int minDamage = 6 + (skillLevel - 1) * 3;
    int maxDamage = 8 + (skillLevel - 1) * 4;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 圣冻减速
   * 
   * @param skillLevel 技能等级
   * @return 减速百分比
   */
  public static int getHolyFreezeSlowPercent(int skillLevel) {
    // 固定 50%
    return 50;
  }

  /**
   * 圣击 - 闪电伤害光环
   * 
   * @param skillLevel 技能等级
   * @return 闪电伤害
   */
  public static int calculateHolyShockDamage(int skillLevel) {
    // 基础 1-10，每级 +1-8
    int minDamage = 1 + (skillLevel - 1);
    int maxDamage = 10 + (skillLevel - 1) * 8;
    return MathUtils.random(minDamage, maxDamage);
  }

  /**
   * 狂热 - 增加伤害、攻击速度和攻击等级
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateFanaticismDamageBonus(int skillLevel) {
    // 基础 180%，每级 +21%
    return 180 + (skillLevel - 1) * 21;
  }

  /**
   * 狂热攻击速度加成
   * 
   * @param skillLevel 技能等级
   * @return 攻击速度加成百分比
   */
  public static int calculateFanaticismIasBonus(int skillLevel) {
    // 固定 35%
    return 35;
  }

  /**
   * 信念 - 降低敌人抗性
   * 
   * @param skillLevel 技能等级
   * @return 抗性降低量
   */
  public static int calculateConvictionResistReduce(int skillLevel) {
    // 基础 -30%，每级 -5%（最高 -150%）
    return Math.min(150, 30 + (skillLevel - 1) * 5);
  }

  //==========================================================================
  // 防御光环
  //==========================================================================

  /**
   * 祈祷 - 恢复生命
   * 
   * @param skillLevel 技能等级
   * @return 每秒恢复生命
   */
  public static int calculatePrayerHealPerSecond(int skillLevel) {
    // 基础 2，每级 +1
    return 2 + (skillLevel - 1);
  }

  /**
   * 抵御火焰/冰冷/闪电 - 增加抗性
   * 
   * @param skillLevel 技能等级
   * @return 抗性加成
   */
  public static int calculateResistAuraBonus(int skillLevel) {
    // 基础 30%，每级 +5%（最高 +75%）
    return Math.min(75, 30 + (skillLevel - 1) * 5);
  }

  /**
   * 反抗 - 增加防御
   * 
   * @param skillLevel 技能等级
   * @return 防御加成百分比
   */
  public static int calculateDefianceDefenseBonus(int skillLevel) {
    // 基础 70%，每级 +15%
    return 70 + (skillLevel - 1) * 15;
  }

  /**
   * 净化 - 降低诅咒和毒素持续时间
   * 
   * @param skillLevel 技能等级
   * @return 持续时间降低百分比
   */
  public static int calculateCleansingReducePercent(int skillLevel) {
    // 基础 -50%，每级 -6%
    return 50 + (skillLevel - 1) * 6;
  }

  /**
   * 活力 - 增加移动速度和耐力恢复
   * 
   * @param skillLevel 技能等级
   * @return 速度加成百分比
   */
  public static int calculateVigorSpeedBonus(int skillLevel) {
    // 固定 +40% 速度
    return 40;
  }

  /**
   * 冥想 - 增加法力恢复
   * 
   * @param skillLevel 技能等级
   * @return 法力恢复加成百分比
   */
  public static int calculateMeditationManaRegen(int skillLevel) {
    // 基础 100%，每级 +40%
    return 100 + (skillLevel - 1) * 40;
  }

  /**
   * 救赎 - 从尸体恢复生命/法力
   * 
   * @param skillLevel 技能等级
   * @return 恢复概率百分比
   */
  public static int getRedemptionChance(int skillLevel) {
    // 基础 20%，每级 +4%
    return 20 + (skillLevel - 1) * 4;
  }

  /**
   * 救赎恢复量
   * 
   * @param skillLevel 技能等级
   * @return 生命/法力恢复量
   */
  public static int calculateRedemptionHeal(int skillLevel) {
    // 基础 25，每级 +7
    return 25 + (skillLevel - 1) * 7;
  }

  /**
   * 救世 - 增加所有抗性
   * 
   * @param skillLevel 技能等级
   * @return 所有抗性加成
   */
  public static int calculateSalvationResistBonus(int skillLevel) {
    // 基础 25%，每级 +5%
    return 25 + (skillLevel - 1) * 5;
  }
}
