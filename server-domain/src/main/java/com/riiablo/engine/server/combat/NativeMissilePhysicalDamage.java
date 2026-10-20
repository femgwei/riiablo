package com.riiablo.engine.server.combat;

/** Immutable ordinary projectile packet captured at creation.
 * D2Common MISSILE_CalculateDamageData stores 8.8 endpoints; D2Game
 * MISSMODE_FillDamageParams applies percentages AFTER the exclusive draw.
 * Host creation supplies audited weapon coefficients; source-missile inheritance
 * remains a separate policy.
 */
public final class NativeMissilePhysicalDamage {
  public final int minimum, maximum;
  public final int damagePercent, demonPercent, undeadPercent;
  public final boolean bypassUndead;
  public final boolean criticalCaptured, creationCritical;
  public final boolean ownerCriticalCaptured, ownerCritical;

  public NativeMissilePhysicalDamage(int minimum, int maximum, int damagePercent,
      int demonPercent, int undeadPercent, boolean bypassUndead) {
    this(minimum, maximum, damagePercent, demonPercent, undeadPercent, bypassUndead, false, false,
        false, false);
  }

  private NativeMissilePhysicalDamage(int minimum, int maximum, int damagePercent,
      int demonPercent, int undeadPercent, boolean bypassUndead,
      boolean criticalCaptured, boolean creationCritical, boolean ownerCriticalCaptured, boolean ownerCritical) {
    this.minimum = minimum;
    this.maximum = maximum;
    this.damagePercent = Math.max(-90, damagePercent);
    this.demonPercent = demonPercent;
    this.undeadPercent = undeadPercent;
    this.bypassUndead = bypassUndead;
    this.criticalCaptured = criticalCaptured;
    this.creationCritical = creationCritical;
    this.ownerCriticalCaptured = ownerCriticalCaptured;
    this.ownerCritical = ownerCritical;
  }

  public NativeMissilePhysicalDamage withEndpoints(int minimum, int maximum) {
    return new NativeMissilePhysicalDamage(minimum, maximum, damagePercent,
        demonPercent, undeadPercent, bypassUndead, criticalCaptured, creationCritical,
        ownerCriticalCaptured, ownerCritical);
  }

  public NativeMissilePhysicalDamage withCreationCritical(boolean critical) {
    return new NativeMissilePhysicalDamage(minimum, maximum, damagePercent,
        demonPercent, undeadPercent, bypassUndead, true, critical, ownerCriticalCaptured, ownerCritical);
  }

  /** Ephemeral impact view; never replace the projectile's creation snapshot. */
  public NativeMissilePhysicalDamage withOwnerCritical(boolean critical) {
    return new NativeMissilePhysicalDamage(minimum, maximum, damagePercent,
        demonPercent, undeadPercent, bypassUndead, criticalCaptured, creationCritical, true, critical);
  }

  /** MISSILE_HasBonusStats(owner, null): passive ALWAYS consumes one draw,
   * even at zero chance; deadly is drawn only after failure and when nonzero. */
  public static boolean creationCritical(int passive, int deadly,
      java.util.function.IntUnaryOperator random) {
    return creationCritical(passive, deadly, 0, random);
  }

  /** Equipped source: mastery is last, unlike SUNITDMG melee's mastery-first order. */
  public static boolean creationCritical(int passive, int deadly, int mastery,
      java.util.function.IntUnaryOperator random) {
    return random.applyAsInt(100) < passive
        || (deadly != 0 && random.applyAsInt(100) < deadly)
        || (mastery != 0 && random.applyAsInt(100) < mastery);
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
