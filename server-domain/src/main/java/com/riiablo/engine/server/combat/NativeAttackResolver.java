package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;

/**
 * Complete snapshot attack resolution, with no attributes, ECS or global random access.
 * Uses native physical mitigation; legacy/native elemental packet differences remain explicit.
 * Does not apply life, mana or status changes to the world.
 */
public final class NativeAttackResolver {
  private static final int MAX_CRIT_CHANCE = 100;
  private final NativeCombatRolls rolls;

  public NativeAttackResolver(NativeCombatRolls rolls) {
    this.rolls = java.util.Objects.requireNonNull(rolls, "rolls");
  }

  /**
   * Compatibility hooks for host overrides and immediate lastblockframe persistence.
   * Null gates use the standalone scalar implementation. Hooks must not re-roll hit.
   */
  public interface Gates {
    void resolveHit(NativeDamagePacket out, int roll);
    int blockChance();
    boolean shieldBlock(int chance);
    boolean blockAnimation();
  }

  public NativeDamagePacket resolve(NativeAttackData attacker, NativeDefenseData defender) {
    return resolve(attacker, defender, new NativeDamagePacket(), null);
  }

  /** Clears and fills the output; input arrays must not alias its damage array. */
  public <T extends NativeDamagePacket> T resolve(NativeAttackData attacker,
      NativeDefenseData defender, T result, Gates gates) {
    result.reset();
    result.physicalScale = attacker.fixedPointPhysical || attacker.missilePhysical != null ? 256 : 1;
    boolean convertsPhysical = attacker.physicalConversionPercent > 0
        && attacker.physicalConversionType > DAMAGE_PHYSICAL
        && attacker.physicalConversionType < DAMAGE_TYPE_COUNT;
    // Keep converted physical fractions even when item elements use legacy integer rolls.
    result.elementalScale = attacker.fixedPointElements
        || (result.physicalScale == 256 && convertsPhysical) ? 256 : 1;

    // 1. Resolve the complete hit packet once. Both the combat result and
    // the structured missile audit consume this same AR/defense/chance/roll
    // snapshot instead of recalculating individual fields.
    int hitRoll = rolls.hitRoll();
    if (gates == null) copyHit(result, calculateHit(attacker, defender, hitRoll));
    else gates.resolveHit(result, hitRoll);

    if (!result.hit) {
      return result;
    }

    // D2MOO SUNITDMG_ApplyBlockOrDodge evaluates shield block first. Missile
    // mode requests this branch only when the packet has physical damage;
    // elemental-only spell missiles continue to Avoid/Weapon Block instead.
    if (defender.canBlock && hasBlockablePhysicalDamage(attacker)) {
      int blockChance = gates == null ? calculateBlockChance(defender) : gates.blockChance();
      result.blocked = gates == null ? rolls.percent(blockChance) : gates.shieldBlock(blockChance);

      if (result.blocked) {
        result.defenseType = NativeCombatConstants.DEFENSE_BLOCK;
        result.blockAnimationAllowed = gates == null
            ? advanceBlockAnimation(defender) : gates.blockAnimation();
        return result;
      }
    }

    // Failed shield block falls through to the passive resolver, whose moving
    // branch checks Evade and returns before stationary Weapon Block/Dodge/Avoid.
    int passiveDefense = rolls.passiveDefense(
        defender.attackType, defender.isMoving, defender.passiveDodge,
        defender.passiveAvoid, defender.passiveEvade, defender.passiveWeaponBlock);
    if (passiveDefense == NativeCombatConstants.DEFENSE_DODGE
        || passiveDefense == NativeCombatConstants.DEFENSE_AVOID
        || passiveDefense == NativeCombatConstants.DEFENSE_EVADE
        || passiveDefense == NativeCombatConstants.DEFENSE_WEAPON_BLOCK) {
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
      baseDamage += crushDamage * result.physicalScale;
    }

    // 6. Skill conversion occurs before either resistance branch. Frenzy's
    // calc4 converts physical damage to magic without losing item elements.
    int convertedDamage = 0;
    if (convertsPhysical) {
      convertedDamage = baseDamage * attacker.physicalConversionPercent / 100;
      baseDamage -= convertedDamage;
    }
    result.physicalDamage = applyPhysicalDamageReduction(baseDamage, defender,
        defender.isUndead && (attacker.missilePhysical != null
            ? attacker.missilePhysical.bypassUndead : attacker.bypassUndeadPhysicalResistance),
        result.physicalScale);

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
      if (!attacker.fixedPointElements && result.elementalScale != 1) {
        elemDamage = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE,
            (long) elemDamage * result.elementalScale));
      }
      if (i == attacker.physicalConversionType) {
        elemDamage += (int) ((long) convertedDamage * result.elementalScale / result.physicalScale);
      }
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
    result.totalDamage = result.physicalDamage / result.physicalScale;
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

  public static void copyHit(NativeDamagePacket out, Hit hit) {
    out.attackRating = hit.attackRating;
    out.targetDefense = hit.targetDefense;
    out.hitChance = hit.chance;
    out.hitRoll = hit.roll;
    out.hit = hit.hit;
  }

  public static class Hit {
    public final int attackRating;
    public final int targetDefense;
    public final int chance;
    public final int roll;
    public final boolean hit;

    protected Hit(int attackRating, int targetDefense, int chance,
        int roll, boolean hit) {
      this.attackRating = attackRating;
      this.targetDefense = targetDefense;
      this.chance = chance;
      this.roll = roll;
      this.hit = hit;
    }
  }

  /** Updates only the snapshot; host persistence is a separate responsibility. */
  public static boolean advanceBlockAnimation(NativeDefenseData defender) {
    int interval = Math.max(0, defender.fasterBlockRate) / 8 + 15;
    long elapsed = (long) defender.currentFrame - defender.lastBlockFrame;
    boolean allowed = elapsed > interval;
    if (allowed) defender.lastBlockFrame = defender.currentFrame;
    return allowed;
  }

  public static int calculateHitChance(NativeAttackData attacker, NativeDefenseData defender) {
    int rating = calculateEffectiveAttackRating(attacker, defender);
    int defense = calculateEffectiveDefense(attacker, defender);
    if (attacker.ignoreTargetDefense && attacker.isPlayer && !defender.isPlayer
        && defender.ignoreTargetDefenseAllowed) defense = 0;
    return NativeCombatMath.hitChance(rating, defense, attacker.level, defender.level);
  }

  public static Hit calculateHit(NativeAttackData attacker, NativeDefenseData defender, int roll) {
    if (attacker == null || defender == null) {
      return new Hit(0, 0, MIN_TO_HIT_CHANCE, -1, false);
    }
    int attackRating = calculateEffectiveAttackRating(attacker, defender);
    int targetDefense = calculateEffectiveDefense(attacker, defender);
    int chance = attacker.alwaysHit ? 100 : calculateHitChance(attacker, defender);
    return calculateHit(attackRating, targetDefense, chance, roll, attacker.alwaysHit);
  }

  /** Precomputed values preserve the host's snapshot-before-virtual-chance ordering. */
  public static Hit calculateHit(int attackRating, int targetDefense, int chance,
      int roll, boolean alwaysHit) {
    int normalizedRoll = alwaysHit ? -1 : Math.max(0, Math.min(99, roll));
    boolean hit = alwaysHit || normalizedRoll < chance;
    return new Hit(attackRating, targetDefense, chance, normalizedRoll, hit);
  }

  public static int calculateEffectiveAttackRating(NativeAttackData attacker, NativeDefenseData defender) {
    return NativeCombatMath.effectiveAttackRating(attacker.attackRating, attacker.isPlayer,
        attacker.dexterity, attacker.attackRatingPercent, attacker.demonToHit, attacker.undeadToHit,
        defender.isDemon, defender.isUndead);
  }

  public static int calculateEffectiveDefense(NativeAttackData attacker, NativeDefenseData defender) {
    return NativeCombatMath.effectiveDefense(defender.defense, defender.defenseVsMissile,
        defender.defenseVsMelee, attacker.isMissile);
  }

  public static boolean hasBlockablePhysicalDamage(NativeAttackData attacker) {
    if (attacker == null) return false;
    if (!attacker.isMissile) return true;
    if (attacker.missilePhysical != null)
      return attacker.missilePhysical.blockable() && attacker.physicalConversionPercent < 100;
    return attacker.maxDamage > 0 && attacker.physicalConversionPercent < 100;
  }

  public static int calculateBlockChance(NativeDefenseData defender) {
    return NativeCombatMath.blockChance(defender.canBlock, defender.isPlayer,
        defender.hasEquippedShield, defender.blockFactor, defender.blockChance,
        defender.dexterity, defender.level, defender.isRunning);
  }

  private int calculateBaseDamage(NativeAttackData attacker, NativeDefenseData defender) {
    if (attacker.missilePhysical != null) return attacker.missilePhysical.roll(defender, rolls);
    if (attacker.fixedPointPhysical) return NativePhysicalDamageMath.roll(attacker, defender, rolls);
    // Preserve zero-profile behavior before applying physical bonuses.
    if (attacker.maxDamage <= 0) return attacker.isMissile && attacker.alwaysHit ? 0 : 1;
    int minimum = Math.max(0, Math.min(attacker.minDamage, attacker.maxDamage));
    int damage = attacker.isMissile
        ? rollMissileDamage(minimum, attacker.maxDamage)
        : rollInclusive(minimum, attacker.maxDamage);
    return NativeCombatMath.physicalDamageAfterBonuses(damage, attacker.isMissile, attacker.strength,
        attacker.enhancedDamagePercent, attacker.demonDamagePercent, attacker.undeadDamagePercent,
        defender != null && defender.isDemon, defender != null && defender.isUndead);
  }

  private int calculateElementalDamage(NativeAttackData attacker, int damageType) {
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

  private int calculateCrushingBlowDamage(NativeDefenseData defender) {
    int percent = defender.isPlayer ? 10 : 25;
    return defender.currentLife * percent / 100;
  }

  private int applyPhysicalDamageReduction(
      int damage, NativeDefenseData defender, boolean bypassResistance, int scale) {
    // SUNITDMG_ApplyResistancesAndAbsorb: flat DR precedes resistance, players
    // cap at 50%, and fully reduced hits deal zero (not a minimum of one).
    // Sanctuary removes only positive undead resistance, not a curse's
    // negative resistance or the target's flat reduction.
    int resistance = defender.damageReducedPercent;
    if (defender.immunePhysical && !defender.isPlayer) resistance = Math.max(100, resistance);
    if (bypassResistance && resistance > 0) resistance = 0;
    return NativeDirectDamageMath.nativePhysicalDamage(
        damage, defender.isPlayer, resistance, defender.damageReduced, scale);
  }

  private void resolveNativeLightningDamage(int rawDamage, NativeAttackData attacker,
      NativeDefenseData defender, NativeDamagePacket result) {
    int type = DAMAGE_LIGHTNING;
    if (defender.immuneElemental[type]
        || (defender.isMonster && defender.resistances[type] >= 100)) return;
    NativeFixedElementalMath.lightningDamage(result, rawDamage, attacker.isPlayer, defender.isPlayer,
        defender.isMonster, defender.immuneElemental[type], defender.resistances[type],
        defender.maxResistances[type], attacker.elementalPierce[type], defender.magicDamageReduced,
        defender.absorbPercent[type], defender.absorbFlat[type]);
  }

  public static int applyElementalResistance(int damage, NativeDefenseData defender, int damageType,
      int piercePercent) {
    return applyElementalResistance(damage, defender, damageType, piercePercent, 1);
  }

  public static int applyElementalResistance(int damage, NativeDefenseData defender, int damageType,
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

  private int absorbElementalDamage(int damage, NativeDefenseData defender, int damageType, int scale) {
    if (damage <= 0 || defender.absorbPercent == null || damageType >= defender.absorbPercent.length) {
      return 0;
    }
    int flat = defender.absorbFlat != null && damageType < defender.absorbFlat.length
        ? defender.absorbFlat[damageType] : 0;
    return NativeCombatMath.elementalAbsorb(damage, defender.absorbPercent[damageType], flat, scale);
  }

  private boolean rollCriticalStrike(NativeAttackData attacker) {
    return rolls.percent(Math.min(attacker.criticalStrike, MAX_CRIT_CHANCE));
  }

  private boolean rollDeadlyStrike(NativeAttackData attacker) {
    return rolls.percent(Math.min(attacker.deadlyStrike, MAX_CRIT_CHANCE));
  }

  private boolean rollCrushingBlow(NativeAttackData attacker) {
    return rolls.percent(attacker.crushingBlow);
  }

  private int calculateLifeLeech(int physicalDamage, NativeAttackData attacker) {
    return NativeCombatMath.leech(physicalDamage, attacker.lifeLeech);
  }

  private int calculateManaLeech(int physicalDamage, NativeAttackData attacker) {
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
    return NativeDifficultyPolicy.scaleMonsterColdDuration(duration, difficulty);
  }
}
