package com.riiablo.engine.server.skill;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.attributes.Attributes;
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
import java.util.function.ToIntFunction;

/**
 * 野蛮人技能实现 - 基于 D2MOD SkillBar.cpp 移植
 * 
 * <p>包含战斗技能、战吼、战斗专精三系技能的实现。
 * 
 * <p>参考：D2MOD/source/D2Game/src/SKILLS/SkillBar.cpp
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
    return 90 + (Math.max(1, skillLevel) - 1) * 5;
  }

  /** D2MOO {@code damage.dwEnDmgPct = calc1}, including hard-point synergies. */
  public static int calculateFrenzyDamageBonus(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    int value = SkillFormula.evaluate(skill != null ? skill.calc1 : null,
        skill, Math.max(1, skillLevel), baseSkillLevel);
    return value != 0 ? value : calculateFrenzyDamageBonus(skillLevel);
  }

  /** Native Frenzy caps {@code STAT_SKILL_FRENZY} at the current skill level. */
  public static int getFrenzyMaxStacks(int skillLevel) {
    return Math.max(1, skillLevel);
  }

  /** Retained for callers compiled against the earlier helper. */
  @Deprecated
  public static int getFrenzyMaxStacks() {
    return getFrenzyMaxStacks(1);
  }

  public static int getFrenzyDuration(Skills.Entry skill, int skillLevel) {
    int duration = SkillFormula.evaluate(
        skill != null ? skill.auralencalc : null, skill, Math.max(1, skillLevel));
    return Math.max(1, duration);
  }

  /** Evaluates one AuraStat formula with the native Frenzy stack as formula level. */
  public static int getFrenzyAuraStat(
      Skills.Entry skill, int stacks, String statName) {
    if (skill == null || skill.aurastat == null || skill.aurastatcalc == null
        || statName == null) return 0;
    int count = Math.min(skill.aurastat.length, skill.aurastatcalc.length);
    for (int i = 0; i < count; i++) {
      if (statName.equalsIgnoreCase(skill.aurastat[i])) {
        return SkillFormula.evaluate(skill.aurastatcalc[i], skill, Math.max(1, stacks));
      }
    }
    return 0;
  }

  public static int getFrenzyMovementPercent(Skills.Entry skill, int stacks) {
    return Math.max(0, getFrenzyAuraStat(skill, stacks, "velocitypercent"));
  }

  public static int getFrenzyAnimationRatePercent(Skills.Entry skill, int stacks) {
    int attackRate = getFrenzyAuraStat(skill, stacks, "attackrate");
    int otherRate = getFrenzyAuraStat(skill, stacks, "other_animrate");
    // sub_6FCFE0E0 mirrors attackrate into other_animrate automatically.
    return Math.max(0, Math.max(attackRate, otherRate));
  }

  /**
   * D2MOO SKILLS_ApplyFrenzyStats. The caller invokes this only when the
   * used skill's Param1 says that the previous strike connected.
   */
  public static UnitState applyFrenzyState(
      StateList states, Skills.Entry skill, int skillLevel, int sourceEntityId) {
    if (states == null || skill == null || !isFrenzy(skill)) return null;
    int stateId = "frenzy".equalsIgnoreCase(skill.aurastate)
        ? StateId.FRENZY : StateId.MONFRENZY;
    UnitState existing = states.getState(stateId);
    int oldStacks = existing != null ? Math.max(0, existing.runtimeValue) : 0;
    int stacks = Math.min(getFrenzyMaxStacks(skillLevel), oldStacks + 1);
    UnitState state = states.addState(
        stateId, getFrenzyDuration(skill, skillLevel), Math.max(1, skillLevel), sourceEntityId);
    if (state == null) return null;
    state.skillId = skill.Id;
    state.runtimeValue = stacks;
    state.velocityModifier = getFrenzyMovementPercent(skill, stacks);
    state.animationRateModifier = getFrenzyAnimationRatePercent(skill, stacks);
    state.needsSync = true;
    return state;
  }

  public static boolean isFrenzy(Skills.Entry skill) {
    return skill != null && skill.skill != null
        && skill.skill.toLowerCase(Locale.ROOT).contains("frenzy");
  }

  /** Native player/monster interpretation of SKILLS_GetToHitFactor. */
  public static int getFrenzyAttackRating(
      Skills.Entry skill, int skillLevel, Attributes attacker, boolean player) {
    int base = statInt(attacker, Stat.tohit);
    int level = Math.max(1, skillLevel);
    int factor = skill == null ? 0 : skill.ToHit + (level - 1) * skill.LevToHit;
    if (player) return Math.max(1, base * Math.max(0, 100 + factor) / 100);
    return Math.max(1, base + factor);
  }

  /** Fully scaled physical range for one hand of SKILLS_RollFrenzyDamage. */
  public static int[] calculateFrenzyWeaponDamage(
      Skills.Entry skill, int skillLevel, Attributes attacker, Item weapon,
      ToIntFunction<String> baseSkillLevel) {
    if (weapon == null || !(weapon.base instanceof Weapons.Entry)) return null;
    Weapons.Entry base = (Weapons.Entry) weapon.base;
    int min = itemStatInt(weapon, Stat.mindamage, base.mindam);
    int max = itemStatInt(weapon, Stat.maxdamage, Math.max(min, base.maxdam));
    int percent = calculateFrenzyDamageBonus(skill, skillLevel, baseSkillLevel)
        + base.StrBonus * statInt(attacker, Stat.strength) / 100
        + base.DexBonus * statInt(attacker, Stat.dexterity) / 100
        + statInt(attacker, Stat.damagepercent)
        + statInt(attacker, Stat.item_maxdamage_percent);
    int sourceDamage = skill == null || skill.SrcDam == 0 ? 128 : skill.SrcDam;
    return new int[] {
        scale(scale(min, percent), sourceDamage, 128),
        scale(scale(Math.max(min, max), percent), sourceDamage, 128)
    };
  }

  public static int getFrenzyMagicConversion(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    if (skill == null || skill.EType == null || !"mag".equalsIgnoreCase(skill.EType)) return 0;
    return Math.max(0, Math.min(100, SkillFormula.evaluate(
        skill.calc4, skill, Math.max(1, skillLevel), baseSkillLevel)));
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

  private static int scale(int value, int percent, int divisor) {
    if (value <= 0 || percent <= 0 || divisor <= 0) return 0;
    long result = (long) value * percent / divisor;
    return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
  }

  private static int scale(int value, int enhancedPercent) {
    long result = (long) value * Math.max(0, 100 + enhancedPercent) / 100L;
    return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
  }

  /**
   * 旋风斩 - 旋转攻击周围所有敌人
   * 
   * @param skillLevel 技能等级
   * @return 伤害加成百分比
   */
  public static int calculateWhirlwindDamageBonus(int skillLevel) {
    return -50 + (Math.max(1, skillLevel) - 1) * 8;
  }

  /** D2MOO SrvDo076 {@code damage.dwEnDmgPct = calc1}. */
  public static int calculateWhirlwindDamageBonus(
      Skills.Entry skill, int skillLevel, ToIntFunction<String> baseSkillLevel) {
    if (skill == null || skill.calc1 == null || skill.calc1.trim().isEmpty()) {
      return calculateWhirlwindDamageBonus(skillLevel);
    }
    return SkillFormula.evaluate(
        skill.calc1, skill, Math.max(1, skillLevel), baseSkillLevel);
  }

  /** Fully scaled physical range for one Whirlwind hand. */
  public static int[] calculateWhirlwindDamage(
      Skills.Entry skill, int skillLevel, Attributes attacker, Item weapon,
      ToIntFunction<String> baseSkillLevel) {
    int min;
    int max;
    int attributePercent = 0;
    if (weapon != null && weapon.base instanceof Weapons.Entry) {
      Weapons.Entry base = (Weapons.Entry) weapon.base;
      min = itemStatInt(weapon, Stat.mindamage, base.mindam);
      max = itemStatInt(weapon, Stat.maxdamage, Math.max(min, base.maxdam));
      attributePercent = base.StrBonus * statInt(attacker, Stat.strength) / 100
          + base.DexBonus * statInt(attacker, Stat.dexterity) / 100;
    } else {
      min = Math.max(0, statInt(attacker, Stat.mindamage));
      max = Math.max(min, statInt(attacker, Stat.maxdamage));
    }
    int percent = calculateWhirlwindDamageBonus(
        skill, skillLevel, baseSkillLevel)
        + attributePercent
        + statInt(attacker, Stat.damagepercent)
        + statInt(attacker, Stat.item_maxdamage_percent);
    int sourceDamage = skill == null || skill.SrcDam == 0 ? 128 : skill.SrcDam;
    return new int[] {
        scale(scale(min, percent), sourceDamage, 128),
        scale(scale(max, percent), sourceDamage, 128)
    };
  }

  public static int getWhirlwindAttackRating(
      Skills.Entry skill, int skillLevel, Attributes attacker, boolean player) {
    return getFrenzyAttackRating(skill, skillLevel, attacker, player);
  }

  /** Expansion SrvDo076 weapon-speed breakpoints, expressed in game frames. */
  public static int getWhirlwindAttackInterval(int nativeWeaponAttackSpeed) {
    if (nativeWeaponAttackSpeed < 12) return 4;
    if (nativeWeaponAttackSpeed < 15) return 6;
    if (nativeWeaponAttackSpeed < 18) return 8;
    if (nativeWeaponAttackSpeed < 20) return 10;
    if (nativeWeaponAttackSpeed < 23) return 12;
    if (nativeWeaponAttackSpeed < 26) return 14;
    return 16;
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
