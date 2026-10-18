package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.CombatSystem.*;

/**
 * Frozen 5399da276 attack orchestration and scalar adapters.
 * Only diagnostics and Attributes lastblockframe persistence are omitted.
 * Existing scalar kernels and NativeCombatRolls are deliberately shared:
 * this oracle verifies orchestration migration, not those prior migrations.
 */
final class PreviousAttackResolver {
  private final NativeCombatRolls rolls;
  private final DefenseCalculator defenseCalculatorResolver;

  PreviousAttackResolver(java.util.function.IntUnaryOperator random) {
    rolls = new NativeCombatRolls(random);
    defenseCalculatorResolver = new DefenseCalculator(random);
  }

  public CombatResult calculateAttack(AttackerData attacker, DefenderData defender) {
    CombatResult result = new CombatResult();
    result.elementalScale = attacker.fixedPointElements ? 256 : 1;

    // 1. Resolve the complete hit packet once. Both the combat result and
    // the structured missile audit consume this same AR/defense/chance/roll
    // snapshot instead of recalculating individual fields.
    PreviousHit hit = rollHit(attacker, defender);
    result.attackRating = hit.attackRating;
    result.targetDefense = hit.targetDefense;
    result.hitChance = hit.chance;
    result.hitRoll = hit.roll;
    result.hit = hit.hit;

    if (!result.hit) {
      return result;
    }

    // D2MOO SUNITDMG_ApplyBlockOrDodge evaluates shield block first. Missile
    // mode requests this branch only when the packet has physical damage;
    // elemental-only spell missiles continue to Avoid/Weapon Block instead.
    if (defender.canBlock && hasBlockablePhysicalDamage(attacker)) {
      int blockChance = calculateBlockChance(defender);
      result.blocked = rollShieldBlock(blockChance);

      if (result.blocked) {
        result.defenseType = DefenseCalculator.DEFENSE_BLOCK;
        result.blockAnimationAllowed = canPlayBlockAnimation(defender);
          return result;
      }
    }

    // If shield block fails, native code falls through to Weapon Block and
    // Dodge/Avoid/Evade in that order.
    int passiveDefense = defenseCalculatorResolver.checkPassiveDefense(
        defender.attackType, defender.isMoving, defender.passiveDodge,
        defender.passiveAvoid, defender.passiveEvade, defender.passiveWeaponBlock);
    if (passiveDefense == DefenseCalculator.DEFENSE_DODGE
        || passiveDefense == DefenseCalculator.DEFENSE_AVOID
        || passiveDefense == DefenseCalculator.DEFENSE_EVADE
        || passiveDefense == DefenseCalculator.DEFENSE_WEAPON_BLOCK) {
      result.blocked = true;
      result.defenseType = passiveDefense;
      return result;
    }

    // 3. 计算基础物理伤害
    int baseDamage = calculateBaseDamage(attacker, defender);

    // 4. 判定暴击/致命一击
    result.critical = rollCriticalStrike(attacker);
    // D2MOO only rolls Deadly Strike after the passive/mastery critical
    // branch fails. This preserves both native RNG consumption and the
    // mutually-exclusive result flags.
    result.deadlyStrike = !result.critical && rollDeadlyStrike(attacker);

    // 暴击和致命一击不叠加，取较高者
    if (result.critical || result.deadlyStrike) {
      baseDamage *= 2;
    }

    // 5. 判定压碎性打击
    result.crushingBlow = rollCrushingBlow(attacker);
    if (result.crushingBlow) {
      int crushDamage = calculateCrushingBlowDamage(defender);
      baseDamage += crushDamage;
    }

    // 6. Skill conversion occurs before either resistance branch. Frenzy's
    // calc4 converts physical damage to magic without losing item elements.
    int convertedDamage = 0;
    if (attacker.physicalConversionPercent > 0
        && attacker.physicalConversionType > DAMAGE_PHYSICAL
        && attacker.physicalConversionType < DAMAGE_TYPE_COUNT) {
      convertedDamage = baseDamage * attacker.physicalConversionPercent / 100;
      baseDamage -= convertedDamage;
    }
    result.physicalDamage = applyPhysicalDamageReduction(baseDamage, defender,
        defender.isUndead && attacker.bypassUndeadPhysicalResistance);

    // 7. 计算元素伤害
    // D2Game runs ColdLen through the same cold-resistance/pierce row as
    // cold damage, then applies Cannot Be Frozen / Half Freeze Duration.
    result.coldDuration = applyElementalResistance(
        Math.max(0, attacker.coldLength), defender, DAMAGE_COLD,
        attacker.elementalPierce[DAMAGE_COLD]);
    if (defender.cannotBeFrozen) result.coldDuration = 0;
    else if (defender.halfFreezeDuration) result.coldDuration /= 2;
    if (!attacker.stateDurationsDeferred) {
      result.coldDuration = scaleMonsterColdDuration(
          result.coldDuration, defender.isMonster, attacker.difficulty);
    }
    result.poisonDuration = Math.max(0, attacker.poisonLength);
    for (int i = 1; i < DAMAGE_TYPE_COUNT; i++) {
      int elemDamage = calculateElementalDamage(attacker, i);
      if (i == attacker.physicalConversionType) elemDamage += convertedDamage * result.elementalScale;
      if (i == DAMAGE_MAGIC && attacker.magicTargetBonusPercent > 0) {
        elemDamage += elemDamage * attacker.magicTargetBonusPercent / 100;
      }
      if (elemDamage > 0) {
        if (attacker.fixedPointElements && i == DAMAGE_LIGHTNING) {
          resolveNativeLightningDamage(elemDamage, attacker, defender, result);
          continue;
        }
        result.elementalDamage[i] = applyElementalResistance(
            elemDamage, defender, i, attacker.elementalPierce[i], result.elementalScale);
        int absorbed = absorbElementalDamage(result.elementalDamage[i], defender, i,
            result.elementalScale);
        result.elementalDamage[i] -= absorbed;
        result.absorbedLife += absorbed;
      }
    }

    // Preserve legacy ordering outside the opted-in Nova lightning packet,
    // which already applied native PvP scaling before mitigation.
    if (attacker.isPlayer && defender.isPlayer) {
      result.physicalDamage = result.physicalDamage * PVP_DAMAGE_PERCENT / 100;
      for (int i = 1; i < DAMAGE_TYPE_COUNT; i++) {
        if (attacker.fixedPointElements && i == DAMAGE_LIGHTNING) continue;
        result.elementalDamage[i] = (int) ((long) result.elementalDamage[i] * PVP_DAMAGE_PERCENT / 100);
      }
    }

    // 8. 计算总伤害
    result.totalDamage = result.physicalDamage;
    for (int i = 1; i < DAMAGE_TYPE_COUNT; i++) {
      // Poison damage is applied by StateUpdater over its duration. Do not
      // also apply it as an immediate hit when a duration is present.
      if (i != DAMAGE_POISON || result.poisonDuration <= 0) {
        result.totalDamage += result.elementalDamage[i] / result.elementalScale;
      }
    }

    // 9. 计算偷取
    result.lifeStolen = calculateLifeLeech(result.physicalDamage, attacker);
    result.manaStolen = calculateManaLeech(result.physicalDamage, attacker);


    return result;
  }

