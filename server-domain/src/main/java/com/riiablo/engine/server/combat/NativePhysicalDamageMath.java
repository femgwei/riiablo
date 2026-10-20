package com.riiablo.engine.server.combat;

/** Ordinary weapon/unarmed branch of SUNITDMG_ApplyDamageBonuses (SUnitDmg.cpp).
 * Stats enter in whole HP; endpoints, flat normal damage, bonuses and the draw
 * stay in 8.8. Values beyond the packet's representable positive range saturate;
 * this is a host guard, not a claim about native signed-overflow behaviour.
 */
public final class NativePhysicalDamageMath {
  private NativePhysicalDamageMath() {}

  public static int roll(NativeAttackData a, NativeDefenseData d, NativeCombatRolls rolls) {
    long min = ((long) (a.equippedWeapon ? a.minDamage : Math.max(1, a.minDamage)) + a.normalDamage) * 256;
    long max = ((long) (a.equippedWeapon ? a.maxDamage : Math.max(2, a.maxDamage)) + a.normalDamage) * 256;
    if (min < 1) min = 256;
    if (max <= min) max = min + 256;
    min = Math.min(Integer.MAX_VALUE, min);
    max = Math.min(Integer.MAX_VALUE, max);
    int percent = NativeCombatMath.physicalBonusPercent(false,
        a.ordinaryWeaponPhysical ? a.weaponAttributePercent : a.strength,
        a.enhancedDamagePercent, a.demonDamagePercent, a.undeadDamagePercent,
        d.isDemon, d.isUndead);
    min += percentage(min, (long) percent + a.minimumDamagePercent);
    max += percentage(max, (long) percent + a.maximumDamagePercent);
    // Gameplay stats fit in the native signed interval. Clamp exceptional
    // synthetic/mod inputs before passing a positive int bound to the RNG.
    int value = Math.max(0, rolls.physicalFixed(endpoint(min), endpoint(max)));
    return a.weaponSourceDamage == 128 ? value
        : endpoint(percentage(value, a.weaponSourceDamage, 128));
  }

  /** MONSTERUNIQUE_CalculatePercentage(value, percent, 100), including its
   * large-value early division. Host long intermediates avoid undefined native
   * overflow for mod inputs; that exceptional overflow is not modelled. */
  static long percentage(long value, long percent) {
    return percentage(value, percent, 100);
  }

  static long percentage(long value, long percent, int divisor) {
    if (value <= 1048576) {
      if (percent <= 65536) return value * percent / divisor;
      if (divisor <= (percent >> 4)) return value * (percent / divisor);
    } else if (divisor <= (value >> 4)) {
      return percent * (value / divisor);
    }
    return value * percent / divisor;
  }

  private static int endpoint(long value) {
    return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
  }
}
