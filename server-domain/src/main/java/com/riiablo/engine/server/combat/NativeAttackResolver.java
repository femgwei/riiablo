package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;

/**
 * Complete snapshot attack resolution, with no attributes, ECS or global random access.
 * Uses shared native mitigation; integer/fixed8 source construction remains explicit.
 * Does not apply life, mana or status changes to the world.
 */
public final class NativeAttackResolver {
  private static final int MAX_CRIT_CHANCE = 100;
  // MISSMODE_FillDamageParams rolls magic before lightning, unlike the legacy host enum.
  private static final int[] MISSILE_ELEMENT_ORDER = {
      DAMAGE_FIRE, DAMAGE_MAGIC, DAMAGE_LIGHTNING, DAMAGE_COLD, DAMAGE_POISON};
  private static final int[] MELEE_ELEMENT_ORDER = {
      DAMAGE_FIRE, DAMAGE_LIGHTNING, DAMAGE_COLD, DAMAGE_MAGIC, DAMAGE_POISON};
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
    if (attacker.ordinaryWeaponPhysical) {
      result.snapshotLifeLeechPercent = attacker.lifeLeech;
      result.snapshotManaLeechPercent = attacker.manaLeech;
    }
    // Reuse only the scalar length/resistance/absorb policy. Melee rolls below
    // deliberately do NOT use MISSMODE_RollDamageValue's positive-min/swap rule.
    NativeMissileElementalDamage nativeElements = attacker.missileElemental;
    if (attacker.nativeMonsterMeleeElements && !attacker.isMissile) {
      nativeElements = NativeMissileElementalDamage.mixed(DAMAGE_PHYSICAL, 0, 0, 0, 128,
          attacker.elementalMinDamage, attacker.elementalMaxDamage,
          attacker.coldLength, attacker.poisonLength, attacker.poisonCount,
          attacker.poisonOverrideLength);
    }
    result.nativeElementalSettlement = nativeElements != null;
    result.physicalScale = attacker.fixedPointPhysical || attacker.missilePhysical != null ? 256 : 1;
    boolean convertsPhysical = attacker.physicalConversionPercent > 0
        && attacker.physicalConversionType > DAMAGE_PHYSICAL
        && attacker.physicalConversionType < DAMAGE_TYPE_COUNT;
    // Keep converted physical fractions even when item elements use legacy integer rolls.
    result.elementalScale = attacker.fixedPointElements || nativeElements != null
        || (result.physicalScale == 256 && convertsPhysical) ? 256 : 1;

    // 1. Resolve the complete hit packet once. Both the combat result and
    // the structured missile audit consume this same AR/defense/chance/roll
    // snapshot instead of recalculating individual fields.
    // GetResultFlags skips AR for running melee targets; missiles with no
    // ToHit flag skip IsHitSuccessful altogether. Legacy snapshots retain
    // their historical unconditional draw contract.
    boolean runningMelee = rolls.nativeSchedule() && !attacker.isMissile
        && defender.isPlayer && defender.isRunning;
    int hitRoll = attacker.preResolvedHit
        || rolls.nativeSchedule() && (attacker.alwaysHit || runningMelee) ? -1 : rolls.hitRoll();
    if (attacker.preResolvedHit) {
      result.hit = true;
      result.hitChance = 100;
      result.hitRoll = -1;
    } else if (gates == null) copyHit(result, calculateHit(attacker, defender, hitRoll));
    else gates.resolveHit(result, hitRoll);
    if (runningMelee) { result.hit = true; result.hitChance = 100; result.hitRoll = -1; }

    if (!result.hit) {
      return result;
    }

    // HandleMissileCollision: owner AR first, FillDamageParams second,
    // SetDamageFlags (defense then owner Crit) third. A blocked missile still
    // fills its damage and consumes owner Crit; an AR miss does neither.
    boolean nativeMissileSchedule = rolls.nativeSchedule() && attacker.isMissile
        && attacker.missilePhysical != null && attacker.missilePhysical.criticalCaptured;
    int prePhysical = nativeMissileSchedule ? calculateBaseDamage(attacker, defender) : 0;
    int[] rawElements = new int[DAMAGE_TYPE_COUNT];
    if (nativeMissileSchedule) {
      for (int type : MISSILE_ELEMENT_ORDER) rawElements[type] = calculateElementalDamage(attacker, type);
    }

    // D2MOO SUNITDMG_ApplyBlockOrDodge evaluates shield block first. Missile
    // mode requests this branch only when the packet has physical damage;
    // elemental-only spell missiles continue to Avoid/Weapon Block instead.
    if (!attacker.preResolvedHit && defender.canBlock && hasBlockablePhysicalDamage(attacker)) {
      int blockChance = gates == null ? calculateBlockChance(defender) : gates.blockChance();
      result.blocked = gates == null ? rolls.defensePercent(blockChance) : gates.shieldBlock(blockChance);

      if (result.blocked) {
        if (nativeMissileSchedule) result.monsterCritical = rollMonsterCritical(attacker);
        result.defenseType = NativeCombatConstants.DEFENSE_BLOCK;
        result.blockAnimationAllowed = gates == null
            ? advanceBlockAnimation(defender) : gates.blockAnimation();
        return result;
      }
    }