  public int calculateHitChance(AttackerData attacker, DefenderData defender) {
    int rating = calculateEffectiveAttackRating(attacker, defender);
    int defense = calculateEffectiveDefense(attacker, defender);
    if (attacker.ignoreTargetDefense && attacker.isPlayer && !defender.isPlayer
        && defender.ignoreTargetDefenseAllowed) defense = 0;
    return NativeCombatMath.hitChance(rating, defense, attacker.level, defender.level);
  }

  public PreviousHit calculateHit(AttackerData attacker, DefenderData defender, int roll) {
    if (attacker == null || defender == null) {
      return new PreviousHit(0, 0, MIN_TO_HIT_CHANCE, -1, false);
    }
    int attackRating = calculateEffectiveAttackRating(attacker, defender);
    int targetDefense = calculateEffectiveDefense(attacker, defender);
    int chance = attacker.alwaysHit ? 100 : calculateHitChance(attacker, defender);
    int normalizedRoll = attacker.alwaysHit ? -1 : Math.max(0, Math.min(99, roll));
    boolean hit = attacker.alwaysHit || normalizedRoll < chance;
    return new PreviousHit(attackRating, targetDefense, chance, normalizedRoll, hit);
  }

  private PreviousHit rollHit(AttackerData attacker, DefenderData defender) {
    return calculateHit(attacker, defender, rolls.hitRoll());
  }

