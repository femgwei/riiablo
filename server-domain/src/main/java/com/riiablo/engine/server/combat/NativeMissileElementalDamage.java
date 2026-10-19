package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;

/** Table-only monster element captured in 8.8 at creation (poison is a per-frame rate).
 * Source/skill/formula/mastery/DamageRate packets require another policy.
 * D2MOO: MISSILE_Get*ElemDamage, MISSMODE_RollDamageValue and
 * SUNITDMG_ApplyResistancesAndAbsorb (including its percentage truncation).
 */
public final class NativeMissileElementalDamage {
  public final int type, minimum, maximum, coldLength, poisonLength;

  public NativeMissileElementalDamage(int type, int minimum, int maximum, int length) {
    if (type != DAMAGE_FIRE && type != DAMAGE_LIGHTNING && type != DAMAGE_COLD
        && type != DAMAGE_MAGIC && type != DAMAGE_POISON)
      throw new IllegalArgumentException("table element: " + type);
    this.type = type;
    this.minimum = minimum;
    this.maximum = maximum;
    this.coldLength = type == DAMAGE_COLD ? Math.max(0, length) : 0;
    this.poisonLength = type == DAMAGE_POISON ? Math.max(0, length) : 0;
  }

  public int roll(NativeCombatRolls rolls) {
    if (minimum <= 0 || maximum <= 0) return 0;
    return rolls.physicalFixed(Math.min(minimum, maximum), Math.max(minimum, maximum));
  }

  /** Writes raw 8.8 damage and absorbed life; no world mutations. */
  public void resolve(int raw, NativeDefenseData d, int pierce, NativeDamagePacket out) {
    if (type == DAMAGE_POISON) {
      // Poison has neither MDR nor absorption. Keep rates OUT of the immediate
      // elemental channels, even when length is zero (including poison shrine).
      out.poisonRawDamageFixed = Math.max(0, raw);
      out.poisonPiercePercent = pierce;
      out.poisonBaseDuration = poisonLength;
      out.poisonDamagePerFrame = positiveInt(resist(raw, resistance(d, type, pierce))) / 256f;
      return;
    }
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

  /** PoisLen has its OWN resistance row, a fixed player cap of 75, no absorption. */
  public int poisonDuration(NativeDefenseData d, int pierce) {
    if (d.poisonShrine) return 0;
    long resistance = d.poisonLengthResistance;
    if (resistance < 100 || !d.isMonster) resistance -= pierce;
    if (!d.isMonster && resistance > 0) resistance = Math.min(75, resistance);
    return positiveInt(resist(poisonLength, Math.max(-100, Math.min(100, resistance))));
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
