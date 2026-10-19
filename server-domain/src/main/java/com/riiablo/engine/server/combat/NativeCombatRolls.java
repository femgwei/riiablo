package com.riiablo.engine.server.combat;

import java.util.Objects;
import java.util.function.IntUnaryOperator;

/** Combat random decisions over an explicitly supplied, shared random stream.
 * Legacy methods preserve existing draw/short-circuit semantics; physicalFixed
 * is the explicit native endpoint policy.
 */
public final class NativeCombatRolls {
  private final IntUnaryOperator random;

  public NativeCombatRolls(IntUnaryOperator random) {
    this.random = Objects.requireNonNull(random, "random");
  }

  /** Melee draws even when the endpoints coincide. */
  public int inclusive(int min, int max) {
    return min + random.applyAsInt(Math.max(1, max - min + 1));
  }

  /** Missile maximum is excluded; a constant range consumes no draw. */
  public int missileDamage(int minimum, int maximum) {
    int min = Math.max(0, minimum);
    int max = Math.max(min, maximum);
    return max > min ? min + random.applyAsInt(max - min) : min;
  }

  /** Native physical endpoints are already scaled/bonused; no draw for a collapsed interval. */
  public int physicalFixed(int minimum, int maximum) {
    long range = (long) maximum - minimum;
    return range > 0 ? minimum + random.applyAsInt((int) Math.min(Integer.MAX_VALUE, range)) : minimum;
  }

  /** Hit resolution consumes a draw even for an always-hit attack. */
  public int hitRoll() {
    return random.applyAsInt(100);
  }

  /** Positive chances, including guaranteed successes, still consume a draw. */
  public boolean percent(int chance) {
    return chance > 0 && random.applyAsInt(100) < chance;
  }

  private boolean passive(int chance) {
    return chance >= 100
        || random.applyAsInt(100) < Math.min(Math.max(chance, 0), 75);
  }

  /** Snapshot/production path only; the old Attributes defense API differs. */
  public int passiveDefense(int attackType, boolean moving,
      int dodge, int avoid, int evade, int weaponBlock) {
    if (moving) {
      return evade > 0 && passive(evade)
          ? NativeCombatConstants.DEFENSE_EVADE : NativeCombatConstants.DEFENSE_NONE;
    }
    if (weaponBlock > 0 && passive(weaponBlock)) return NativeCombatConstants.DEFENSE_WEAPON_BLOCK;
    if (attackType == NativeCombatConstants.ATTACK_MELEE && dodge > 0 && passive(dodge)) return NativeCombatConstants.DEFENSE_DODGE;
    if (attackType == NativeCombatConstants.ATTACK_RANGED && avoid > 0 && passive(avoid)) return NativeCombatConstants.DEFENSE_AVOID;
    return NativeCombatConstants.DEFENSE_NONE;
  }
}
