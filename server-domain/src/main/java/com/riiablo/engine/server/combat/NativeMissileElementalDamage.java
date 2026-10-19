package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;

/** Table-only, instantaneous monster element captured in 8.8 at creation.
 * Source/skill/formula/mastery/rate packets deliberately require another policy.
 * D2MOO: MISSILE_Get*ElemDamage, MISSMODE_RollDamageValue and
 * SUNITDMG_ApplyResistancesAndAbsorb (including its percentage truncation).
 */
public final class NativeMissileElementalDamage {
  public final int type, minimum, maximum, coldLength;

  public NativeMissileElementalDamage(int type, int minimum, int maximum, int coldLength) {
    if (type != DAMAGE_FIRE && type != DAMAGE_LIGHTNING && type != DAMAGE_COLD
        && type != DAMAGE_MAGIC) throw new IllegalArgumentException("instantaneous element: " + type);
    this.type = type;
    this.minimum = minimum;
    this.maximum = maximum;
    this.coldLength = type == DAMAGE_COLD ? Math.max(0, coldLength) : 0;
  }

  public int roll(NativeCombatRolls rolls) {
    if (minimum <= 0 || maximum <= 0) return 0;
    return rolls.physicalFixed(Math.min(minimum, maximum), Math.max(minimum, maximum));
  }

  /** Writes raw 8.8 damage and absorbed life; no world mutations. */
  public void resolve(int raw, NativeDefenseData d, int pierce, NativeDamagePacket out) {
    long damage = Math.max(0L, (long) raw - Math.max(0L, d.magicDamageReduced) * 256);
    damage = resist(damage, resistance(d, type, pierce));
    long absorbed = NativePhysicalDamageMath.percentage(damage,
        Math.max(0, Math.min(40, d.absorbPercent[type])));
    absorbed += Math.min(damage - absorbed, Math.max(0L, d.absorbFlat[type]) * 256);
    out.elementalDamage[type] = positiveInt(damage - absorbed);
    out.absorbedLife = positiveInt((long) out.absorbedLife + absorbed);
  }

  /** ColdLen uses the cold resistance row, but neither MDR nor absorption. */
  public int coldDuration(NativeDefenseData d, int pierce) {
    return positiveInt(resist(coldLength, resistance(d, DAMAGE_COLD, pierce)));
  }

  private static long resistance(NativeDefenseData d, int type, int pierce) {
    long resistance = d.resistances[type];
    if (d.isMonster && d.immuneElemental[type]) resistance = Math.max(100, resistance);
    // The magic damage row has no pierce stat. Pierce cannot break monster immunity.
    if (type != DAMAGE_MAGIC && (resistance < 100 || !d.isMonster)) resistance -= pierce;
    if (!d.isMonster && resistance > 0)
      resistance = Math.min(resistance, Math.min(95, d.maxResistances[type]));
    return Math.max(-100, Math.min(100, resistance));
  }

  private static long resist(long value, long resistance) {
    // No percentage call for zero resistance: large packets must not lose low bits.
    return value <= 0 ? 0 : resistance == 0 ? value
        : NativePhysicalDamageMath.percentage(value, 100 - resistance);
  }

  private static int positiveInt(long value) {
    return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
  }
}