    // Failed shield block falls through to the passive resolver, whose moving
    // branch checks Evade and returns before stationary Weapon Block/Dodge/Avoid.
    int passiveDefense = attacker.preResolvedHit ? NativeCombatConstants.DEFENSE_NONE : rolls.passiveDefense(
        defender.attackType, defender.isMoving, defender.passiveDodge,
        defender.passiveAvoid, defender.passiveEvade, defender.passiveWeaponBlock);
    if (passiveDefense == NativeCombatConstants.DEFENSE_DODGE
        || passiveDefense == NativeCombatConstants.DEFENSE_AVOID
        || passiveDefense == NativeCombatConstants.DEFENSE_EVADE
        || passiveDefense == NativeCombatConstants.DEFENSE_WEAPON_BLOCK) {
      result.blocked = true;
      result.defenseType = passiveDefense;
      if (nativeMissileSchedule) result.monsterCritical = rollMonsterCritical(attacker);
      return result;
    }

    // 3. 计算基础物理伤害
    int baseDamage = nativeMissileSchedule ? prePhysical : calculateBaseDamage(attacker, defender);

    // 4. 判定暴击/致命一击
    boolean capturedCritical = attacker.missilePhysical != null
        && attacker.missilePhysical.criticalCaptured;
    result.critical = capturedCritical ? attacker.missilePhysical.creationCritical
        : rollCriticalStrike(attacker);
    // D2MOO only rolls Deadly Strike after the passive/mastery critical
    // branch fails. This preserves both native RNG consumption and the
    // mutually-exclusive result flags.
    result.deadlyStrike = !capturedCritical && !result.critical && rollDeadlyStrike(attacker);

