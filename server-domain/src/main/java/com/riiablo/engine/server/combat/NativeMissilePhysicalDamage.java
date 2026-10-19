package com.riiablo.engine.server.combat;

/** Immutable weaponless monster projectile packet captured at creation.
 * D2Common MISSILE_CalculateDamageData stores 8.8 endpoints; D2Game
 * MISSMODE_FillDamageParams applies percentages AFTER the exclusive draw.
 * Weapon coefficients, critical pre-rolls and source-missile inheritance are
 * separate policies, not supplied by this packet.
 */
public final class NativeMissilePhysicalDamage {
  public final int minimum, maximum;
  public final int damagePercent, demonPercent, undeadPercent;
  public final boolean bypassUndead;

  public NativeMissilePhysicalDamage(int minimum, int maximum, int damagePercent,
      int demonPercent, int undeadPercent, boolean bypassUndead) {
    this.minimum = minimum;
    this.maximum = maximum;
    this.damagePercent = Math.max(-90, damagePercent);
    this.demonPercent = demonPercent;
    this.undeadPercent = undeadPercent;
    this.bypassUndead = bypassUndead;
  }

  public NativeMissilePhysicalDamage withEndpoints(int minimum, int maximum) {
    return new NativeMissilePhysicalDamage(minimum, maximum, damagePercent,
        demonPercent, undeadPercent, bypassUndead);
  }

  public boolean blockable() { return minimum > 0 && maximum > 0; }

  public int roll(NativeDefenseData target, NativeCombatRolls rolls) {
    // Unlike melee: no minimum-1/maximum-2 fallback, no equal-range expansion.
    if (!blockable()) return 0;
    int value = rolls.physicalFixed(Math.min(minimum, maximum), Math.max(minimum, maximum));
    long percent = Math.max(-90L, (long) damagePercent
        + (target.isDemon ? demonPercent : 0) + (target.isUndead ? undeadPercent : 0));
    return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value + value * percent / 100));
  }
}
