package com.riiablo.engine.server.combat;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;

/**
 * 战斗系统 - 基于 D2MOD SUnitDmg.cpp 移植
 * 
 * <p>实现完整的暗黑破坏神 II 战斗机制：
 * <ul>
 *   <li>攻击命中率（AR）计算</li>
 *   <li>防御等级（Defense）计算</li>
 *   <li>命中判定公式</li>
 *   <li>伤害类型和减免</li>
 *   <li>格挡和闪避</li>
 *   <li>暴击（Deadly Strike / Critical Strike）</li>
 *   <li>吸血和法力偷取</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/UNIT/SUnitDmg.cpp
 * 
 * @author riiablo team
 */
public class CombatSystem {
  private static final Logger log = LogManager.getLogger(CombatSystem.class);


  //==========================================================================
  // 常量 - 伤害类型
  //==========================================================================

  /** 物理伤害 */
  public static final int DAMAGE_PHYSICAL = NativeCombatConstants.DAMAGE_PHYSICAL;
  
  /** 火焰伤害 */
  public static final int DAMAGE_FIRE = NativeCombatConstants.DAMAGE_FIRE;
  
  /** 闪电伤害 */
  public static final int DAMAGE_LIGHTNING = NativeCombatConstants.DAMAGE_LIGHTNING;
  
  /** 冰冷伤害 */
  public static final int DAMAGE_COLD = NativeCombatConstants.DAMAGE_COLD;
  
  /** 毒素伤害 */
  public static final int DAMAGE_POISON = NativeCombatConstants.DAMAGE_POISON;
  
  /** 魔法伤害 */
  public static final int DAMAGE_MAGIC = NativeCombatConstants.DAMAGE_MAGIC;

  /** 伤害类型数量 */
  public static final int DAMAGE_TYPE_COUNT = NativeCombatConstants.DAMAGE_TYPE_COUNT;

  //==========================================================================
  // 常量 - 命中公式参数
  //==========================================================================

  /** 基础命中率（5%） */
  public static final int BASE_TO_HIT_CHANCE = NativeCombatConstants.BASE_TO_HIT_CHANCE;

  /** 最低命中率（5%） */
  public static final int MIN_TO_HIT_CHANCE = NativeCombatConstants.MIN_TO_HIT_CHANCE;

  /** 最高命中率（95%） */
  public static final int MAX_TO_HIT_CHANCE = NativeCombatConstants.MAX_TO_HIT_CHANCE;

  /** Native elemental resistance bounds before damage is resolved. */
  public static final int MIN_RESISTANCE = NativeCombatConstants.MIN_RESISTANCE;
  public static final int ABSOLUTE_MAX_RESISTANCE = NativeCombatConstants.ABSOLUTE_MAX_RESISTANCE;

  /** Native chance-to-hit numerator factor (applies to every unit type). */
  public static final int PVP_HIT_FACTOR = NativeCombatConstants.PVP_HIT_FACTOR;

  /** Diablo II player-vs-player damage scalar (approximately one sixth). */
  public static final int PVP_DAMAGE_PERCENT = NativeCombatConstants.PVP_DAMAGE_PERCENT;

  /** 等级差异每级影响（用于命中计算） */
  public static final int LEVEL_DIFF_MODIFIER = 2;

  //==========================================================================
  // 常量 - 格挡参数
  //==========================================================================

  /** 基础格挡率 */
  public static final int BASE_BLOCK_CHANCE = 0;

  /** 最低格挡率 */
  public static final int MIN_BLOCK_CHANCE = NativeCombatConstants.MIN_BLOCK_CHANCE;

  /** 最高格挡率 */
  public static final int MAX_BLOCK_CHANCE = NativeCombatConstants.MAX_BLOCK_CHANCE;

  /** 格挡敏捷因子 */
  public static final int BLOCK_DEXTERITY_FACTOR = NativeCombatConstants.BLOCK_DEXTERITY_FACTOR;

  //==========================================================================
  // 常量 - 暴击参数
  //==========================================================================

  /** 最高暴击率 */
  public static final int MAX_CRIT_CHANCE = 100;

  //==========================================================================
  // 内部类 - 战斗上下文
  //==========================================================================

  /**
   * 攻击者数据
   */
  public static class AttackerData extends NativeAttackData {}

  /**
   * 防御者数据
   */
  public static class DefenderData extends NativeDefenseData {
    /** Runtime attribute list stays in the host, never in the domain snapshot. */
    Attributes combatAttributes;
  }

  /**
   * 战斗结果
   */
  /** Compatibility result type; the packet fields live in the native boundary. */
  public static class CombatResult extends NativeDamagePacket {}

  //==========================================================================
  // 构造函数
  //==========================================================================

  private final NativeCombatRolls rolls;
  private final NativeAttackResolver attackResolver;

  /** Compatibility RNG for old tools that do not bind a server world. */
  public CombatSystem() {
    java.util.function.IntUnaryOperator random = bound -> MathUtils.random(bound - 1);
    this.rolls = new NativeCombatRolls(random);
    this.attackResolver = new NativeAttackResolver(rolls);
  }

  public CombatSystem(com.riiablo.engine.server.NativeRng rng) {
    java.util.function.IntUnaryOperator random = java.util.Objects.requireNonNull(rng, "rng")::nextInt;
    this.rolls = new NativeCombatRolls(random);
    this.attackResolver = new NativeAttackResolver(rolls);
  }



