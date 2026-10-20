package com.riiablo.engine.server.combat;

/** SUNITDMG_CalculateTotalDamage / ApplyResistancesAndAbsorb, without rolls or world writes.
 * Damage and healing use the caller's scale (1 HP or native 256). DamageRate is
 * dwPiercePct's /1024 flat-reduction scalar, NOT resistance pierce.
 */
public final class NativeMitigationMath {
  private NativeMitigationMath() {}

  public static int pvp(int raw, boolean enabled, int scale) {
    return enabled ? percentage(Math.max(0, raw), 17, scale) : Math.max(0, raw);
  }

  public static int resistance(boolean player, boolean physical, boolean magic,
      int resistance, int maximum, int pierce) {
    long adjusted = resistance;
    if (!physical && !magic && (player || adjusted < 100)) adjusted -= pierce;
    int cap = player && adjusted > 0 ? (physical ? 50 : Math.min(95, maximum)) : 100;
    return (int) Math.max(-100L, Math.min(cap, adjusted));
  }

  public static int reduce(int raw, int scale, int resistance, int flat, int damageRate) {
    long reduction = Math.max(0L, flat) * scale;
    if (damageRate > 0) reduction = ratio(reduction, damageRate, 1024);
    long value = Math.max(0L, (long) raw - reduction);
    return resistance == 0 ? positive(value) : percentage(value, 100 - resistance, scale);
  }

  public static int absorb(int damage, int scale, int percent, int flat) {
    long absorbed = percentage(damage, Math.max(0, Math.min(40, percent)), scale);
    return positive(absorbed + Math.min(Math.max(0L, (long) damage - absorbed),
        Math.max(0L, flat) * scale));
  }

  public static int percentage(long value, int percent, int scale) {
    return positive(scale == 256 ? NativePhysicalDamageMath.percentage(value, percent)
        : value * percent / 100);
  }

  /** MONSTERUNIQUE_CalculatePercentage's generic denominator, used by DamageRate. */
  static long ratio(long value, long percent, int denominator) {
    if (value <= 1048576) {
      if (percent <= 65536) return value * percent / denominator;
      if (denominator <= (percent >> 4)) return value * (percent / denominator);
    } else if (denominator <= (value >> 4)) {
      return percent * (value / denominator);
    }
    return value * percent / denominator;
  }

  public static int positive(long value) {
    return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
  }
}
