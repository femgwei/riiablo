package com.riiablo.engine.server.combat;

/** Weaponless monster branch of SUNITDMG_ApplyDamageBonuses (SUnitDmg.cpp).
 * Stats enter in whole HP; endpoints, flat normal damage, bonuses and the draw
 * stay in 8.8. Values beyond the packet's representable positive range saturate;
 * this is a host guard, not a claim about native signed-overflow behaviour.
 */
public final class NativePhysicalDamageMath {
  private NativePhysicalDamageMath() {}

  public static int roll(NativeAttackData a, NativeDefenseData d, NativeCombatRolls rolls) {
    long min = ((long) Math.max(1, a.minDamage) + a.normalDamage) * 256;
    long max = ((long) Math.max(2, a.maxDamage) + a.normalDamage) * 256;
    if (min < 1) min = 256;
    if (max <= min) max = min + 256;
    min = Math.min(Integer.MAX_VALUE, min);
    max = Math.min(Integer.MAX_VALUE, max);
    int percent = NativeCombatMath.physicalBonusPercent(false, a.strength,
        a.enhancedDamagePercent, a.demonDamagePercent, a.undeadDamagePercent,
        d.isDemon, d.isUndead);
    min += percentage(min, (long) percent + a.minimumDamagePercent);
    max += percentage(max, (long) percent + a.maximumDamagePercent);
    // Gameplay stats fit in the native signed interval. Clamp exceptional
    // synthetic/mod inputs before passing a positive int bound to the RNG.
    return Math.max(0, rolls.physicalFixed(endpoint(min), endpoint(max)));
  }

  /** MONSTERUNIQUE_CalculatePercentage(value, percent, 100), including its
   * large-value early division. Host long intermediates avoid undefined native
   * overflow for mod inputs; that exceptional overflow is not modelled. */
  static long percentage(long value, long percent) {
    if (value <= 1048576) {
      if (percent <= 65536) return value * percent / 100;
      if (100 <= (percent >> 4)) return value * (percent / 100);
    } else if (100 <= (value >> 4)) {
      return percent * (value / 100);
    }
    return value * percent / 100;
  }

  private static int endpoint(long value) {
    return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
  }
}