  /**
   * Builds the native-style combat context from the attributes that belong to
   * the two entities and resolves one hit. Keeping this adapter here makes the
   * Actioneer and missile paths use exactly the same hit/damage pipeline.
   *
   * @param attacker attributes of the attacking entity
   * @param defender attributes of the target entity
   * @param attackerPlayer whether the attacker is a player
   * @param defenderPlayer whether the target is a player
   * @param missile whether this is a missile/ranged hit
   */
  public CombatResult calculateAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        NativeCombatContext.normal());
  }

  public CombatResult calculateAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile, NativeCombatContext context) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile, 0, 0, 0,
        context);
  }

  /**
   * Immutable hit-test output shared by melee and missile callers.  Keeping
   * the effective AR/defense, chance and roll together prevents diagnostics
   * and damage paths from recomputing different snapshots of the same attack.
   */
  public static final class HitResult extends NativeAttackResolver.Hit {
    private HitResult(NativeAttackResolver.Hit hit) {
      super(hit.attackRating, hit.targetDefense, hit.chance, hit.roll, hit.hit);
    }
  }

  /** Signed 8.8 result used by Static Field's fractional native damage path. */
  public static class StaticFieldDamageResult extends NativeFixedElementalMath.Result {}

  /** Signed 8.8 result for persistent elemental ground missiles. */
  public static class FixedElementalDamageResult extends NativeFixedElementalMath.Result {}

  /**
   * Resolves an attack with an optional native monster attack profile.
   * MonStats stores separate A1/A2 damage and to-hit values while the ECS
   * attribute list contains the default A1 profile.  A2 attacks therefore
   * need a small per-action override instead of mutating shared attributes.
   */
  public CombatResult calculateAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride,
        NativeCombatContext.normal());
  }

  public CombatResult calculateAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, NativeCombatContext context) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, false, context);
  }

  /** Variant of the native attack-profile overload with runtime states. */
  public CombatResult calculateAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, StateList attackerStates, StateList defenderStates) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, attackerStates,
        defenderStates, NativeCombatContext.normal());
  }

  public CombatResult calculateAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, StateList attackerStates, StateList defenderStates, NativeCombatContext context) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, false, null, null, 0, 0,
        attackerStates, defenderStates, context);
  }

  /** Attack-profile overload carrying movement context for Evade. */
  public CombatResult calculateAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, StateList attackerStates, StateList defenderStates,
      boolean defenderMoving) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, attackerStates,
        defenderStates, defenderMoving, NativeCombatContext.normal());
  }

  public CombatResult calculateAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, NativeCombatContext context) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, false, null, null, 0, 0,
        attackerStates, defenderStates, defenderMoving, context);
  }

  public CombatResult calculateAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, attackerStates,
        defenderStates, defenderMoving, mastery, NativeCombatContext.normal());
  }

  public CombatResult calculateAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery, NativeCombatContext context) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, false, null, null, 0, 0,
        attackerStates, defenderStates, defenderMoving, mastery, context);
  }

  public CombatResult calculateAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        NativeCombatContext.normal());
  }

  public CombatResult calculateAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit, NativeCombatContext context) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit, null, null, 0,
        0, context);
  }

  /** Resolves an attack with a per-animation native monster elemental profile. */
  public CombatResult calculateAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        NativeCombatContext.normal());
  }

  public CombatResult calculateAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride, NativeCombatContext context) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride, null, null,
        context);
  }

  /**
   * Resolves an attack while applying authoritative runtime state modifiers.
   * StateList is optional to preserve compatibility with callers that only
   * have the aggregated Attributes component.
   */
  public CombatResult calculateAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, NativeCombatContext.normal());
  }

  public CombatResult calculateAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates, NativeCombatContext context) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, false, context);
  }

  /** Full native combat context including movement for Amazon Evade. */
  public CombatResult calculateAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, defenderMoving, NativeCombatContext.normal());
  }

  public CombatResult calculateAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, NativeCombatContext context) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, defenderMoving, null, context);
  }

  /** Full context plus the native item-layered mastery selected for this attack. */
  public CombatResult calculateAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery) {
    return calculateAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, defenderMoving, mastery, NativeCombatContext.normal());
  }

  public CombatResult calculateAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery, NativeCombatContext context) {
    return calculateAttackInternal(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, defenderMoving, false, 0, DAMAGE_PHYSICAL, mastery, context, 0,
        false, false, true, 0, false);
  }

  /** Regular attack with the native monster-type context needed by Sanctuary. */
  public CombatResult calculateAttackAgainstMonsterType(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery,
      boolean defenderDemon, boolean defenderUndead) {
    return calculateAttackInternal(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, false,
        null, null, 0, 0, attackerStates, defenderStates, defenderMoving, false,
        0, DAMAGE_PHYSICAL, mastery, 0, 0, defenderDemon, defenderUndead, true);
  }

  /**
   * Regular attack with explicit native Ignore Target Defense eligibility.
   * The flag is supplied by the authoritative entity layer because the
   * aggregated Attributes object does not retain monster quality or hireling
   * identity.
   */
  public CombatResult calculateAttackAgainstMonsterType(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery,
      boolean defenderDemon, boolean defenderUndead,
      boolean defenderIgnoreTargetDefenseAllowed) {
    return calculateAttackInternal(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, false,
        null, null, 0, 0, attackerStates, defenderStates, defenderMoving, false,
        0, DAMAGE_PHYSICAL, mastery, 0, 0, defenderDemon, defenderUndead,
        defenderIgnoreTargetDefenseAllowed);
  }

  /** Full context with native Nightmare/Hell resistance penalty selection. */
  public CombatResult calculateAttackAtDifficulty(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery,
      int difficulty) {
    return calculateAttackInternal(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, defenderMoving, false, 0, DAMAGE_PHYSICAL, mastery,
        difficulty, 0, false, false, true);
  }

  /**
   * Native missile damage-function variant that adds a target-dependent
   * percentage to the rolled magic packet before resistance and absorption.
   * Blessed Hammer uses this for its independent undead/demon bonuses.
   */
  public CombatResult calculateAttackAtDifficulty(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery,
      int difficulty, int magicTargetBonusPercent) {
    return calculateAttackInternal(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, defenderMoving, false, 0, DAMAGE_PHYSICAL, mastery,
        difficulty, Math.max(0, magicTargetBonusPercent), false, false, true);
  }

  /** Full difficulty context with explicit Ignore Target Defense eligibility. */
  public CombatResult calculateAttackAtDifficulty(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery,
      int difficulty, int magicTargetBonusPercent,
      boolean defenderDemon, boolean defenderUndead,
      boolean defenderIgnoreTargetDefenseAllowed) {
    return calculateAttackInternal(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, defenderMoving, false, 0, DAMAGE_PHYSICAL, mastery,
        difficulty, Math.max(0, magicTargetBonusPercent), defenderDemon, defenderUndead,
        defenderIgnoreTargetDefenseAllowed);
  }

  /** Missile/ranged attack with target-class metadata for Sanctuary bonuses. */
  public CombatResult calculateAttackAtDifficulty(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery,
      int difficulty, int magicTargetBonusPercent,
      boolean defenderDemon, boolean defenderUndead) {
    return calculateAttackInternal(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, defenderMoving, false, 0, DAMAGE_PHYSICAL, mastery,
        difficulty, Math.max(0, magicTargetBonusPercent), defenderDemon, defenderUndead, true);
  }

  /** Full missile context with an explicit elemental precision opt-in. */
  public CombatResult calculateAttackAtDifficulty(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery,
      int difficulty, int magicTargetBonusPercent,
      boolean defenderDemon, boolean defenderUndead,
      boolean defenderIgnoreTargetDefenseAllowed, boolean fixedPointElements) {
    return calculateAttackInternal(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, defenderMoving, false, 0, DAMAGE_PHYSICAL, mastery,
        difficulty, Math.max(0, magicTargetBonusPercent), defenderDemon, defenderUndead,
        defenderIgnoreTargetDefenseAllowed, 0, fixedPointElements);
  }

  /** Table-backed attack entry; legacy integer overloads keep stock-rule fallback. */
  public CombatResult calculateAttackAtDifficulty(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery,
      NativeCombatContext context, int magicTargetBonusPercent,
      boolean defenderDemon, boolean defenderUndead,
      boolean defenderIgnoreTargetDefenseAllowed, boolean fixedPointElements) {
    return calculateAttackInternal(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, defenderMoving, false, 0, DAMAGE_PHYSICAL, mastery, context,
        Math.max(0, magicTargetBonusPercent), defenderDemon, defenderUndead,
        defenderIgnoreTargetDefenseAllowed, 0, fixedPointElements);
  }

  /** Resolves a fixed elemental packet for area effects that have no missile
   *  entity. This keeps hand-written skill explosions on the same native
   *  resistance/absorb/PvP chain as regular attacks.
   */
  public CombatResult calculateFixedElementalDamage(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int damageType, int rawDamage, int piercePercent,
      StateList defenderStates, int difficulty) {
    return calculateFixedElementalDamage(defender, defenderPlayer, attackerPlayer, damageType, rawDamage,
        piercePercent, defenderStates, NativeCombatContext.ofDifficulty(difficulty), false);
  }

  public CombatResult calculateFixedElementalDamage(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int damageType, int rawDamage, int piercePercent,
      StateList defenderStates, NativeCombatContext context) {
    return calculateFixedElementalDamage(defender, defenderPlayer, attackerPlayer, damageType, rawDamage,
        piercePercent, defenderStates, context, false);
  }

  /** Native direct packet: MDR before resistance, no monster player-cap, 40% absorb cap. */
  public CombatResult calculateNativeFixedElementalDamage(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int damageType, int rawDamage, int piercePercent,
      StateList defenderStates, int difficulty) {
    return calculateFixedElementalDamage(defender, defenderPlayer, attackerPlayer, damageType, rawDamage,
        piercePercent, defenderStates, NativeCombatContext.ofDifficulty(difficulty), true);
  }

  public CombatResult calculateNativeFixedElementalDamage(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int damageType, int rawDamage, int piercePercent,
      StateList defenderStates, NativeCombatContext context) {
    return calculateFixedElementalDamage(defender, defenderPlayer, attackerPlayer, damageType, rawDamage,
        piercePercent, defenderStates, context, true);
  }

  private CombatResult calculateFixedElementalDamage(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int damageType, int rawDamage, int piercePercent,
      StateList defenderStates, NativeCombatContext context, boolean nativePacket) {
    CombatResult result = new CombatResult();
    result.reset();
    if (defender == null || damageType <= DAMAGE_PHYSICAL
        || damageType >= DAMAGE_TYPE_COUNT || rawDamage <= 0) return result;

    DefenderData d = new DefenderData();
    d.isPlayer = defenderPlayer;
    d.isMonster = !defenderPlayer;
    d.resistances[DAMAGE_FIRE] = statInt(defender, Stat.fireresist, 0);
    d.resistances[DAMAGE_LIGHTNING] = statInt(defender, Stat.lightresist, 0);
    d.resistances[DAMAGE_COLD] = statInt(defender, Stat.coldresist, 0);
    d.resistances[DAMAGE_POISON] = statInt(defender, Stat.poisonresist, 0);
    d.resistances[DAMAGE_MAGIC] = statInt(defender, Stat.magicresist, 0);
    int penalty = defenderPlayer ? NativeDifficultyRules.remainingResistancePenalty(defender, context) : 0;
    // Native difficulty penalties exclude physical and magic resistance.
    for (int i = DAMAGE_FIRE; i < DAMAGE_TYPE_COUNT; i++) {
      if (i != DAMAGE_MAGIC) d.resistances[i] += penalty;
    }
    d.maxResistances[DAMAGE_FIRE] = 75 + statInt(defender, Stat.maxfireresist, 0);
    d.maxResistances[DAMAGE_LIGHTNING] = 75 + statInt(defender, Stat.maxlightresist, 0);
    d.maxResistances[DAMAGE_COLD] = 75 + statInt(defender, Stat.maxcoldresist, 0);
    d.maxResistances[DAMAGE_POISON] = 75 + statInt(defender, Stat.maxpoisonresist, 0);
    d.maxResistances[DAMAGE_MAGIC] = 75 + statInt(defender, Stat.maxmagicresist, 0);
    d.absorbPercent[DAMAGE_FIRE] = statInt(defender, Stat.item_absorbfire_percent, 0);
    d.absorbPercent[DAMAGE_LIGHTNING] = statInt(defender, Stat.item_absorblight_percent, 0);
    d.absorbPercent[DAMAGE_COLD] = statInt(defender, Stat.item_absorbcold_percent, 0);
    d.absorbPercent[DAMAGE_MAGIC] = statInt(defender, Stat.item_absorbmagic_percent, 0);
    d.absorbFlat[DAMAGE_FIRE] = statInt(defender, Stat.item_absorbfire, 0);
    d.absorbFlat[DAMAGE_LIGHTNING] = statInt(defender, Stat.item_absorblight, 0);
    d.absorbFlat[DAMAGE_COLD] = statInt(defender, Stat.item_absorbcold, 0);
    d.absorbFlat[DAMAGE_MAGIC] = statInt(defender, Stat.item_absorbmagic, 0);
    d.magicDamageReduced = Math.max(0,
        statInt(defender, Stat.magic_damage_reduction, 0));
    if (defenderStates != null) {
      d.resistances[DAMAGE_FIRE] += defenderStates.getTotalResistModifier(0);
      d.resistances[DAMAGE_COLD] += defenderStates.getTotalResistModifier(1);
      d.resistances[DAMAGE_LIGHTNING] += defenderStates.getTotalResistModifier(2);
      d.resistances[DAMAGE_POISON] += defenderStates.getTotalResistModifier(3);
      d.resistances[DAMAGE_MAGIC] += defenderStates.getTotalResistModifier(4);
      d.maxResistances[DAMAGE_FIRE] +=
          defenderStates.getTotalStatContribution(Stat.maxfireresist);
      d.maxResistances[DAMAGE_COLD] +=
          defenderStates.getTotalStatContribution(Stat.maxcoldresist);
      d.maxResistances[DAMAGE_LIGHTNING] +=
          defenderStates.getTotalStatContribution(Stat.maxlightresist);
      d.maxResistances[DAMAGE_POISON] +=
          defenderStates.getTotalStatContribution(Stat.maxpoisonresist);
      d.maxResistances[DAMAGE_MAGIC] +=
          defenderStates.getTotalStatContribution(Stat.maxmagicresist);
    }
    // Native SUNITDMG only preserves >=100 elemental resistance as immunity
    // for monsters. Player resistance is still clamped to the raised
    // 75..95 maximum-resistance cap below.
    d.immuneElemental[damageType] = d.isMonster && d.resistances[damageType] >= 100;
    NativeDirectDamageMath.elementalDamage(result, damageType, rawDamage, piercePercent,
        defenderPlayer, attackerPlayer, d.resistances[damageType], d.maxResistances[damageType],
        d.magicDamageReduced, d.absorbPercent[damageType], d.absorbFlat[damageType], nativePacket);
    return result;
  }

  /** Resolves a cold/freeze length through the native cold resistance row. */
  public int resolveColdLengthBeforeState(Attributes defender, boolean defenderPlayer,
      int baseDuration, int piercePercent, StateList defenderStates, int difficulty) {
    return resolveColdLengthBeforeState(defender, defenderPlayer, baseDuration,
        piercePercent, defenderStates, NativeCombatContext.ofDifficulty(difficulty));
  }

  public int resolveColdLengthBeforeState(Attributes defender, boolean defenderPlayer,
      int baseDuration, int piercePercent, StateList defenderStates, NativeCombatContext context) {
    if (defender == null || baseDuration <= 0) return 0;
    if (statInt(defender, Stat.item_cannotbefrozen, 0) != 0) return 0;
    // SUNITDMG_CalculateTotalDamage halves the packet before its Flen row.
    if (statInt(defender, Stat.item_halffreezeduration, 0) != 0) baseDuration /= 2;
    int resistance = statInt(defender, Stat.coldresist, 0);
    int maximum = 75 + statInt(defender, Stat.maxcoldresist, 0);
    if (defenderPlayer) resistance += NativeDifficultyRules.remainingResistancePenalty(defender, context);
    if (defenderStates != null) {
      resistance += defenderStates.getTotalResistModifier(1);
      maximum += defenderStates.getTotalStatContribution(Stat.maxcoldresist);
    }
    // SUnitDmg.cpp's Flen row has DAMAGE_REDUCTION_NONE: MDR and absorb
    // affect damage, never length. StateUpdater owns ColdDiv/FreezeDiv and
    // player/boss downgrade, so do not pre-divide monster duration here.
    return NativeStatusDurationMath.coldLengthBeforeState(
        baseDuration, defenderPlayer, resistance, maximum, piercePercent);
  }

  /** Legacy damage callers also request the monster cold divisor here. */
  public int resolveColdDuration(Attributes defender, boolean defenderPlayer,
      int baseDuration, int piercePercent, StateList defenderStates, int difficulty) {
    if (defender == null || baseDuration <= 0) return 0;
    DefenderData d = new DefenderData();
    d.isPlayer = defenderPlayer;
    d.isMonster = !defenderPlayer;
    d.resistances[DAMAGE_COLD] = statInt(defender, Stat.coldresist, 0);
    if (defenderPlayer) {
      d.resistances[DAMAGE_COLD] += NativeDifficultyRules.remainingResistancePenalty(defender, difficulty);
    }
    d.maxResistances[DAMAGE_COLD] = 75 + statInt(defender, Stat.maxcoldresist, 0);
    d.cannotBeFrozen = statInt(defender, Stat.item_cannotbefrozen, 0) != 0;
    d.halfFreezeDuration = statInt(defender, Stat.item_halffreezeduration, 0) != 0;
    if (defenderStates != null) {
      d.resistances[DAMAGE_COLD] += defenderStates.getTotalResistModifier(1);
      d.maxResistances[DAMAGE_COLD] +=
          defenderStates.getTotalStatContribution(Stat.maxcoldresist);
    }
    int duration = NativeAttackResolver.applyElementalResistance(
        baseDuration, d, DAMAGE_COLD, Math.max(0, piercePercent));
    if (d.cannotBeFrozen) return 0;
    if (d.halfFreezeDuration) duration /= 2;
    return scaleMonsterColdDuration(duration, !defenderPlayer, difficulty);
  }

  /** Regular attack carrying a per-swing monster unique elemental packet. */
  public CombatResult calculateAttackAgainstMonsterType(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery,
      boolean defenderDemon, boolean defenderUndead,
      boolean defenderIgnoreTargetDefenseAllowed, int difficulty) {
    return calculateAttackInternal(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, false,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, defenderMoving, false,
        0, DAMAGE_PHYSICAL, mastery, difficulty, 0, defenderDemon, defenderUndead,
        defenderIgnoreTargetDefenseAllowed);
  }

  /**
   * Applies Diablo II's monster cold-length penalty after resistance and
   * Cannot Be Frozen/Half Freeze checks. Normal, Nightmare and Hell monsters
   * use divisors 1, 2 and 4 respectively; player targets are never scaled.
   */
  static int scaleMonsterColdDuration(int duration, boolean monsterTarget, int difficulty) {
    if (duration <= 0 || !monsterTarget) return Math.max(0, duration);
    return NativeDifficultyRules.scaleMonsterColdDuration(duration, difficulty);
  }

  /**
   * Resolves one native game-frame hit from Blaze/Fire Wall without dropping
   * the fractional 8.8 damage. {@code damageRate} scales flat magical damage
   * reduction by {@code rate / 1024}, matching D2Game's
   * {@code STAT_DAMAGE_FRAMERATE} handling for rapid area missiles.
   */
  public FixedElementalDamageResult calculateFixedElementalRateDamage(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int damageType, int rawDamageFixed, int piercePercent, int damageRate,
      StateList defenderStates, int difficulty) {
    return calculateFixedElementalRateDamage(defender, defenderPlayer, attackerPlayer, damageType,
        rawDamageFixed, piercePercent, damageRate, defenderStates,
        NativeCombatContext.ofDifficulty(difficulty));
  }

  public FixedElementalDamageResult calculateFixedElementalRateDamage(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int damageType, int rawDamageFixed, int piercePercent, int damageRate,
      StateList defenderStates, NativeCombatContext context) {
    FixedElementalDamageResult result = new FixedElementalDamageResult();
    if (defender == null || damageType <= DAMAGE_PHYSICAL
        || damageType >= DAMAGE_TYPE_COUNT || rawDamageFixed <= 0) return result;

    short[] resistanceStats = {0, Stat.fireresist, Stat.lightresist,
        Stat.coldresist, Stat.poisonresist, Stat.magicresist};
    short[] maximumStats = {0, Stat.maxfireresist, Stat.maxlightresist,
        Stat.maxcoldresist, Stat.maxpoisonresist, Stat.maxmagicresist};
    short[] absorbPercentStats = {0, Stat.item_absorbfire_percent,
        Stat.item_absorblight_percent, Stat.item_absorbcold_percent, 0,
        Stat.item_absorbmagic_percent};
    short[] absorbFlatStats = {0, Stat.item_absorbfire, Stat.item_absorblight,
        Stat.item_absorbcold, 0, Stat.item_absorbmagic};
    int[] stateResistanceTypes = {-1, 0, 2, 1, 3, 4};

    int resistance = statInt(defender, resistanceStats[damageType], 0);
    if (defenderStates != null) {
      resistance += defenderStates.getTotalResistModifier(
          stateResistanceTypes[damageType]);
    }
    if (defenderPlayer && damageType != DAMAGE_MAGIC) {
      resistance += NativeDifficultyRules.remainingResistancePenalty(defender, context);
    }
    if (!defenderPlayer && resistance >= 100) {
      result.immune = true;
      return result;
    }
    NativeFixedElementalMath.rateDamage(result, rawDamageFixed, defenderPlayer, attackerPlayer,
        resistance, statInt(defender, maximumStats[damageType], 0), piercePercent,
        statInt(defender, Stat.magic_damage_reduction, 0), damageRate,
        absorbPercentStats[damageType] != 0 ? statInt(defender, absorbPercentStats[damageType], 0) : 0,
        absorbFlatStats[damageType] != 0 ? statInt(defender, absorbFlatStats[damageType], 0) : 0);
    return result;
  }

  /**
   * Resolves the packet built by D2Game's Static Field callback without
   * discarding its signed 8.8 precision. Native first compensates negative
   * resistance and then runs the ordinary elemental mitigation path; this
   * deliberately prevents negative resistance from increasing Static Field
   * damage while positive resistance and immunity still reduce it.
   */
  public StaticFieldDamageResult calculateStaticFieldDamage(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int damageType, int rawDamageFixed, StateList defenderStates, int difficulty) {
    return calculateStaticFieldDamage(defender, defenderPlayer, attackerPlayer, damageType,
        rawDamageFixed, defenderStates, NativeCombatContext.ofDifficulty(difficulty));
  }

  public StaticFieldDamageResult calculateStaticFieldDamage(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int damageType, int rawDamageFixed, StateList defenderStates, NativeCombatContext context) {
    StaticFieldDamageResult result = new StaticFieldDamageResult();
    if (defender == null || damageType <= DAMAGE_PHYSICAL
        || damageType >= DAMAGE_TYPE_COUNT || rawDamageFixed <= 0) return result;

    short[] resistanceStats = {0, Stat.fireresist, Stat.lightresist,
        Stat.coldresist, Stat.poisonresist, Stat.magicresist};
    short[] maximumStats = {0, Stat.maxfireresist, Stat.maxlightresist,
        Stat.maxcoldresist, Stat.maxpoisonresist, Stat.maxmagicresist};
    short[] absorbPercentStats = {0, Stat.item_absorbfire_percent,
        Stat.item_absorblight_percent, Stat.item_absorbcold_percent, 0,
        Stat.item_absorbmagic_percent};
    short[] absorbFlatStats = {0, Stat.item_absorbfire, Stat.item_absorblight,
        Stat.item_absorbcold, 0, Stat.item_absorbmagic};
    int[] stateResistanceTypes = {-1, 0, 2, 1, 3, 4};

    int resistance = statInt(defender, resistanceStats[damageType], 0);
    if (defenderStates != null) {
      resistance += defenderStates.getTotalResistModifier(
          stateResistanceTypes[damageType]);
    }
    if (defenderPlayer && damageType != DAMAGE_MAGIC) {
      resistance += NativeDifficultyRules.remainingResistancePenalty(defender, context);
    }
    if (!defenderPlayer && resistance >= 100) {
      result.immune = true;
      return result;
    }
    NativeFixedElementalMath.staticFieldDamage(result, rawDamageFixed, defenderPlayer, attackerPlayer,
        resistance, statInt(defender, maximumStats[damageType], 0), damageType == DAMAGE_POISON,
        damageType != DAMAGE_POISON ? statInt(defender, Stat.magic_damage_reduction, 0) : 0,
        absorbPercentStats[damageType] != 0 ? statInt(defender, absorbPercentStats[damageType], 0) : 0,
        absorbFlatStats[damageType] != 0 ? statInt(defender, absorbFlatStats[damageType], 0) : 0);
    return result;
  }

  /** Native monster direct packet: PDR before resistance, player 50% cap, no minimum damage.
   * Integer-damage projection of SUNITDMG_ApplyResistancesAndAbsorb; not an 8.8 replay.
   */
  public CombatResult calculateNativeFixedPhysicalDamage(
      Attributes defender, boolean defenderPlayer, int rawDamage, StateList defenderStates) {
    CombatResult result = new CombatResult();
    result.reset();
    if (defender == null || rawDamage <= 0) return result;
    int resistance = statInt(defender, Stat.damageresist, 0);
    if (defenderStates != null) resistance += defenderStates.getTotalPhysicalResistModifier();
    result.hit = true;
    result.hitChance = 100;
    result.physicalDamage = NativeDirectDamageMath.nativePhysicalDamage(rawDamage, defenderPlayer,
        resistance, statInt(defender, Stat.normal_damage_reduction, 0));
    result.totalDamage = result.physicalDamage;
    return result;
  }

  /** Resolves reflected/direct physical damage without rolling a second hit. */
  public CombatResult calculateFixedPhysicalDamage(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int rawDamage, StateList defenderStates) {
    CombatResult result = new CombatResult();
    result.reset();
    if (defender == null || rawDamage <= 0) return result;

    int resistance = statInt(defender, Stat.damageresist, 0);
    if (defenderStates != null) resistance += defenderStates.getTotalPhysicalResistModifier();
    result.hit = true;
    result.hitChance = 100;
    result.physicalDamage = NativeDirectDamageMath.reflectedPhysicalDamage(rawDamage,
        defenderPlayer, attackerPlayer, resistance, statInt(defender, Stat.normal_damage_reduction, 0));
    result.totalDamage = result.physicalDamage;
    return result;
  }

  /**
   * Resolves an 8.8 fixed-point poison rate and its independent duration.
   * D2Game sends poison damage and poison length through separate rows of the
   * damage-stat table: both receive difficulty/pierce, but only damage receives
   * poison resistance/max-resistance and the PvP damage scalar.
   */
  public CombatResult calculateFixedPoisonDamage(
      Attributes attacker, Attributes defender, boolean defenderPlayer,
      boolean attackerPlayer, int rawDamageFixed, int baseDuration,
      StateList defenderStates, int difficulty) {
    return calculateFixedPoisonDamage(attacker, defender, defenderPlayer, attackerPlayer, rawDamageFixed,
        baseDuration, defenderStates, NativeCombatContext.ofDifficulty(difficulty));
  }

  public CombatResult calculateFixedPoisonDamage(
      Attributes attacker, Attributes defender, boolean defenderPlayer,
      boolean attackerPlayer, int rawDamageFixed, int baseDuration,
      StateList defenderStates, NativeCombatContext context) {
    int mastery = statInt(attacker, Stat.passive_pois_mastery, 0);
    int rawSnapshot = NativeStatusDurationMath.poisonMasterySnapshot(rawDamageFixed, mastery);
    int pierce = statInt(attacker, Stat.item_pierce_pois, 0)
        + statInt(attacker, Stat.passive_pois_pierce, 0);
    return calculateFixedPoisonDamageSnapshot(defender, defenderPlayer, attackerPlayer, rawSnapshot,
        pierce, baseDuration, defenderStates, context);
  }

  /** Replays a Rabies cast snapshot against a newly infected target. */
  public CombatResult calculateFixedPoisonDamageSnapshot(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int rawDamageFixed, int piercePercent, int baseDuration,
      StateList defenderStates, int difficulty) {
    return calculateFixedPoisonDamageSnapshot(defender, defenderPlayer, attackerPlayer, rawDamageFixed,
        piercePercent, baseDuration, defenderStates, NativeCombatContext.ofDifficulty(difficulty));
  }

  public CombatResult calculateFixedPoisonDamageSnapshot(
      Attributes defender, boolean defenderPlayer, boolean attackerPlayer,
      int rawDamageFixed, int piercePercent, int baseDuration,
      StateList defenderStates, NativeCombatContext context) {
    CombatResult result = calculateFixedElementalDamage(
        defender, defenderPlayer, attackerPlayer, DAMAGE_POISON,
        rawDamageFixed, piercePercent, defenderStates, context);
    result.poisonRawDamageFixed = Math.max(0, rawDamageFixed);
    result.poisonPiercePercent = Math.max(0, piercePercent);
    result.poisonBaseDuration = Math.max(0, baseDuration);
    result.poisonDuration = resolvePoisonDuration(defender, defenderPlayer,
        result.poisonBaseDuration, result.poisonPiercePercent, defenderStates, context);
    result.poisonDamagePerFrame = fixed8RateToPerFrame(
        result.elementalDamage[DAMAGE_POISON]);
    // Poison is a state payload, not immediate hit-point damage.
    result.totalDamage = 0;
    return result;
  }

  /** Converts D2's signed 8.8 poison-rate storage to life points per frame. */
  public static float fixed8RateToPerFrame(int damageFixed) {
    return NativeStatusDurationMath.fixed8RateToPerFrame(damageFixed);
  }

  /**
   * Converts an item poison packet (total damage over its duration) to the
   * native 8.8 poison rate used by the poison state.  ItemStatCost's
   * poisonmindam/poisonmaxdam values are not per-frame values; D2 converts
   * the rolled total into BitRate before installing STATE_POISON.
   */
  public static int totalPoisonToBitRate(int totalDamage, int durationFrames) {
    return NativeStatusDurationMath.totalPoisonToBitRate(totalDamage, durationFrames);
  }

  /** Converts an item poison packet to life points per server frame. */
  public static float totalPoisonToPerFrame(int totalDamage, int durationFrames) {
    return NativeStatusDurationMath.totalPoisonToPerFrame(totalDamage, durationFrames);
  }

  static int resolvePoisonDuration(Attributes defender, boolean defenderPlayer,
      int baseDuration, int piercePercent, StateList defenderStates, int difficulty) {
    return resolvePoisonDuration(defender, defenderPlayer, baseDuration, piercePercent,
        defenderStates, NativeCombatContext.ofDifficulty(difficulty));
  }

  static int resolvePoisonDuration(Attributes defender, boolean defenderPlayer,
      int baseDuration, int piercePercent, StateList defenderStates, NativeCombatContext context) {
    if (defender == null || baseDuration <= 0) return 0;
    if (defenderStates != null && defenderStates.hasState(StateId.SHRINE_RESIST_POISON)) {
      return 0;
    }
    return NativeStatusDurationMath.poisonDuration(baseDuration, defenderPlayer,
        statInt(defender, Stat.item_poisonlengthresist, 0), piercePercent, context);
  }

  /**
   * Resolves a melee attack whose physical min/max were already fully scaled
   * by a native skill formula (for example Assassin kick damage).
   */
  public CombatResult calculatePrecomputedMeleeAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer,
      int attackMinDamage, int attackMaxDamage, int attackRating,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving) {
    return calculatePrecomputedMeleeAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer,
        attackMinDamage, attackMaxDamage, attackRating, attackerStates, defenderStates, defenderMoving,
        NativeCombatContext.normal());
  }

  public CombatResult calculatePrecomputedMeleeAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer,
      int attackMinDamage, int attackMaxDamage, int attackRating,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, NativeCombatContext context) {
    return calculatePrecomputedMeleeAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer,
        attackMinDamage, attackMaxDamage, attackRating, 0, DAMAGE_PHYSICAL, attackerStates,
        defenderStates, defenderMoving, null, context);
  }

  /** Native shape attack with a precomputed physical packet and elemental range. */
  public CombatResult calculatePrecomputedMeleeElementalAttack(
      Attributes attacker, Attributes defender, boolean attackerPlayer,
      boolean defenderPlayer, int physicalMin, int physicalMax, int attackRating,
      int[] elementalMin, int[] elementalMax, int coldLength, int poisonLength,
      StateList attackerStates, StateList defenderStates, boolean defenderMoving) {
    return calculatePrecomputedMeleeElementalAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer,
        physicalMin, physicalMax, attackRating, elementalMin, elementalMax, coldLength, poisonLength,
        attackerStates, defenderStates, defenderMoving, NativeCombatContext.normal());
  }

  public CombatResult calculatePrecomputedMeleeElementalAttackWithContext(
      Attributes attacker, Attributes defender, boolean attackerPlayer,
      boolean defenderPlayer, int physicalMin, int physicalMax, int attackRating,
      int[] elementalMin, int[] elementalMax, int coldLength, int poisonLength,
      StateList attackerStates, StateList defenderStates, boolean defenderMoving, NativeCombatContext context) {
    return calculatePrecomputedMeleeElementalAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer,
        physicalMin, physicalMax, attackRating, elementalMin, elementalMax, coldLength, poisonLength, 0,
        DAMAGE_PHYSICAL, attackerStates, defenderStates, defenderMoving, context);
  }

  /** Native melee packet with both skill elements and physical conversion. */
  public CombatResult calculatePrecomputedMeleeElementalAttack(
      Attributes attacker, Attributes defender, boolean attackerPlayer,
      boolean defenderPlayer, int physicalMin, int physicalMax, int attackRating,
      int[] elementalMin, int[] elementalMax, int coldLength, int poisonLength,
      int physicalConversionPercent, int physicalConversionType,
      StateList attackerStates, StateList defenderStates, boolean defenderMoving) {
    return calculatePrecomputedMeleeElementalAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer,
        physicalMin, physicalMax, attackRating, elementalMin, elementalMax, coldLength, poisonLength,
        physicalConversionPercent, physicalConversionType, attackerStates, defenderStates,
        defenderMoving, NativeCombatContext.normal());
  }

  public CombatResult calculatePrecomputedMeleeElementalAttackWithContext(
      Attributes attacker, Attributes defender, boolean attackerPlayer,
      boolean defenderPlayer, int physicalMin, int physicalMax, int attackRating,
      int[] elementalMin, int[] elementalMax, int coldLength, int poisonLength,
      int physicalConversionPercent, int physicalConversionType,
      StateList attackerStates, StateList defenderStates, boolean defenderMoving, NativeCombatContext context) {
    CombatSnapshotFactory.Request request = new CombatSnapshotFactory.Request(
        attacker, defender, attackerPlayer, defenderPlayer, context);
    request.attackerStates = attackerStates;
    request.defenderStates = defenderStates;
    request.defenderMoving = defenderMoving;
    request.profile(NativeSkillAttackProfile.meleeElements(
        CombatSnapshotFactory.skillSource(attacker, attackerStates), physicalMin, physicalMax,
        attackRating, elementalMin, elementalMax, coldLength, poisonLength,
        physicalConversionPercent, physicalConversionType));
    return resolveSnapshot(request);
  }

  /** Native Barbarian weapon mastery context for one concrete weapon hand. */
  public CombatResult calculatePrecomputedMeleeAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer,
      int attackMinDamage, int attackMaxDamage, int attackRating,
      StateList attackerStates, StateList defenderStates, boolean defenderMoving,
      StateList.WeaponMasteryBonus mastery) {
    return calculatePrecomputedMeleeAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer,
        attackMinDamage, attackMaxDamage, attackRating, attackerStates, defenderStates, defenderMoving,
        mastery, NativeCombatContext.normal());
  }

  public CombatResult calculatePrecomputedMeleeAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer,
      int attackMinDamage, int attackMaxDamage, int attackRating,
      StateList attackerStates, StateList defenderStates, boolean defenderMoving,
      StateList.WeaponMasteryBonus mastery, NativeCombatContext context) {
    return calculatePrecomputedMeleeAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer,
        attackMinDamage, attackMaxDamage, attackRating, 0, DAMAGE_PHYSICAL, attackerStates,
        defenderStates, defenderMoving, mastery, context);
  }

  /**
   * Native Amazon Lightning Strike melee packet.  SrvSt10 adds the skill's
   * lightning range to the normal weapon packet, applies Calc1 as enhanced
   * physical damage, and optionally converts Calc4 percent of that physical
   * packet to EType before resistance.
   */
  public CombatResult calculateLightningStrikeAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer,
      int lightningMin, int lightningMax, int damagePercent,
      int conversionPercent, int conversionType,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, int difficulty,
      boolean defenderDemon, boolean defenderUndead,
      boolean defenderIgnoreTargetDefenseAllowed) {
    return calculateLightningStrikeAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer,
        lightningMin, lightningMax, damagePercent, conversionPercent, conversionType, attackerStates,
        defenderStates, defenderMoving, NativeCombatContext.ofDifficulty(difficulty), defenderDemon,
        defenderUndead, defenderIgnoreTargetDefenseAllowed);
  }

  public CombatResult calculateLightningStrikeAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer,
      int lightningMin, int lightningMax, int damagePercent,
      int conversionPercent, int conversionType,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, NativeCombatContext context,
      boolean defenderDemon, boolean defenderUndead,
      boolean defenderIgnoreTargetDefenseAllowed) {
    CombatSnapshotFactory.Request request = new CombatSnapshotFactory.Request(
        attacker, defender, attackerPlayer, defenderPlayer, context);
    request.attackerStates = attackerStates;
    request.defenderStates = defenderStates;
    request.defenderMoving = defenderMoving;
    request.defenderDemon = defenderDemon;
    request.defenderUndead = defenderUndead;
    request.defenderIgnoreTargetDefenseAllowed = defenderIgnoreTargetDefenseAllowed;
    request.profile(NativeSkillAttackProfile.lightningStrike(
        CombatSnapshotFactory.skillSource(attacker, attackerStates), lightningMin, lightningMax,
        damagePercent, conversionPercent, conversionType));
    return resolveSnapshot(request);
  }

  public CombatResult calculatePrecomputedMeleeAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer,
      int attackMinDamage, int attackMaxDamage, int attackRating,
      int physicalConversionPercent, int physicalConversionType,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving) {
    return calculatePrecomputedMeleeAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer,
        attackMinDamage, attackMaxDamage, attackRating, physicalConversionPercent,
        physicalConversionType, attackerStates, defenderStates, defenderMoving,
        NativeCombatContext.normal());
  }

  public CombatResult calculatePrecomputedMeleeAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer,
      int attackMinDamage, int attackMaxDamage, int attackRating,
      int physicalConversionPercent, int physicalConversionType,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, NativeCombatContext context) {
    return calculatePrecomputedMeleeAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer,
        attackMinDamage, attackMaxDamage, attackRating, physicalConversionPercent,
        physicalConversionType, attackerStates, defenderStates, defenderMoving, null, context);
  }

  public CombatResult calculatePrecomputedMeleeAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer,
      int attackMinDamage, int attackMaxDamage, int attackRating,
      int physicalConversionPercent, int physicalConversionType,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery) {
    return calculatePrecomputedMeleeAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer,
        attackMinDamage, attackMaxDamage, attackRating, physicalConversionPercent,
        physicalConversionType, attackerStates, defenderStates, defenderMoving, mastery,
        NativeCombatContext.normal());
  }

  public CombatResult calculatePrecomputedMeleeAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer,
      int attackMinDamage, int attackMaxDamage, int attackRating,
      int physicalConversionPercent, int physicalConversionType,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, StateList.WeaponMasteryBonus mastery, NativeCombatContext context) {
    CombatSnapshotFactory.Request request = new CombatSnapshotFactory.Request(
        attacker, defender, attackerPlayer, defenderPlayer, context);
    request.attackerStates = attackerStates;
    request.defenderStates = defenderStates;
    request.defenderMoving = defenderMoving;
    request.mastery = mastery;
    request.profile(NativeSkillAttackProfile.precomputed(
        attackMinDamage, attackMaxDamage, attackRating, physicalConversionPercent,
        physicalConversionType));
    return resolveSnapshot(request);
  }

  /**
   * Resolves Blade Shield's native flat skill damage plus SrcDam-scaled
   * weapon and elemental stats. Poison length is deliberately not scaled:
   * SUnitDmg uses skill_poison_override_length directly when Venom is active.
   */
  public CombatResult calculateBladeShieldAttack(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer,
      int skillMinDamage, int skillMaxDamage, int sourceDamageScale,
      int skillToHitPercent, boolean alwaysHit,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving) {
    return calculateBladeShieldAttackWithContext(attacker, defender, attackerPlayer, defenderPlayer, skillMinDamage,
        skillMaxDamage, sourceDamageScale, skillToHitPercent, alwaysHit, attackerStates, defenderStates,
        defenderMoving, NativeCombatContext.normal());
  }

  public CombatResult calculateBladeShieldAttackWithContext(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer,
      int skillMinDamage, int skillMaxDamage, int sourceDamageScale,
      int skillToHitPercent, boolean alwaysHit,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, NativeCombatContext context) {
    CombatSnapshotFactory.Request request = new CombatSnapshotFactory.Request(
        attacker, defender, attackerPlayer, defenderPlayer, context);
    request.attackerStates = attackerStates;
    request.defenderStates = defenderStates;
    request.defenderMoving = defenderMoving;
    request.profile(NativeSkillAttackProfile.bladeShield(
        CombatSnapshotFactory.skillSource(attacker, attackerStates), skillMinDamage, skillMaxDamage,
        sourceDamageScale, skillToHitPercent, alwaysHit));
    return resolveSnapshot(request);
  }

  private CombatResult calculateAttackInternal(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, boolean precomputedPhysicalDamage,
      int physicalConversionPercent, int physicalConversionType,
      StateList.WeaponMasteryBonus mastery, int difficulty,
      int magicTargetBonusPercent, boolean defenderDemon, boolean defenderUndead,
      boolean defenderIgnoreTargetDefenseAllowed) {
    return calculateAttackInternal(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, defenderMoving, precomputedPhysicalDamage,
        physicalConversionPercent, physicalConversionType, mastery, difficulty,
        magicTargetBonusPercent, defenderDemon, defenderUndead,
        defenderIgnoreTargetDefenseAllowed, 0);
  }

  private CombatResult calculateAttackInternal(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, boolean precomputedPhysicalDamage,
      int physicalConversionPercent, int physicalConversionType,
      StateList.WeaponMasteryBonus mastery, int difficulty,
      int magicTargetBonusPercent, boolean defenderDemon, boolean defenderUndead,
      boolean defenderIgnoreTargetDefenseAllowed, int enhancedDamageBonus) {
    return calculateAttackInternal(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, defenderMoving, precomputedPhysicalDamage,
        physicalConversionPercent, physicalConversionType, mastery, difficulty,
        magicTargetBonusPercent, defenderDemon, defenderUndead,
        defenderIgnoreTargetDefenseAllowed, enhancedDamageBonus, false);
  }

  private CombatResult calculateAttackInternal(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, boolean precomputedPhysicalDamage,
      int physicalConversionPercent, int physicalConversionType,
      StateList.WeaponMasteryBonus mastery, int difficulty,
      int magicTargetBonusPercent, boolean defenderDemon, boolean defenderUndead,
      boolean defenderIgnoreTargetDefenseAllowed, int enhancedDamageBonus,
      boolean fixedPointElements) {
    return calculateAttackInternal(attacker, defender, attackerPlayer, defenderPlayer, missile,
        attackMinDamageOverride, attackMaxDamageOverride, attackRatingOverride, alwaysHit,
        elementalMinOverride, elementalMaxOverride, coldLengthOverride, poisonLengthOverride,
        attackerStates, defenderStates, defenderMoving, precomputedPhysicalDamage,
        physicalConversionPercent, physicalConversionType, mastery,
        NativeCombatContext.ofDifficulty(difficulty), magicTargetBonusPercent, defenderDemon,
        defenderUndead, defenderIgnoreTargetDefenseAllowed, enhancedDamageBonus, fixedPointElements);
  }

  private CombatResult calculateAttackInternal(
      Attributes attacker, Attributes defender,
      boolean attackerPlayer, boolean defenderPlayer, boolean missile,
      int attackMinDamageOverride, int attackMaxDamageOverride,
      int attackRatingOverride, boolean alwaysHit,
      int[] elementalMinOverride, int[] elementalMaxOverride,
      int coldLengthOverride, int poisonLengthOverride,
      StateList attackerStates, StateList defenderStates,
      boolean defenderMoving, boolean precomputedPhysicalDamage,
      int physicalConversionPercent, int physicalConversionType,
      StateList.WeaponMasteryBonus mastery, NativeCombatContext context,
      int magicTargetBonusPercent, boolean defenderDemon, boolean defenderUndead,
      boolean defenderIgnoreTargetDefenseAllowed, int enhancedDamageBonus,
      boolean fixedPointElements) {
    CombatSnapshotFactory.Request request = new CombatSnapshotFactory.Request(
        attacker, defender, attackerPlayer, defenderPlayer, context);
    request.missile = missile;
    request.attackMinDamageOverride = attackMinDamageOverride;
    request.attackMaxDamageOverride = attackMaxDamageOverride;
    request.attackRatingOverride = attackRatingOverride;
    request.alwaysHit = alwaysHit;
    request.elementalMinOverride = elementalMinOverride;
    request.elementalMaxOverride = elementalMaxOverride;
    request.coldLengthOverride = coldLengthOverride;
    request.poisonLengthOverride = poisonLengthOverride;
    request.attackerStates = attackerStates;
    request.defenderStates = defenderStates;
    request.defenderMoving = defenderMoving;
    request.precomputedPhysicalDamage = precomputedPhysicalDamage;
    request.physicalConversionPercent = physicalConversionPercent;
    request.physicalConversionType = physicalConversionType;
    request.mastery = mastery;
    request.magicTargetBonusPercent = magicTargetBonusPercent;
    request.defenderDemon = defenderDemon;
    request.defenderUndead = defenderUndead;
    request.defenderIgnoreTargetDefenseAllowed = defenderIgnoreTargetDefenseAllowed;
    request.enhancedDamageBonus = enhancedDamageBonus;
    request.fixedPointElements = fixedPointElements;
    return resolveSnapshot(request);
  }

  private CombatResult resolveSnapshot(CombatSnapshotFactory.Request request) {
    if (request.attacker == null || request.defender == null) {
      CombatResult result = new CombatResult();
      result.reset();
      return result;
    }
    CombatSnapshotFactory.Snapshot snapshot = CombatSnapshotFactory.create(request);
    return calculateAttack(snapshot.attacker, snapshot.defender);
  }

  private static int statInt(Attributes attrs, short stat, int defaultValue) {
    if (attrs == null) return defaultValue;
    StatRef ref = attrs.get(stat);
    return ref != null ? ref.asInt() : defaultValue;
  }

  //==========================================================================
  // 核心方法 - 战斗解算
  //==========================================================================

  /**
   * 计算攻击结果
   * 
   * <p>参考 D2MOD SUNITDMG_CalculateDamage
   * 
   * @param attacker 攻击者数据
   * @param defender 防御者数据
   * @return 战斗结果
   */
  public CombatResult calculateAttack(final AttackerData attacker, final DefenderData defender) {
    CombatResult result = attackResolver.resolve(attacker, defender, new CombatResult(),
        new NativeAttackResolver.Gates() {
          @Override public void resolveHit(NativeDamagePacket out, int roll) {
            NativeAttackResolver.copyHit(out, calculateHit(attacker, defender, roll));
          }
          @Override public int blockChance() {
            return calculateBlockChance(defender);
          }
          @Override public boolean shieldBlock(int chance) {
            return rollShieldBlock(chance);
          }
          @Override public boolean blockAnimation() {
            return canPlayBlockAnimation(defender);
          }
        });
    log.debug("[COMBAT_HIT] hit={} blocked={} defenseType={} ar={} defense={} chance={} roll={} physical={} total={} crit={} deadly={} crushing={}",
        result.hit, result.blocked, result.defenseType, result.attackRating, result.targetDefense,
        result.hitChance, result.hitRoll, result.physicalDamage, result.totalDamageHp(),
        result.critical, result.deadlyStrike, result.crushingBlow);
    return result;
  }

  //==========================================================================
  // 命中率计算
  //==========================================================================

  /**
   * 计算命中率
   * 
   * <p>暗黑 II 命中公式：
   * <pre>
   * Chance to hit = 200 * AR / (AR + DR) * attackerLevel /
   *                 (attackerLevel + defenderLevel)
   *      AR = AttackerAR * (100 + ToHitPercent) / 100
   *      DR = DefenderDefense * clvl / (clvl + alvl)
   * 
   * This is the native D2 formula for every attacker/defender type; whether
   * the defender is a player does not introduce an additional PvP factor.
   * </pre>
   */
  public int calculateHitChance(AttackerData attacker, DefenderData defender) {
    return NativeAttackResolver.calculateHitChance(attacker, defender);
  }

  /** Resolves a deterministic native hit roll for boundary and replay tests. */
  public HitResult calculateHit(AttackerData attacker, DefenderData defender, int roll) {
    if (attacker == null || defender == null) {
      return new HitResult(NativeAttackResolver.calculateHit(attacker, defender, roll));
    }
    int rating = NativeAttackResolver.calculateEffectiveAttackRating(attacker, defender);
    int defense = NativeAttackResolver.calculateEffectiveDefense(attacker, defender);
    int chance = attacker.alwaysHit ? 100 : calculateHitChance(attacker, defender);
    return new HitResult(NativeAttackResolver.calculateHit(
        rating, defense, chance, roll, attacker.alwaysHit));
  }


  static boolean hasBlockablePhysicalDamage(AttackerData attacker) {
    return NativeAttackResolver.hasBlockablePhysicalDamage(attacker);
  }

  /** Test seam for the defender-owned native block roll. */
  protected boolean rollShieldBlock(int blockChance) {
    return rolls.percent(blockChance);
  }

  //==========================================================================
  // 格挡计算
  //==========================================================================

  /**
   * 计算格挡率
   * 
   * <p>格挡公式：
   * <pre>
   * Block% = (ClassBlockFactor + ToBlock) * (Dexterity - 15) / (clvl * 2)
   * 限制在 0-75% 之间
   * </pre>
   */
  public int calculateBlockChance(DefenderData defender) {
    return NativeAttackResolver.calculateBlockChance(defender);
  }

  /** Native lastblockframe gate: block still succeeds, but BL is throttled. */
  private boolean canPlayBlockAnimation(DefenderData defender) {
    boolean allowed = NativeAttackResolver.advanceBlockAnimation(defender);
    if (allowed && defender.combatAttributes != null) {
      defender.combatAttributes.aggregate().put(Stat.lastblockframe, defender.currentFrame);
    }
    return allowed;
  }

  /** Compatibility global RNG entry point; production uses the injected domain stream. */
  public static int rollNativeMissileDamage(int minimum, int maximum) {
    int min = Math.max(0, minimum);
    int max = Math.max(min, maximum);
    return max > min ? MathUtils.random(min, max - 1) : min;
  }

  //==========================================================================
  // 辅助方法 - 从属性获取数据
  //==========================================================================

  /**
   * 相关属性 ID 常量
   */
  public static final class Stats {
    public static final short STRENGTH = Stat.strength;
    public static final short DEXTERITY = Stat.dexterity;
    public static final short LEVEL = Stat.level;
    public static final short ATTACK_RATING = Stat.tohit;
    public static final short DEFENSE = Stat.armorclass;
    public static final short MIN_DAMAGE = Stat.mindamage;
    public static final short MAX_DAMAGE = Stat.maxdamage;
    public static final short FIRE_MIN = Stat.firemindam;
    public static final short FIRE_MAX = Stat.firemaxdam;
    public static final short LIGHT_MIN = Stat.lightmindam;
    public static final short LIGHT_MAX = Stat.lightmaxdam;
    public static final short COLD_MIN = Stat.coldmindam;
    public static final short COLD_MAX = Stat.coldmaxdam;
    public static final short POISON_MIN = Stat.poisonmindam;
    public static final short POISON_MAX = Stat.poisonmaxdam;
    public static final short LIFE_LEECH_MIN = Stat.lifedrainmindam;
    public static final short LIFE_LEECH_MAX = Stat.lifedrainmaxdam;
    public static final short MANA_LEECH_MIN = Stat.manadrainmindam;
    public static final short MANA_LEECH_MAX = Stat.manadrainmaxdam;
    public static final short DEADLY_STRIKE = Stat.item_deadlystrike;
    public static final short CRUSHING_BLOW = Stat.item_crushingblow;
    public static final short FIRE_RESIST = Stat.fireresist;
    public static final short LIGHT_RESIST = Stat.lightresist;
    public static final short COLD_RESIST = Stat.coldresist;
    public static final short POISON_RESIST = Stat.poisonresist;
    public static final short DAMAGE_REDUCED = Stat.item_armor_percent;
    public static final short MAGIC_REDUCED = Stat.item_absorbmagic_percent;
  }
}