  private int calculateEffectiveAttackRating(AttackerData attacker, DefenderData defender) {
    return NativeCombatMath.effectiveAttackRating(attacker.attackRating, attacker.isPlayer,
        attacker.dexterity, attacker.attackRatingPercent, attacker.demonToHit, attacker.undeadToHit,
        defender.isDemon, defender.isUndead);
  }

  private int calculateEffectiveDefense(AttackerData attacker, DefenderData defender) {
    return NativeCombatMath.effectiveDefense(defender.defense, defender.defenseVsMissile,
        defender.defenseVsMelee, attacker.isMissile);
  }

  static boolean hasBlockablePhysicalDamage(AttackerData attacker) {
    if (attacker == null) return false;
    if (!attacker.isMissile) return true;
    return attacker.maxDamage > 0 && attacker.physicalConversionPercent < 100;
  }

  protected boolean rollShieldBlock(int blockChance) {
    return rolls.percent(blockChance);
  }

  public int calculateBlockChance(DefenderData defender) {
    return NativeCombatMath.blockChance(defender.canBlock, defender.isPlayer,
        defender.hasEquippedShield, defender.blockFactor, defender.blockChance,
        defender.dexterity, defender.level, defender.isRunning);
  }

  private boolean canPlayBlockAnimation(DefenderData defender) {
    int interval = Math.max(0, defender.fasterBlockRate) / 8 + 15;
    long elapsed = (long) defender.currentFrame - defender.lastBlockFrame;
    boolean allowed = elapsed > interval;
    if (allowed) defender.lastBlockFrame = defender.currentFrame;
    return allowed;
  }

  private int calculateBaseDamage(AttackerData attacker, DefenderData defender) {
    // Keep zero-profile adaptation here; random range semantics live in the domain.
    if (attacker.maxDamage <= 0) return attacker.isMissile && attacker.alwaysHit ? 0 : 1;
    int minimum = Math.max(0, Math.min(attacker.minDamage, attacker.maxDamage));
    int damage = attacker.isMissile
        ? rollMissileDamage(minimum, attacker.maxDamage)
        : rollInclusive(minimum, attacker.maxDamage);
    return NativeCombatMath.physicalDamageAfterBonuses(damage, attacker.isMissile, attacker.strength,
        attacker.enhancedDamagePercent, attacker.demonDamagePercent, attacker.undeadDamagePercent,
        defender != null && defender.isDemon, defender != null && defender.isUndead);
  }

  private int calculateElementalDamage(AttackerData attacker, int damageType) {
    int scale = attacker.fixedPointElements ? 256 : 1;
    int minDmg = scale == 1 ? attacker.elementalMinDamage[damageType]
        : saturatingInt((long) attacker.elementalMinDamage[damageType] * scale);
    int maxDmg = scale == 1 ? attacker.elementalMaxDamage[damageType]
        : saturatingInt((long) attacker.elementalMaxDamage[damageType] * scale);

    if (maxDmg <= 0) {
      return 0;
    }

    return attacker.isMissile
        ? rollMissileDamage(minDmg, maxDmg)
        : rollInclusive(minDmg, maxDmg);
  }

  private int calculateCrushingBlowDamage(DefenderData defender) {
    int percent = defender.isPlayer ? 10 : 25;
    return defender.currentLife * percent / 100;
  }

  private int applyPhysicalDamageReduction(int damage, DefenderData defender) {
    return applyPhysicalDamageReduction(damage, defender, false);
  }

