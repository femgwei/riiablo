package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.DifficultyLevels;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.States;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.item.Type;

import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 死灵法师技能实现 - 基于 D2MOD SkillNec.cpp 移植
 * 
 * <p>包含诅咒、毒素和骨、召唤三系技能的实现。
 * 
 * <p>参考：D2MOD/source/D2Game/src/SKILLS/SkillNec.cpp
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

  /** Legacy UI estimate backed by the loaded 1.10f Skills.txt row. */
  public static int calculatePoisonDaggerDamage(int skillLevel) {
    Skills.Entry skill = com.riiablo.Riiablo.files != null
        ? com.riiablo.Riiablo.files.skills.get(SkillId.POISON_DAGGER) : null;
    int[] rate = getPoisonDaggerDamage(skill, skillLevel, name -> 0);
    int duration = getPoisonDaggerDurationFrames(skill, skillLevel, name -> 0);
    return Math.round(MathUtils.random(rate[0], rate[1]) / 256f * duration);
  }

  /** Legacy UI duration in seconds, derived from D2's 25 Hz frame count. */
  public static float getPoisonDaggerDuration(int skillLevel) {
    Skills.Entry skill = com.riiablo.Riiablo.files != null
        ? com.riiablo.Riiablo.files.skills.get(SkillId.POISON_DAGGER) : null;
    return getPoisonDaggerDurationFrames(skill, skillLevel, name -> 0) / 25f;
  }

  public static boolean isPoisonDagger(Skills.Entry skill) {
    return skill != null && skill.srvstfunc == 16 && skill.srvdofunc == 32;
  }

  /** D2Common's Poison Dagger item gate: a melee dagger, not a throwing knife. */
  public static boolean isPoisonDaggerWeapon(Item weapon) {
    return weapon != null && weapon.base instanceof Weapons.Entry && weapon.type != null
        && weapon.type.is(Type.KNIF) && !weapon.type.is(Type.TKNI)
        && !weapon.type.is(Type.THRO);
  }

  /** Native SKILLS_GetToHitFactor for the SrvSt16 combat roll. */
  public static int getPoisonDaggerAttackRating(
      Skills.Entry skill, int skillLevel, Attributes attacker, boolean player) {
    int base = statInt(attacker, Stat.tohit);
    int factor = skill == null ? 0
        : skill.ToHit + (Math.max(1, skillLevel) - 1) * skill.LevToHit;
    return player ? Math.max(1, base * Math.max(0, 100 + factor) / 100)
        : Math.max(1, base + factor);
  }

  /** Full physical packet filled by SrvSt16 with Skills.txt SrcDam/calc1. */
  public static int[] getPoisonDaggerPhysicalDamage(
      Skills.Entry skill, int skillLevel, Attributes attacker, Item weapon,
      StateList states) {
    if (!isPoisonDagger(skill) || !isPoisonDaggerWeapon(weapon)) return new int[] {0, 0};
    Weapons.Entry base = (Weapons.Entry) weapon.base;
    int min = itemStatInt(weapon, Stat.mindamage, base.mindam);
    int max = itemStatInt(weapon, Stat.maxdamage, Math.max(min, base.maxdam));
    int percent = SkillFormula.evaluate(skill.calc1, skill, Math.max(1, skillLevel))
        + base.StrBonus * statInt(attacker, Stat.strength) / 100
        + base.DexBonus * statInt(attacker, Stat.dexterity) / 100
        + statInt(attacker, Stat.damagepercent)
        + statInt(attacker, Stat.item_maxdamage_percent);
    if (states != null) percent += states.getTotalDamageModifier();
    int sourceDamage = skill.SrcDam == 0 ? 128 : skill.SrcDam;
    return new int[] {
        scaleSource(scalePercent(min, percent), sourceDamage),
        scaleSource(scalePercent(Math.max(min, max), percent), sourceDamage)};
  }

  /** Native 8.8 poison rate from EMin/EMax and EDmgSymPerCalc. */
  public static int[] getPoisonDaggerDamage(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    if (!isPoisonDagger(skill)) return new int[] {0, 0};
    int level = Math.max(1, skillLevel);
    long min = Math.max(0L, (long) skill.EMin + damageBonusByLevel(level, skill.EMinLev));
    long max = Math.max(min, (long) skill.EMax + damageBonusByLevel(level, skill.EMaxLev));
    min <<= Math.min(Math.max(0, skill.HitShift), 30);
    max <<= Math.min(Math.max(0, skill.HitShift), 30);
    int synergy = Math.max(0, SkillFormula.evaluate(
        skill.EDmgSymPerCalc, skill, level, baseSkillLevel));
    min += min * synergy / 100;
    max += max * synergy / 100;
    return new int[] {saturated(min), saturated(max)};
  }

  public static int getPoisonDaggerDurationFrames(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    if (!isPoisonDagger(skill)) return 0;
    int level = Math.max(1, skillLevel);
    return Math.max(10, skill.ELen + damageBonusByLevel(level, skill.ELevLen)
        + SkillFormula.evaluate(skill.ELenSymPerCalc, skill, level, baseSkillLevel));
  }

  private static int itemStatInt(Item item, short stat, int fallback) {
    if (item == null || item.attrs == null) return fallback;
    StatRef ref = item.attrs.get(stat, StatRef.obtain());
    if (ref == null) ref = item.attrs.base().get(stat, StatRef.obtain());
    return ref == null ? fallback : ref.asInt();
  }

  private static int statInt(Attributes attrs, short stat) {
    if (attrs == null) return 0;
    StatRef ref = attrs.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }

  private static int damageBonusByLevel(int level, int[] values) {
    if (level <= 1 || values == null || values.length == 0) return 0;
    int l1 = values.length > 0 ? values[0] : 0;
    int l2 = values.length > 1 ? values[1] : 0;
    int l3 = values.length > 2 ? values[2] : 0;
    int l4 = values.length > 3 ? values[3] : l3;
    int l5 = values.length > 4 ? values[4] : l4;
    if (level > 28) return 7 * l1 + 8 * l2 + 6 * (l3 + l4) + (level - 28) * l5;
    if (level > 22) return 7 * l1 + 8 * l2 + 6 * l3 + (level - 22) * l4;
    if (level > 16) return 7 * l1 + 8 * l2 + (level - 16) * l3;
    if (level > 8) return 7 * l1 + (level - 8) * l2;
    return (level - 1) * l1;
  }

  private static int scalePercent(int value, int percent) {
    return saturated((long) Math.max(0, value) * Math.max(0, 100 + percent) / 100L);
  }

  private static int scaleSource(int value, int sourceDamage) {
    return saturated((long) Math.max(0, value) * Math.max(0, sourceDamage) / 128L);
  }

  private static int saturated(long value) {
    return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) value);
  }

  public static boolean isCorpseExplosion(Skills.Entry skill) {
    return skill != null && skill.srvstfunc == 17 && skill.srvdofunc == 55;
  }

  public static boolean isPoisonExplosion(Skills.Entry skill) {
    return skill != null && skill.srvstfunc == 17 && skill.srvdofunc == 63;
  }

  /** Native SrvDo055 minimum/maximum corpse-life percentage from calc1/calc2. */
  public static int[] getCorpseExplosionDamagePercent(Skills.Entry skill, int skillLevel) {
    if (!isCorpseExplosion(skill)) return new int[] {0, 0};
    int level = Math.max(1, skillLevel);
    int min = Math.max(0, SkillFormula.evaluate(skill.calc1, skill, level));
    int max = Math.max(min, SkillFormula.evaluate(skill.calc2, skill, level));
    return new int[] {min, max};
  }

  /** Native calc3 elemental split, clamped by SrvDo055 to 0..100. */
  public static int getCorpseExplosionElementalPercent(Skills.Entry skill, int skillLevel) {
    if (!isCorpseExplosion(skill)) return 0;
    return Math.max(0, Math.min(100,
        SkillFormula.evaluate(skill.calc3, skill, Math.max(1, skillLevel))));
  }

  /** Returns D2's integer inner/outer radii after the ln34 half-square conversion. */
  public static int[] getCorpseExplosionRadii(Skills.Entry skill, int skillLevel) {
    if (!isCorpseExplosion(skill)) return new int[] {0, 0};
    int range = Math.max(0,
        SkillFormula.evaluate(skill.aurarangecalc, skill, Math.max(1, skillLevel)));
    return new int[] {range / 2, (range + 1) / 2};
  }

  /** Compatibility estimate retained for UI callers. */
  public static int calculateCorpseExplosionDamage(int corpseMaxHp) {
    return Math.round(Math.max(0, corpseMaxHp) * MathUtils.random(0.7f, 1.2f));
  }

  /**
   * 获取尸体爆炸半径
   * 
   * @param skillLevel 技能等级
   * @return 半径（子格）
   */
  public static int getCorpseExplosionRadius(int skillLevel) {
    Skills.Entry skill = com.riiablo.Riiablo.files != null
        ? com.riiablo.Riiablo.files.skills.get(SkillId.CORPSE_EXPLOSION) : null;
    return getCorpseExplosionRadii(skill, skillLevel)[1];
  }

  /** Native Poison Explosion 8.8 poison rate, including hard-point synergies. */
  public static int[] getPoisonExplosionDamage(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    if (!isPoisonExplosion(skill)) return new int[] {0, 0};
    int level = Math.max(1, skillLevel);
    long min = Math.max(0L, (long) skill.EMin + damageBonusByLevel(level, skill.EMinLev));
    long max = Math.max(min, (long) skill.EMax + damageBonusByLevel(level, skill.EMaxLev));
    min <<= Math.min(Math.max(0, skill.HitShift), 30);
    max <<= Math.min(Math.max(0, skill.HitShift), 30);
    int synergy = Math.max(0, SkillFormula.evaluate(
        skill.EDmgSymPerCalc, skill, level, baseSkillLevel));
    min += min * synergy / 100;
    max += max * synergy / 100;
    return new int[] {saturated(min), saturated(max)};
  }

  public static int getPoisonExplosionDurationFrames(Skills.Entry skill, int skillLevel) {
    if (!isPoisonExplosion(skill)) return 0;
    return Math.max(1, skill.ELen + damageBonusByLevel(
        Math.max(1, skillLevel), skill.ELevLen));
  }

  private static final int[] BONE_PRISON_X = {
      -1, 1, 3, 4, 4, 3, -1, 1, -3, -4, -4, -3
  };
  private static final int[] BONE_PRISON_Y = {
      -4, -4, -3, -1, 1, 3, 4, 4, 3, -1, 1, -3
  };

  public static boolean isBoneWall(Skills.Entry skill) {
    return skill != null && skill.srvdofunc == 60;
  }

  public static boolean isBonePrison(Skills.Entry skill) {
    return skill != null && skill.srvstfunc == 19 && skill.srvdofunc == 62;
  }

  /** Native calc1 maximum-life percentage applied by SetSummonPassiveStats. */
  public static int getBoneWallLifePercent(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    if (!isBoneWall(skill) && !isBonePrison(skill)) return 0;
    return SkillFormula.evaluate(skill.calc1, skill, Math.max(1, skillLevel),
        baseSkillLevel == null ? name -> 0 : baseSkillLevel);
  }

  /** BoneWall AI Param2: native lifetime in 25 Hz game frames. */
  public static int getBoneWallDurationFrames(Skills.Entry skill) {
    return skill != null && skill.Param != null && skill.Param.length > 1
        ? Math.max(1, skill.Param[1]) : 1;
  }

  /** Native SrvDo060 evaluates calc2, divides by two and launches two makers. */
  public static int getBoneWallSegmentsPerSide(Skills.Entry skill, int skillLevel) {
    if (!isBoneWall(skill)) return 0;
    return Math.max(0, Math.min(32,
        SkillFormula.evaluate(skill.calc2, skill, Math.max(1, skillLevel)) / 2));
  }

  public static int getBonePrisonSegmentCount() {
    return BONE_PRISON_X.length;
  }

  /** Copies one of D2MOO SrvDo062's twelve fixed offsets. */
  public static void getBonePrisonOffset(int index, int[] out) {
    if (out == null || out.length < 2) return;
    int normalized = Math.max(0, Math.min(BONE_PRISON_X.length - 1, index));
    out[0] = BONE_PRISON_X[normalized];
    out[1] = BONE_PRISON_Y[normalized];
  }

  /** Compatibility estimate retained for UI callers. */
  public static int calculateBoneWallHp(int skillLevel) {
    Skills.Entry skill = com.riiablo.Riiablo.files != null
        ? com.riiablo.Riiablo.files.skills.get(SkillId.BONE_WALL) : null;
    int percent = getBoneWallLifePercent(skill, skillLevel, name -> 0);
    return Math.max(1, Math.round(19f * (100f + percent) / 100f));
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

  /** Compatibility estimate retained for UI callers. */
  public static int calculateBonePrisonHp(int skillLevel) {
    Skills.Entry skill = com.riiablo.Riiablo.files != null
        ? com.riiablo.Riiablo.files.skills.get(SkillId.BONE_PRISON) : null;
    int percent = getBoneWallLifePercent(skill, skillLevel, name -> 0);
    return Math.max(1, Math.round(19f * (100f + percent) / 100f));
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

  /**
   * Installs the native SrvDo018 stat-list used by Bone Armor.
   *
   * <p>The current and maximum shield values are sourced from the 1.10f
   * {@code AuraStat/AuraStatCalc} columns. Recasting replaces the old list and
   * restores the shield to its newly evaluated maximum, matching
   * {@code sub_6FD11C90(..., 1)} before the defensive-buff list is allocated.</p>
   */
  public static UnitState applyBoneArmorState(StateList states, Skills.Entry skill,
      int skillLevel, int sourceEntityId, ToIntFunction<String> baseSkillLevels,
      Function<String, Skills.Entry> skillResolver) {
    if (states == null || skill == null || skill.aurastate == null
        || !"bonearmor".equalsIgnoreCase(skill.aurastate.trim())) return null;
    int level = Math.max(1, skillLevel);
    int duration = Math.max(0, SkillFormula.evaluate(
        skill.auralencalc, skill, level, baseSkillLevels, skillResolver));
    int absorb = evaluateAuraStat(skill, level, Stat.bonearmor,
        baseSkillLevels, skillResolver);
    int maximum = evaluateAuraStat(skill, level, Stat.bonearmormax,
        baseSkillLevels, skillResolver);
    if (absorb <= 0) absorb = Math.max(0, SkillFormula.evaluate(
        skill.calc1, skill, level, baseSkillLevels, skillResolver));
    if (absorb <= 0) absorb = calculateBoneArmorAbsorb(level);
    if (maximum <= 0) maximum = absorb;
    absorb = Math.min(absorb, maximum);
    if (absorb <= 0) return null;

    states.removeState(StateId.BONEARMOR);
    UnitState state = states.addStateLayer(
        StateId.BONEARMOR, duration, level, sourceEntityId, skill.Id);
    if (state == null) return null;
    state.setStatContribution(
        Stat.bonearmor, 0, NativeStatResolver.Operation.ADD, absorb);
    state.setStatContribution(
        Stat.bonearmormax, 0, NativeStatResolver.Operation.ADD, maximum);
    state.runtimeValue = absorb;
    state.needsSync = true;
    return state;
  }

  private static int evaluateAuraStat(Skills.Entry skill, int level, int statId,
      ToIntFunction<String> baseSkillLevels,
      Function<String, Skills.Entry> skillResolver) {
    if (skill.aurastat == null || skill.aurastatcalc == null) return 0;
    int count = Math.min(skill.aurastat.length, skill.aurastatcalc.length);
    for (int i = 0; i < count; i++) {
      String statName = skill.aurastat[i];
      if (statName == null || statName.trim().isEmpty()
          || Stat.index(statName.trim()) != statId) continue;
      return SkillFormula.evaluate(
          skill.aurastatcalc[i], skill, level, baseSkillLevels, skillResolver);
    }
    return 0;
  }

  /** Resolves the native Skills.txt aura target state used by SrvDo030. */
  public static int resolveCurseStateId(String auraTargetState, String skillName) {
    String value = auraTargetState == null ? "" : auraTargetState.trim();
    if (value.isEmpty()) value = skillName == null ? "" : skillName.trim();
    String name = value.toLowerCase(java.util.Locale.ROOT).replace("_", "");
    if (name.equals("amplifydamage") || name.contains("amplify")) return StateId.AMPLIFYDAMAGE;
    if (name.equals("dimvision") || name.contains("dimvision")) return StateId.DIMVISION;
    if (name.equals("weaken")) return StateId.WEAKEN;
    if (name.equals("ironmaiden")) return StateId.IRONMAIDEN;
    if (name.equals("terror")) return StateId.TERROR;
    if (name.equals("attract")) return StateId.ATTRACT;
    if (name.equals("confuse")) return StateId.CONFUSE;
    if (name.equals("lifetap")) return StateId.LIFETAP;
    if (name.equals("decrepify")) return StateId.DECREPIFY;
    if (name.equals("lowerresist")) return StateId.LOWERRESIST;
    return StateId.NONE;
  }

  /** Resolves a native aura stat name to the stable ItemStatCost/Stat id. */
  public static int resolveCurseStatId(String statName) {
    if (statName == null) return -1;
    String name = statName.trim().toLowerCase(java.util.Locale.ROOT).replace("_", "");
    switch (name) {
      case "damagepercent": return Stat.damagepercent;
      case "itemarmorpercent": return Stat.item_armor_percent;
      case "itemtohitpercent": return Stat.item_tohit_percent;
      case "damageresist": return Stat.damageresist;
      case "magicresist": return Stat.magicresist;
      case "fireresist": return Stat.fireresist;
      case "lightresist": return Stat.lightresist;
      case "coldresist": return Stat.coldresist;
      case "poisonresist": return Stat.poisonresist;
      case "velocitypercent": return Stat.velocitypercent;
      case "attackrate": return Stat.attackrate;
      case "otheranimrate": return Stat.other_animrate;
      case "lifedrainmindam": return Stat.lifedrainmindam;
      case "lifedrainmaxdam": return Stat.lifedrainmaxdam;
      case "stunlength": return Stat.stunlength;
      default: return -1;
    }
  }

  /** Native difficulty divisor used by Dim Vision/Terror/Attract/Confuse AI curses. */
  public static int curseDifficultyDivisor(DifficultyLevels.Entry difficulty) {
    return difficulty != null && difficulty.AiCurseDivisor > 0
        ? difficulty.AiCurseDivisor : 1;
  }

  /** Evaluates native curse length and applies the difficulty reduction where required. */
  public static int curseDuration(Skills.Entry skill, int skillLevel,
      DifficultyLevels.Entry difficulty) {
    int duration = SkillFormula.evaluate(skill == null ? null : skill.auralencalc,
        skill, Math.max(1, skillLevel));
    if (duration <= 0) duration = 1;
    int stateId = resolveCurseStateId(skill == null ? null : skill.auratargetstate,
        skill == null ? null : skill.skill);
    if (stateId == StateId.DIMVISION || stateId == StateId.TERROR
        || stateId == StateId.ATTRACT || stateId == StateId.CONFUSE) {
      duration /= curseDifficultyDivisor(difficulty);
    }
    return Math.max(1, duration);
  }

  /** Native calc1 payload used by hit-event curses such as Iron Maiden and Life Tap. */
  public static int reactiveCursePercent(Skills.Entry skill, int skillLevel) {
    return Math.max(0, SkillFormula.evaluate(
        skill == null ? null : skill.calc1, skill, Math.max(1, skillLevel)));
  }

  /** D2Game reduces reflected thorns damage to one eighth against players and hirelings. */
  public static int ironMaidenPercent(
      Skills.Entry skill, int skillLevel, boolean attackerPlayerOrHireling) {
    int percent = reactiveCursePercent(skill, skillLevel);
    return attackerPlayerOrHireling ? (percent + 4) / 8 : percent;
  }

  /** Applies one native SrvDo030 curse layer from the row's aura stat columns. */
  public static UnitState applyCurse(StateList states, States stateTable, Skills.Entry skill,
      int skillLevel, int sourceEntityId, DifficultyLevels.Entry difficulty,
      Attributes targetAttributes, boolean targetPlayerOrHireling) {
    if (states == null || skill == null) return null;
    int stateId = resolveCurseStateId(skill.auratargetstate, skill.skill);
    if (stateId == StateId.NONE) return null;
    int duration = curseDuration(skill, skillLevel, difficulty);
    int statId = -1;
    int statValue = 0;
    if (skill.aurastat != null && skill.aurastat.length > 0) {
      statId = resolveCurseStatId(skill.aurastat[0]);
      if (statId >= 0 && skill.aurastatcalc != null && skill.aurastatcalc.length > 0) {
        statValue = normalizeCurseStatValue(statId,
            SkillFormula.evaluate(skill.aurastatcalc[0], skill, skillLevel),
            targetAttributes, targetPlayerOrHireling);
      }
    }
    // D2MOO sub_6FD0B450 rejects a target when AuraStat1 exists but its
    // evaluated value is zero. Curses without AuraStat1 remain state-only.
    if (statId >= 0 && statValue == 0) return null;
    int strength = Math.max(1, Math.abs(statValue));
    UnitState state = states.applyCurseState(stateTable, stateId, duration,
        Math.max(1, skillLevel), sourceEntityId, skill.Id, strength, statId, statValue,
        NativeStatResolver.Operation.ADD);
    if (state == null) return null;
    int count = skill.aurastat == null ? 0 : skill.aurastat.length;
    for (int i = 1; i < count; i++) {
      int extraStat = resolveCurseStatId(skill.aurastat[i]);
      if (extraStat < 0 || skill.aurastatcalc == null || i >= skill.aurastatcalc.length) continue;
      int extraValue = normalizeCurseStatValue(extraStat,
          SkillFormula.evaluate(skill.aurastatcalc[i], skill, skillLevel),
          targetAttributes, targetPlayerOrHireling);
      // setStatContribution removes a zero entry. This also prevents a
      // refreshed lower-level layer retaining a stale non-zero old value.
      state.setStatContribution(extraStat, 0, NativeStatResolver.Operation.ADD, extraValue);
    }
    state.needsSync = true;
    return state;
  }

  /** Native curse rule: resistance reductions affect immune monsters at one fifth strength. */
  public static int normalizeCurseStatValue(int statId, int value,
      Attributes targetAttributes, boolean targetPlayerOrHireling) {
    if (value > 0 || targetPlayerOrHireling || !isResistanceStat(statId)) return value;
    StatRef resistance = targetAttributes != null
        ? targetAttributes.get((short) statId, StatRef.obtain()) : null;
    return resistance != null && resistance.asInt() >= 100 ? value / 5 : value;
  }

  private static boolean isResistanceStat(int statId) {
    return statId == Stat.damageresist || statId == Stat.magicresist
        || statId == Stat.fireresist || statId == Stat.lightresist
        || statId == Stat.coldresist || statId == Stat.poisonresist;
  }
}
