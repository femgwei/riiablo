package com.riiablo.engine.server.combat;

/**
 * Scalar arithmetic extracted from the production CombatSystem.
 *
 * <p>No attribute/ECS access or random draws. Integer operation order is deliberately
 * preserved, including legacy mitigation ordering; this is not a rules correction.
 */
public final class NativeCombatMath {
  private NativeCombatMath() {}

  public static int effectiveAttackRating(int rating, boolean player, int dexterity,
      int percent, int demonBonus, int undeadBonus, boolean demon, boolean undead) {
    int ar = rating;
    if (!player) ar += 5 * dexterity;
    if (percent != 0) ar = ar * (100 + percent) / 100;
    if (demon && demonBonus > 0) ar += demonBonus;
    if (undead && undeadBonus > 0) ar += undeadBonus;
    return Math.max(0, ar);
  }

  public static int effectiveDefense(int defense, int vsMissile, int vsMelee, boolean missile) {
    return Math.max(0, defense + (missile ? vsMissile : vsMelee));
  }

  /** Receives effective values after the host's Ignore Target Defense eligibility check. */
  public static int hitChance(int attackRating, int defense, int attackerLevel, int defenderLevel) {
    int chance;
    if (attackRating + defense == 0) {
      chance = NativeCombatConstants.BASE_TO_HIT_CHANCE;
    } else {
      int attacker = Math.max(1, attackerLevel);
      int defender = Math.max(1, defenderLevel);
      int factor = 100 * attackRating / (attackRating + defense);
      chance = NativeCombatConstants.PVP_HIT_FACTOR * attacker * factor / (attacker + defender);
    }
    return Math.max(NativeCombatConstants.MIN_TO_HIT_CHANCE,
        Math.min(NativeCombatConstants.MAX_TO_HIT_CHANCE, chance));
  }

  /** Preserve the existing player dexterity formula and cap-before-running ordering. */
  public static int blockChance(boolean canBlock, boolean player, boolean shield,
      int classFactor, int toBlock, int dexterity, int level, boolean running) {
    if (!canBlock || (player && !shield)) return 0;
    int chance = player ? classFactor + toBlock : toBlock;
    if (player) {
      chance = chance * Math.max(0, dexterity - NativeCombatConstants.BLOCK_BASE_DEX)
          / (NativeCombatConstants.BLOCK_DEXTERITY_FACTOR * Math.max(1, level));
    }
    chance = Math.max(NativeCombatConstants.MIN_BLOCK_CHANCE,
        Math.min(NativeCombatConstants.MAX_BLOCK_CHANCE, chance));
    if (player && running) chance /= 3;
    return chance;
  }

  /** Receives an already-rolled base value; the host retains roll and zero-profile semantics. */
  public static int physicalDamageAfterBonuses(int damage, boolean missile, int strength,
      int enhancedPercent, int demonPercent, int undeadPercent, boolean demon, boolean undead) {
    if (!missile) damage = damage * (100 + Math.max(0, strength)) / 100;
    int percent = enhancedPercent;
    if (demon) percent += demonPercent;
    if (undead) percent += undeadPercent;
    if (percent > 0) damage = damage * (100 + percent) / 100;
    return Math.max(1, damage);
  }

  public static int physicalReduction(int damage, boolean immune, int percent, int flat,
      boolean bypassResistance) {
    if (damage <= 0) return 0;
    if (!bypassResistance && immune) return 0;
    if (!bypassResistance && percent != 0) damage = damage * (100 - percent) / 100;
    damage -= flat;
    return Math.max(1, damage);
  }

  /** Legacy attack ordering: resistance precedes MDR, unlike the separate native lightning path. */
  public static int legacyElementalResistance(int damage, boolean immune, int resistance,
      int maxResistance, int pierce, boolean applyMdr, int magicDamageReduced, int scale) {
    if (immune) return 0;
    resistance -= Math.max(0, Math.min(100, pierce));
    int maximum = Math.min(NativeCombatConstants.ABSOLUTE_MAX_RESISTANCE, maxResistance);
    resistance = Math.max(NativeCombatConstants.MIN_RESISTANCE, resistance);
    resistance = Math.min(maximum, resistance);
    damage = (int) Math.min(Integer.MAX_VALUE, (long) damage * (100 - resistance) / 100);
    if (applyMdr) damage = (int) Math.max(0L, damage - (long) magicDamageReduced * scale);
    return Math.max(0, damage);
  }

  public static int elementalAbsorb(int damage, int percent, int flat, int scale) {
    if (damage <= 0) return 0;
    percent = Math.max(0, Math.min(100, percent));
    int scaledFlat = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) flat * scale));
    int absorbed = (int) ((long) damage * percent / 100);
    return Math.min(damage, absorbed + Math.min(scaledFlat, damage - absorbed));
  }

  /** Existing uncapped physical leech arithmetic; no target/difficulty policy is added here. */
  public static int leech(int physicalDamage, int percent) {
    return percent <= 0 ? 0 : physicalDamage * percent / 100;
  }
}
