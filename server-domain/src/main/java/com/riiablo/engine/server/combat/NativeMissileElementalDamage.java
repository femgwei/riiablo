package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;

/** Immutable monster elements captured in 8.8 at creation (poison is a per-frame rate).
 * Includes audited ordinary source mixtures; skill/formula/mastery/DamageRate opt out.
 * D2MOO: MISSILE_Get*ElemDamage, MISSMODE_RollDamageValue and
 * SUNITDMG_ApplyResistancesAndAbsorb (including its percentage truncation).
 */
public final class NativeMissileElementalDamage {
  public final int type, minimum, maximum, coldLength, poisonLength;
  public final int poisonCount;
  private final int fireMin, fireMax, lightMin, lightMax, coldMin, coldMax;
  private final int poisonMin, poisonMax, magicMin, magicMax;

  public NativeMissileElementalDamage(int type, int minimum, int maximum, int length) {
    this(type, type == DAMAGE_FIRE ? minimum : 0, type == DAMAGE_FIRE ? maximum : 0,
        type == DAMAGE_LIGHTNING ? minimum : 0, type == DAMAGE_LIGHTNING ? maximum : 0,
        type == DAMAGE_COLD ? minimum : 0, type == DAMAGE_COLD ? maximum : 0,
        type == DAMAGE_POISON ? minimum : 0, type == DAMAGE_POISON ? maximum : 0,
        type == DAMAGE_MAGIC ? minimum : 0, type == DAMAGE_MAGIC ? maximum : 0,
        type == DAMAGE_COLD ? length : 0, type == DAMAGE_POISON ? length : 0, 0);
    if (type < DAMAGE_FIRE || type >= DAMAGE_TYPE_COUNT)
      throw new IllegalArgumentException("table element: " + type);
  }

  private NativeMissileElementalDamage(int type, int fireMin, int fireMax,
      int lightMin, int lightMax, int coldMin, int coldMax, int poisonMin, int poisonMax,
      int magicMin, int magicMax, int coldLength, int poisonLength, int poisonCount) {
    this.type = type;
    this.fireMin = fireMin; this.fireMax = fireMax;
    this.lightMin = lightMin; this.lightMax = lightMax;
    this.coldMin = coldMin; this.coldMax = coldMax;
    this.poisonMin = poisonMin; this.poisonMax = poisonMax;
    this.magicMin = magicMin; this.magicMax = magicMax;
    this.minimum = minimum(type);
    this.maximum = maximum(type);
    this.coldLength = Math.max(0, coldLength);
    this.poisonLength = Math.max(0, poisonLength);
    this.poisonCount = poisonCount;
  }

  /** MISSILE_AddStatsToDamage(owner, 8), then CalculateFinalDamage(SrcDamage).
   * Source instant elements are integer HP; poison endpoints already are raw 8.8 rates.
   * Arrays are inputs only: no caller-owned mutable data is retained.
   */
  public static NativeMissileElementalDamage mixed(int tableType, int tableMin, int tableMax,
      int tableLength, int sourceScale, int[] sourceMin, int[] sourceMax,
      int sourceColdLength, int sourcePoisonLength, int sourcePoisonCount, int poisonOverride) {
    if (tableType < DAMAGE_PHYSICAL || tableType >= DAMAGE_TYPE_COUNT
        || sourceScale <= 0 || sourceScale > 255)
      throw new IllegalArgumentException("ordinary source mixture");
    int[] min = new int[DAMAGE_TYPE_COUNT], max = new int[DAMAGE_TYPE_COUNT];
    for (int i = DAMAGE_FIRE; i < DAMAGE_TYPE_COUNT; i++) {
      int shift = i == DAMAGE_POISON ? 1 : 256;
      min[i] = signedInt(((i == tableType ? tableMin : 0L)
          + (long) sourceMin[i] * shift) * sourceScale / 128);
      max[i] = signedInt(((i == tableType ? tableMax : 0L)
          + (long) sourceMax[i] * shift) * sourceScale / 128);
    }
    int cold = positiveInt(((tableType == DAMAGE_COLD ? tableLength : 0L)
        + sourceColdLength) * sourceScale / 128);
    int poison = positiveInt((tableType == DAMAGE_POISON ? tableLength : 0L)
        + (poisonOverride > 0 ? poisonOverride : sourcePoisonLength));
    int count = poisonOverride > 0 ? 0 : signedInt((long) sourcePoisonCount * sourceScale / 128);
    return new NativeMissileElementalDamage(tableType, min[DAMAGE_FIRE], max[DAMAGE_FIRE],
        min[DAMAGE_LIGHTNING], max[DAMAGE_LIGHTNING], min[DAMAGE_COLD], max[DAMAGE_COLD],
        min[DAMAGE_POISON], max[DAMAGE_POISON], min[DAMAGE_MAGIC], max[DAMAGE_MAGIC],
        cold, poison, count);
  }

