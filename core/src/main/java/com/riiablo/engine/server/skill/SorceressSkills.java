package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.DifficultyLevels;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import java.util.function.ToIntFunction;

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

  /** Native {@code SrvDo025} discriminator. */
  public static boolean isEnchant(Skills.Entry skill) {
    return skill != null && skill.srvdofunc == 25
        && "Enchant".equalsIgnoreCase(skill.skill);
  }

  /** Native {@code SrvDo018} Sorceress cold-armor family. */
  public static boolean isDefensiveArmor(Skills.Entry skill) {
    return defensiveArmorStateId(skill) != StateId.NONE;
  }

  /** Maps the three mutually-exclusive native group-1 armor states. */
  public static int defensiveArmorStateId(Skills.Entry skill) {
    if (skill == null || skill.srvdofunc != 18 || skill.aurastate == null) {
      return StateId.NONE;
    }
    switch (skill.aurastate.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "frozenarmor": return StateId.FROZENARMOR;
      case "shiverarmor": return StateId.SHIVERARMOR;
      case "chillingarmor": return StateId.CHILLINGARMOR;
      default: return StateId.NONE;
    }
  }

  /** Evaluates the native group-1 armor duration including hard-point synergies. */
  public static int getDefensiveArmorDuration(Skills.Entry skill, int skillLevel,
      ToIntFunction<String> baseSkillLevel) {
    if (!isDefensiveArmor(skill)) return 0;
    return Math.max(1, SkillFormula.evaluate(skill.auralencalc, skill,
        Math.max(1, skillLevel), baseSkillLevel == null ? name -> 0 : baseSkillLevel));
  }

  /** Native {@code aurastat1=skill_armor_percent}. */
  public static int getDefensiveArmorDefensePercent(Skills.Entry skill, int skillLevel) {
    if (!isDefensiveArmor(skill) || skill.aurastat == null
        || skill.aurastatcalc == null) return 0;
    int count = Math.min(skill.aurastat.length, skill.aurastatcalc.length);
    for (int i = 0; i < count; i++) {
      if (skill.aurastat[i] != null
          && !skill.aurastat[i].trim().isEmpty()
          && Stat.index(skill.aurastat[i]) == Stat.skill_armor_percent) {
        return Math.max(0, SkillFormula.evaluate(
            skill.aurastatcalc[i], skill, Math.max(1, skillLevel)));
      }
    }
    return 0;
  }

  /** Frozen Armor's native {@code calc1} freeze length. */
  public static int getFrozenArmorFreezeLength(Skills.Entry skill, int skillLevel,
      ToIntFunction<String> baseSkillLevel) {
    if (defensiveArmorStateId(skill) != StateId.FROZENARMOR) return 0;
    return Math.max(0, SkillFormula.evaluate(skill.calc1, skill,
        Math.max(1, skillLevel), baseSkillLevel == null ? name -> 0 : baseSkillLevel));
  }

  /** Shiver/Chilling Armor elemental damage in ordinary life-point units. */
  public static int[] getArmorColdDamage(Skills.Entry skill, int skillLevel,
      ToIntFunction<String> baseSkillLevel) {
    if (skill == null || (defensiveArmorStateId(skill) != StateId.SHIVERARMOR
        && defensiveArmorStateId(skill) != StateId.CHILLINGARMOR)) {
      return new int[] {0, 0};
    }
    int level = Math.max(1, skillLevel);
    int synergy = Math.max(0, SkillFormula.evaluate(skill.EDmgSymPerCalc, skill,
        level, baseSkillLevel == null ? name -> 0 : baseSkillLevel));
    int min = scaleElementalDamage(skill.EMin, skill.EMinLev, level, skill.HitShift);
    int max = scaleElementalDamage(skill.EMax, skill.EMaxLev, level, skill.HitShift);
    min += min * synergy / 100;
    max += max * synergy / 100;
    return new int[] {Math.max(0, min), Math.max(Math.max(0, min), max)};
  }

  /** Native cold length used by Shiver Armor's direct retaliation packet. */
  public static int getArmorColdLength(Skills.Entry skill, int skillLevel,
      ToIntFunction<String> baseSkillLevel) {
    if (skill == null || defensiveArmorStateId(skill) != StateId.SHIVERARMOR) return 0;
    int level = Math.max(1, skillLevel);
    int length = Math.max(0, skill.ELen + damageBonusByLevel(level, skill.ELevLen));
    int synergy = Math.max(0, SkillFormula.evaluate(skill.ELenSymPerCalc, skill,
        level, baseSkillLevel == null ? name -> 0 : baseSkillLevel));
    return Math.max(0, length + length * synergy / 100);
  }

  /** Enchant duration from {@code AuraLenCalc=ln12}. */
  public static int getEnchantDuration(Skills.Entry skill, int skillLevel) {
    if (!isEnchant(skill)) return 0;
    return Math.max(1, SkillFormula.evaluate(
        skill.auralencalc, skill, Math.max(1, skillLevel)));
  }

  /**
   * Native {@code enma/exma} values. These special skill-calc opcodes return
   * mastered skill elemental damage; the Java formula parser deliberately
   * keeps this unit-aware operation here instead of treating it as a generic
   * arithmetic identifier.
   */
  public static int[] getEnchantDamage(Skills.Entry skill, int skillLevel,
      ToIntFunction<String> baseSkillLevel, int fireMasteryPercent) {
    if (!isEnchant(skill)) return new int[] {0, 0};
    int level = Math.max(1, skillLevel);
    int synergy = Math.max(0, SkillFormula.evaluate(skill.EDmgSymPerCalc, skill,
        level, baseSkillLevel == null ? name -> 0 : baseSkillLevel));
    int min = scaleElementalDamage(skill.EMin, skill.EMinLev, level, skill.HitShift);
    int max = scaleElementalDamage(skill.EMax, skill.EMaxLev, level, skill.HitShift);
    min += min * synergy / 100;
    max += max * synergy / 100;
    int mastery = Math.max(0, fireMasteryPercent);
    min += min * mastery / 100;
    max += max * mastery / 100;
    return new int[] {Math.max(0, min), Math.max(Math.max(0, min), max)};
  }

  /** Native {@code aurastatcalc3=toht}. */
  public static int getEnchantAttackRatingPercent(Skills.Entry skill, int skillLevel) {
    if (!isEnchant(skill)) return 0;
    return Math.max(0, SkillFormula.evaluate("toht", skill, Math.max(1, skillLevel)));
  }

  /** Installs a single native group-1 self armor stat list. */
  public static UnitState applyDefensiveArmorState(StateList states, Skills.Entry skill,
      int skillLevel, int sourceEntityId, ToIntFunction<String> baseSkillLevel) {
    int stateId = defensiveArmorStateId(skill);
    if (states == null || stateId == StateId.NONE) return null;
    states.removeState(StateId.FROZENARMOR);
    states.removeState(StateId.SHIVERARMOR);
    states.removeState(StateId.CHILLINGARMOR);
    int level = Math.max(1, skillLevel);
    int duration = getDefensiveArmorDuration(skill, level, baseSkillLevel);
    UnitState state = states.addStateLayer(
        stateId, duration, level, sourceEntityId, skill.Id);
    if (state == null) return null;
    int defense = getDefensiveArmorDefensePercent(skill, level);
    state.setStatContribution(Stat.skill_armor_percent, 0,
        NativeStatResolver.Operation.ADD, defense);
    state.runtimeValue = defense;
    state.needsSync = true;
    return state;
  }

  /** Installs the target-owned SrvDo025 Enchant stat list. */
  public static UnitState applyEnchantState(StateList states, Skills.Entry skill,
      int skillLevel, int sourceEntityId, ToIntFunction<String> baseSkillLevel,
      int fireMasteryPercent) {
    if (states == null || !isEnchant(skill)) return null;
    int level = Math.max(1, skillLevel);
    int duration = getEnchantDuration(skill, level);
    int[] damage = getEnchantDamage(
        skill, level, baseSkillLevel, fireMasteryPercent);
    int attackRating = getEnchantAttackRatingPercent(skill, level);
    states.removeState(StateId.ENCHANT);
    UnitState state = states.addStateLayer(
        StateId.ENCHANT, duration, level, sourceEntityId, skill.Id);
    if (state == null) return null;
    state.setStatContribution(Stat.firemindam, 0,
        NativeStatResolver.Operation.ADD, damage[0]);
    state.setStatContribution(Stat.firemaxdam, 0,
        NativeStatResolver.Operation.ADD, damage[1]);
    state.setStatContribution(Stat.item_tohit_percent, 0,
        NativeStatResolver.Operation.ADD, attackRating);
    state.runtimeValue = damage[0];
    state.needsSync = true;
    return state;
  }

  /** Fire Mastery's permanent passive state/stat list. */
  public static int getFireMasteryPercent(Skills.Entry skill, int skillLevel) {
    if (skill == null || skill.Id != SkillId.FIRE_MASTERY || skillLevel <= 0) return 0;
    int count = Math.min(skill.passivestat != null ? skill.passivestat.length : 0,
        skill.passivecalc != null ? skill.passivecalc.length : 0);
    for (int i = 0; i < count; i++) {
      String stat = skill.passivestat[i];
      if (stat != null && Stat.index(stat) == Stat.passive_fire_mastery) {
        return Math.max(0, SkillFormula.evaluate(
            skill.passivecalc[i], skill, Math.max(1, skillLevel)));
      }
    }
    return 0;
  }

  /** Fire Mastery's permanent passive state/stat list. */
  public static UnitState applyFireMasteryState(
      StateList states, Skills.Entry skill, int skillLevel, int sourceEntityId) {
    if (states == null || skill == null || skill.Id != SkillId.FIRE_MASTERY
        || !skill.passive || skillLevel <= 0) return null;
    int level = Math.max(1, skillLevel);
    int value = getFireMasteryPercent(skill, level);
    states.removeState(StateId.FIREMASTERY);
    UnitState state = states.addStateLayer(
        StateId.FIREMASTERY, 0, level, sourceEntityId, skill.Id);
    if (state == null) return null;
    state.setStatContribution(Stat.passive_fire_mastery, 0,
        NativeStatResolver.Operation.ADD, value);
    state.needsSync = true;
    return state;
  }

  private static int scaleElementalDamage(
      int base, int[] perLevel, int level, int hitShift) {
    long value = Math.max(0L, (long) base + damageBonusByLevel(level, perLevel));
    int shift = hitShift - 8;
    if (shift > 0) value <<= Math.min(shift, 30);
    else if (shift < 0) value >>= Math.min(-shift, 30);
    return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
  }

  private static int damageBonusByLevel(int level, int[] values) {
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

  /** D2MOO {@code SKILLS_SrvDo020_StaticField}. */
  public static boolean isStaticField(Skills.Entry skill) {
    return skill != null && skill.srvdofunc == 20
        && "Static Field".equalsIgnoreCase(skill.skill);
  }

  /** Native {@code SKILLS_SrvDo023_Blaze_EnergyShield_SpiderLay} Blaze row. */
  public static boolean isBlaze(Skills.Entry skill) {
    return skill != null && skill.srvdofunc == 23
        && "Blaze".equalsIgnoreCase(skill.skill);
  }

  /** Blaze's native self-state duration from {@code AuraLenCalc}. */
  public static int getBlazeDuration(Skills.Entry skill, int skillLevel,
      ToIntFunction<String> baseSkillLevel) {
    if (!isBlaze(skill)) return 0;
    return Math.max(1, SkillFormula.evaluate(skill.auralencalc, skill,
        Math.max(1, skillLevel), baseSkillLevel == null ? name -> 0 : baseSkillLevel));
  }

  /** Native {@code SKILLS_SrvDo024_FireWall} row discriminator. */
  public static boolean isFireWall(Skills.Entry skill) {
    return skill != null && skill.srvdofunc == 24
        && "Fire Wall".equalsIgnoreCase(skill.skill);
  }

  /** Native {@code AuraRangeCalc}; 1.10f uses {@code ln12}. */
  public static int getStaticFieldRadius(Skills.Entry skill, int skillLevel) {
    return Math.max(0, SkillFormula.evaluate(
        skill != null ? skill.aurarangecalc : null, skill, Math.max(1, skillLevel)));
  }

  /** Native {@code calc1}; 1.10f uses {@code par4}=25 percent of current life. */
  public static int getStaticFieldDamagePercent(Skills.Entry skill, int skillLevel) {
    return Math.max(0, SkillFormula.evaluate(
        skill != null ? skill.calc1 : null, skill, Math.max(1, skillLevel)));
  }

  /** Native {@code calc2}, retained in D2's signed 8.8 damage unit. */
  public static int getStaticFieldMinimumDamageFixed(Skills.Entry skill, int skillLevel) {
    return Math.max(0, SkillFormula.evaluate(
        skill != null ? skill.calc2 : null, skill, Math.max(1, skillLevel)));
  }

  /** Expansion-only difficulty floor read from DifficultyLevels.txt. */
  public static int getStaticFieldLifeFloorPercent(
      DifficultyLevels.Entry difficulty, boolean expansion) {
    return expansion && difficulty != null ? Math.max(0, difficulty.StaticFieldMin) : 0;
  }

  /**
   * Reproduces the integer-life arithmetic in
   * {@code SKILLS_AuraCallback_StaticField} and returns signed 8.8 damage.
   * The difficulty floor is an eligibility threshold, not a post-hit clamp:
   * a hit starting just above the floor may cross below it, exactly as in
   * D2Game 1.10f.
   */
  public static int calculateStaticFieldRawDamageFixed(
      int currentLifeFixed, int maximumLifeFixed, int damagePercent,
      int minimumDamageFixed, int lifeFloorPercent) {
    int currentLife = Math.max(0, currentLifeFixed) >> 8;
    if (currentLife < 1) return 0;
    int maximumLife = Math.max(0, maximumLifeFixed) >> 8;
    if (lifeFloorPercent > 0
        && currentLife <= percentage(maximumLife, lifeFloorPercent, 100)) return 0;
    int shiftedDamage = percentage(currentLife, Math.max(0, damagePercent), 100);
    shiftedDamage = Math.min(shiftedDamage, currentLife - 1);
    long damageFixed = (long) Math.max(0, shiftedDamage) << 8;
    damageFixed = Math.max(damageFixed, Math.max(0, minimumDamageFixed));
    return damageFixed >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) damageFixed;
  }

  private static int percentage(int value, int multiplier, int divisor) {
    if (divisor == 0) return 0;
    long result = (long) value * multiplier / divisor;
    if (result > Integer.MAX_VALUE) return Integer.MAX_VALUE;
    if (result < Integer.MIN_VALUE) return Integer.MIN_VALUE;
    return (int) result;
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
