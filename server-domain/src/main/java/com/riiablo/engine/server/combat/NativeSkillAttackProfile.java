package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;

/** Pure special-attack input assembly. Deliberately preserves distinct legacy recipes. */
public final class NativeSkillAttackProfile {
  /** Raw item values and separate state overlays captured by the host, without RNG. */
  public static final class Source {
    public int weaponMin, weaponMax, attackRating, coldLength, poisonLength;
    public final int[] itemMin = new int[DAMAGE_TYPE_COUNT];
    public final int[] itemMax = new int[DAMAGE_TYPE_COUNT];
    public final boolean[] itemMaxPresent = new boolean[DAMAGE_TYPE_COUNT];
    public final int[] stateMin = new int[DAMAGE_TYPE_COUNT];
    public final int[] stateMax = new int[DAMAGE_TYPE_COUNT];
    public boolean venomActive;
    public int venomMin, venomMax, venomLength;
  }

  public int physicalMin, physicalMax, attackRating, coldLength, poisonLength;
  /** Null means retain the ordinary weapon/item element snapshot. */
  public int[] elementalMin, elementalMax;
  public boolean precomputedPhysical, alwaysHit;
  public int conversionPercent, conversionType = DAMAGE_PHYSICAL, enhancedDamageBonus;

  private NativeSkillAttackProfile() {}

  public static NativeSkillAttackProfile precomputed(int min, int max, int rating,
      int conversionPercent, int conversionType) {
    NativeSkillAttackProfile out = new NativeSkillAttackProfile();
    out.precomputedPhysical = true;
    out.physicalMin = min;
    out.physicalMax = max;
    out.attackRating = rating;
    out.conversionPercent = conversionPercent;
    out.conversionType = conversionType;
    return out;
  }

  public static NativeSkillAttackProfile meleeElements(Source source, int min, int max, int rating,
      int[] skillMin, int[] skillMax, int coldLength, int poisonLength,
      int conversionPercent, int conversionType) {
    NativeSkillAttackProfile out = precomputed(min, max, rating, conversionPercent, conversionType);
    out.elementalMin = new int[DAMAGE_TYPE_COUNT];
    out.elementalMax = new int[DAMAGE_TYPE_COUNT];
    for (int type = DAMAGE_FIRE; type < DAMAGE_TYPE_COUNT; type++) {
      int low = skillMin != null && type < skillMin.length ? skillMin[type] : 0;
      int high = skillMax != null && type < skillMax.length ? skillMax[type] : low;
      // This recipe uses zero for an absent item maximum, unlike the other two.
      out.elementalMin[type] = Math.max(0, source.itemMin[type]) + Math.max(0, low);
      out.elementalMax[type] = Math.max(out.elementalMin[type],
          Math.max(0, source.itemMax[type]) + Math.max(0, high));
    }
    if (source.venomActive) {
      out.elementalMin[DAMAGE_POISON] += Math.max(0, source.venomMin);
      out.elementalMax[DAMAGE_POISON] += Math.max(0, source.venomMax);
      if (poisonLength <= 0 && source.venomLength > 0) poisonLength = source.venomLength;
    }
    out.coldLength = coldLength <= 0 ? source.coldLength : coldLength;
    out.poisonLength = poisonLength <= 0 ? source.poisonLength : poisonLength;
    return out;
  }

  public static NativeSkillAttackProfile lightningStrike(Source source, int lightningMin,
      int lightningMax, int damagePercent, int conversionPercent, int conversionType) {
    NativeSkillAttackProfile out = new NativeSkillAttackProfile();
    out.elementalMin = new int[DAMAGE_TYPE_COUNT];
    out.elementalMax = new int[DAMAGE_TYPE_COUNT];
    for (int type = DAMAGE_FIRE; type < DAMAGE_TYPE_COUNT; type++) {
      int low = Math.max(0, source.itemMin[type]);
      out.elementalMin[type] = low;
      out.elementalMax[type] = Math.max(low,
          source.itemMaxPresent[type] ? source.itemMax[type] : low);
      out.elementalMin[type] += source.stateMin[type];
      out.elementalMax[type] += source.stateMax[type];
    }
    out.elementalMin[DAMAGE_LIGHTNING] += Math.max(0, lightningMin);
    out.elementalMax[DAMAGE_LIGHTNING] = Math.max(out.elementalMin[DAMAGE_LIGHTNING],
        out.elementalMax[DAMAGE_LIGHTNING] + Math.max(0, lightningMax));
    out.coldLength = source.coldLength;
    out.poisonLength = source.poisonLength;
    out.conversionPercent = Math.max(0, Math.min(100, conversionPercent));
    out.conversionType = conversionType;
    out.enhancedDamageBonus = Math.max(0, damagePercent);
    return out;
  }

  public static NativeSkillAttackProfile bladeShield(Source source, int skillMin, int skillMax,
      int sourceDamageScale, int toHitPercent, boolean alwaysHit) {
    int scale = Math.max(0, sourceDamageScale);
    int sourceMax = Math.max(source.weaponMin, source.weaponMax);
    // SrcDam scales the completed skill+weapon physical range, not only the weapon.
    int min = (Math.max(0, skillMin) + source.weaponMin) * scale / 128;
    int max = Math.max(min, (Math.max(0, skillMax) + sourceMax) * scale / 128);
    int rating = source.attackRating * Math.max(0, 100 + toHitPercent) / 100;
    NativeSkillAttackProfile out = precomputed(min, max, rating, 0, DAMAGE_PHYSICAL);
    out.alwaysHit = alwaysHit;
    out.elementalMin = new int[DAMAGE_TYPE_COUNT];
    out.elementalMax = new int[DAMAGE_TYPE_COUNT];
    for (int type = DAMAGE_FIRE; type < DAMAGE_TYPE_COUNT; type++) {
      int low = source.itemMin[type];
      int high = Math.max(low, source.itemMaxPresent[type] ? source.itemMax[type] : low);
      if (type == DAMAGE_POISON && source.venomActive) {
        low += Math.max(0, source.venomMin);
        high += Math.max(0, source.venomMax);
      }
      out.elementalMin[type] = low * scale / 128;
      out.elementalMax[type] = Math.max(out.elementalMin[type], high * scale / 128);
    }
    out.coldLength = source.coldLength * scale / 128;
    out.poisonLength = source.venomActive && source.venomLength > 0
        ? source.venomLength : source.poisonLength;
    return out;
  }
}