  private int applyPhysicalDamageReduction(
      int damage, DefenderData defender, boolean bypassResistance) {
    return NativeCombatMath.physicalReduction(damage, defender.immunePhysical,
        defender.damageReducedPercent, defender.damageReduced, bypassResistance);
  }

  private void resolveNativeLightningDamage(int rawDamage, AttackerData attacker,
      DefenderData defender, CombatResult result) {
    int type = DAMAGE_LIGHTNING;
    if (defender.immuneElemental[type]
        || (defender.isMonster && defender.resistances[type] >= 100)) return;
    NativeFixedElementalMath.lightningDamage(result, rawDamage, attacker.isPlayer, defender.isPlayer,
        defender.isMonster, defender.immuneElemental[type], defender.resistances[type],
        defender.maxResistances[type], attacker.elementalPierce[type], defender.magicDamageReduced,
        defender.absorbPercent[type], defender.absorbFlat[type]);
  }

  private int applyElementalResistance(int damage, DefenderData defender, int damageType,
      int piercePercent) {
    return applyElementalResistance(damage, defender, damageType, piercePercent, 1);
  }

  private int applyElementalResistance(int damage, DefenderData defender, int damageType,
      int piercePercent, int scale) {
    // Preserve the early immunity return and missing-channel default from the host adapter.
    if (defender.immuneElemental[damageType]) return 0;
    int maximum = defender.maxResistances != null
        && damageType < defender.maxResistances.length && defender.maxResistances[damageType] > 0
        ? defender.maxResistances[damageType] : 75;
    return NativeCombatMath.legacyElementalResistance(damage, false,
        defender.resistances[damageType], maximum, piercePercent,
        damageType != DAMAGE_POISON && damageType != DAMAGE_PHYSICAL,
        defender.magicDamageReduced, scale);
  }

  private int absorbElementalDamage(int damage, DefenderData defender, int damageType) {
    return absorbElementalDamage(damage, defender, damageType, 1);
  }

  private int absorbElementalDamage(int damage, DefenderData defender, int damageType, int scale) {
    if (damage <= 0 || defender.absorbPercent == null || damageType >= defender.absorbPercent.length) {
      return 0;
    }
    int flat = defender.absorbFlat != null && damageType < defender.absorbFlat.length
        ? defender.absorbFlat[damageType] : 0;
    return NativeCombatMath.elementalAbsorb(damage, defender.absorbPercent[damageType], flat, scale);
  }

  private boolean rollCriticalStrike(AttackerData attacker) {
    return rolls.percent(Math.min(attacker.criticalStrike, MAX_CRIT_CHANCE));
  }

  private boolean rollDeadlyStrike(AttackerData attacker) {
    return rolls.percent(Math.min(attacker.deadlyStrike, MAX_CRIT_CHANCE));
  }

  private boolean rollCrushingBlow(AttackerData attacker) {
    return rolls.percent(attacker.crushingBlow);
  }

  private int calculateLifeLeech(int physicalDamage, AttackerData attacker) {
    return NativeCombatMath.leech(physicalDamage, attacker.lifeLeech);
  }

  private int calculateManaLeech(int physicalDamage, AttackerData attacker) {
    return NativeCombatMath.leech(physicalDamage, attacker.manaLeech);
  }

  private int rollInclusive(int min, int max) {
    return rolls.inclusive(min, max);
  }

  private int rollMissileDamage(int minimum, int maximum) {
    return rolls.missileDamage(minimum, maximum);
  }

  private static int saturatingInt(long value) {
    return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) value);
  }

  static int scaleMonsterColdDuration(int duration, boolean monsterTarget, int difficulty) {
    if (duration <= 0 || !monsterTarget) return Math.max(0, duration);
    return NativeDifficultyRules.scaleMonsterColdDuration(duration, difficulty);
  }

  public static final class PreviousHit {
    public final int attackRating;
    public final int targetDefense;
    public final int chance;
    public final int roll;
    public final boolean hit;

    private PreviousHit(int attackRating, int targetDefense, int chance,
        int roll, boolean hit) {
      this.attackRating = attackRating;
      this.targetDefense = targetDefense;
      this.chance = chance;
      this.roll = roll;
      this.hit = hit;
    }
  }
}