    // 暴击和致命一击不叠加，取较高者
    if (result.critical || result.deadlyStrike) {
      baseDamage = saturatingInt((long) baseDamage * 2);
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
    for (int channel = 1; channel < DAMAGE_TYPE_COUNT; channel++) {
      int type = attacker.missileElemental != null ? MISSILE_ELEMENT_ORDER[channel - 1]
          : attacker.nativeMonsterMeleeElements ? MELEE_ELEMENT_ORDER[channel - 1] : channel;
      int value = nativeMissileSchedule ? rawElements[type] : calculateElementalDamage(attacker, type);
      if (!attacker.fixedPointElements && nativeElements == null && result.elementalScale != 1)
        value = saturatingInt((long) value * result.elementalScale);
      if (type == attacker.physicalConversionType)
        value += (int) ((long) convertedDamage * result.elementalScale / result.physicalScale);
      if (type == DAMAGE_MAGIC && attacker.magicTargetBonusPercent > 0)
        value += value * attacker.magicTargetBonusPercent / 100;
      rawElements[type] = value;
    }
    // SUNITDMG_FillDamageValues rolls life, mana, then stamina after elements.
    // The extra SrcDam share is ADDITIVE; MonStats.Crit never doubles drain.
    if (attacker.fixedMonsterDrain) {
      int[] drains = new int[3];
      for (int i = 0; i < drains.length; i++) {
        int min = saturatingInt((long) attacker.drainMinimum[i] * 256);
        int max = saturatingInt((long) attacker.drainMaximum[i] * 256);
        int value = max >= 8 ? rolls.physicalFixed(min, max) : 0;
        drains[i] = Math.max(0, saturatingInt(value + (long) value * attacker.drainSourceDamage / 128));
      }
      result.fixedMonsterDrain = true;
      result.lifeDrainFixed = drains[0];
      result.manaDrainFixed = drains[1];
      result.staminaDrainFixed = drains[2];
    }
    // MonsterMode::ApplyCriticalDamage is after source rolls/conversion, before
    // resistances; doubles ALL five channels (including poison RATE), not lengths.
    result.monsterCritical = rollMonsterCritical(attacker);
    if (result.monsterCritical) {
      baseDamage = saturatingInt((long) baseDamage * 2);
      for (int type = 1; type < DAMAGE_TYPE_COUNT; type++)
        rawElements[type] = saturatingInt((long) rawElements[type] * 2);
    }
    boolean pvp = attacker.isPlayer && defender.isPlayer;
    baseDamage = NativeMitigationMath.pvp(baseDamage, pvp, result.physicalScale);
    result.physicalDamage = applyPhysicalDamageReduction(baseDamage, defender,
        defender.isUndead && (attacker.missilePhysical != null
            ? attacker.missilePhysical.bypassUndead : attacker.bypassUndeadPhysicalResistance),
        result.physicalScale);

    // 7. 计算元素伤害
    // D2Game handles Cannot Be Frozen / Half Freeze Duration before the
    // ColdLen resistance/pierce row; length never pays damage reduction.
    int coldLength = nativeElements != null ? nativeElements.coldLength : Math.max(0, attacker.coldLength);
    if (defender.cannotBeFrozen) coldLength = 0;
    else if (defender.halfFreezeDuration) coldLength /= 2;
    result.coldDuration = NativeMitigationMath.reduce(coldLength, nativeElements != null ? 256 : 1,
        NativeMitigationMath.resistance(defender.isPlayer, false, false,
            defender.resistances[DAMAGE_COLD], defender.maxResistances[DAMAGE_COLD],
            attacker.elementalPierce[DAMAGE_COLD]), 0, 0);
    if (!attacker.stateDurationsDeferred) {
      result.coldDuration = scaleMonsterColdDuration(
          result.coldDuration, defender.isMonster, attacker.difficulty);
    }
    result.poisonDuration = nativeElements != null
        ? nativeElements.poisonDuration(defender, attacker.elementalPierce[DAMAGE_POISON])
        : Math.max(0, attacker.poisonLength);
    for (int channel = 1; channel < DAMAGE_TYPE_COUNT; channel++) {
      int i = attacker.missileElemental != null ? MISSILE_ELEMENT_ORDER[channel - 1]
          : attacker.nativeMonsterMeleeElements ? MELEE_ELEMENT_ORDER[channel - 1] : channel;
      int elemDamage = NativeMitigationMath.pvp(rawElements[i], pvp, result.elementalScale);
      if (attacker.nativeMonsterMeleeElements && i == DAMAGE_COLD && elemDamage <= 0)
        result.coldDuration = 0;
      if (elemDamage > 0) {
        if (nativeElements != null) {
          nativeElements.resolve(i, elemDamage, defender, attacker.elementalPierce[i], result);
          continue;
        }
        result.elementalDamage[i] = applyElementalResistance(
            elemDamage, defender, i, attacker.elementalPierce[i], result.elementalScale);
        int absorbed = absorbElementalDamage(result.elementalDamage[i], defender, i,
            result.elementalScale);
        result.elementalDamage[i] -= absorbed;
        result.absorbedLife = NativeMitigationMath.positive((long) result.absorbedLife + absorbed);
      }
    }

    // 8. 计算总伤害
    result.totalDamage = result.physicalDamage / result.physicalScale;
    if (result.fixedMonsterDrain) result.totalDamage += result.lifeDrainFixed / 256;
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

  private boolean rollMonsterCritical(NativeAttackData attacker) {
    return attacker.missilePhysical != null && attacker.missilePhysical.ownerCriticalCaptured
        ? attacker.missilePhysical.ownerCritical : rolls.monsterCritical(attacker.monsterCriticalChance);
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
    if (attacker.missileElemental != null) return attacker.missileElemental.roll(damageType, rolls);
    if (attacker.nativeMonsterMeleeElements) {
      // SUNITDMG_FillDamageValues / RollDamageValueInRange: instant stats <<8,
      // poison already is an 8.8 rate; equal/reversed bounds consume no draw.
      if (attacker.elementalMaxDamage[damageType] <= 0) return 0;
      int shift = damageType == DAMAGE_POISON ? 1 : 256;
      long min = (long) attacker.elementalMinDamage[damageType] * shift;
      long max = (long) attacker.elementalMaxDamage[damageType] * shift;
      min += NativePhysicalDamageMath.percentage(min, attacker.elementalMastery[damageType]);
      max += NativePhysicalDamageMath.percentage(max, attacker.elementalMastery[damageType]);
      return Math.max(0, rolls.physicalFixed(saturatingInt(min), saturatingInt(max)));
    }
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

  public static int applyElementalResistance(int damage, NativeDefenseData defender, int damageType,
      int piercePercent) {
    return applyElementalResistance(damage, defender, damageType, piercePercent, 1);
  }

  public static int applyElementalResistance(int damage, NativeDefenseData defender, int damageType,
      int piercePercent, int scale) {
    if (!defender.isPlayer && defender.immuneElemental[damageType]) return 0;
    int maximum = defender.maxResistances != null
        && damageType < defender.maxResistances.length
        ? defender.maxResistances[damageType] : 75;
    return NativeMitigationMath.reduce(damage, scale,
        NativeMitigationMath.resistance(defender.isPlayer, damageType == DAMAGE_PHYSICAL,
            damageType == DAMAGE_MAGIC, defender.resistances[damageType], maximum, piercePercent),
        damageType == DAMAGE_POISON || damageType == DAMAGE_PHYSICAL ? 0 : defender.magicDamageReduced, 0);
  }

  private int absorbElementalDamage(int damage, NativeDefenseData defender, int damageType, int scale) {
    if (damageType == DAMAGE_POISON || damage <= 0 || defender.absorbPercent == null || damageType >= defender.absorbPercent.length) {
      return 0;
    }
    int flat = defender.absorbFlat != null && damageType < defender.absorbFlat.length
        ? defender.absorbFlat[damageType] : 0;
    return NativeMitigationMath.absorb(damage, scale, defender.absorbPercent[damageType], flat);
  }

  private boolean rollCriticalStrike(NativeAttackData attacker) {
    if (attacker.ordinaryWeaponPhysical && rolls.percent(attacker.masteryCritical)) return true;
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