  public int minimum(int damageType) {
    switch (damageType) {
      case DAMAGE_FIRE: return fireMin;
      case DAMAGE_LIGHTNING: return lightMin;
      case DAMAGE_COLD: return coldMin;
      case DAMAGE_POISON: return poisonMin;
      case DAMAGE_MAGIC: return magicMin;
      default: return 0;
    }
  }

  /** Raw 8.8 endpoints have already been scaled; never run mixed() on restore. */
  public static NativeMissileElementalDamage restore(int type, int[] minimum, int[] maximum,
      int coldLength, int poisonLength, int poisonCount) {
    if (type < DAMAGE_PHYSICAL || type >= DAMAGE_TYPE_COUNT || minimum.length != DAMAGE_TYPE_COUNT
        || maximum.length != DAMAGE_TYPE_COUNT || coldLength < 0 || poisonLength < 0) {
      throw new IllegalArgumentException("elemental snapshot");
    }
    return new NativeMissileElementalDamage(type, minimum[DAMAGE_FIRE], maximum[DAMAGE_FIRE],
        minimum[DAMAGE_LIGHTNING], maximum[DAMAGE_LIGHTNING], minimum[DAMAGE_COLD], maximum[DAMAGE_COLD],
        minimum[DAMAGE_POISON], maximum[DAMAGE_POISON], minimum[DAMAGE_MAGIC], maximum[DAMAGE_MAGIC],
        coldLength, poisonLength, poisonCount);
  }

  public int maximum(int damageType) {
    switch (damageType) {
      case DAMAGE_FIRE: return fireMax;
      case DAMAGE_LIGHTNING: return lightMax;
      case DAMAGE_COLD: return coldMax;
      case DAMAGE_POISON: return poisonMax;
      case DAMAGE_MAGIC: return magicMax;
      default: return 0;
    }
  }

  public int roll(NativeCombatRolls rolls) {
    return roll(type, rolls);
  }

  public int roll(int damageType, NativeCombatRolls rolls) {
    int minimum = minimum(damageType), maximum = maximum(damageType);
    if (minimum <= 0 || maximum <= 0) return 0;
    return rolls.physicalFixed(Math.min(minimum, maximum), Math.max(minimum, maximum));
  }

  /** Writes raw 8.8 damage and absorbed life; no world mutations. */
  public void resolve(int raw, NativeDefenseData d, int pierce, NativeDamagePacket out) {
    resolve(type, raw, d, pierce, out);
  }

  public void resolve(int damageType, int raw, NativeDefenseData d, int pierce, NativeDamagePacket out) {
    if (damageType == DAMAGE_POISON) {
      // Poison has neither MDR nor absorption. Keep rates OUT of the immediate
      // elemental channels, even when length is zero (including poison shrine).
      out.poisonRawDamageFixed = Math.max(0, raw);
      out.poisonPiercePercent = pierce;
      out.poisonBaseDuration = basePoisonDuration();
      out.poisonDamagePerFrame = positiveInt(resist(raw, resistance(d, damageType, pierce))) / 256f;
      return;
    }
    int damage = NativeMitigationMath.reduce(raw, 256,
        (int) resistance(d, damageType, pierce), d.magicDamageReduced, 0);
    int absorbed = NativeMitigationMath.absorb(damage, 256,
        d.absorbPercent[damageType], d.absorbFlat[damageType]);
    out.elementalDamage[damageType] = positiveInt(damage - absorbed);
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
    return positiveInt(resist(basePoisonDuration(), Math.max(-100, Math.min(100, resistance))));
  }

  public int basePoisonDuration() {
    return poisonCount > 1 ? poisonLength / poisonCount : poisonLength;
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

  private static int signedInt(long value) {
    return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
  }
}
