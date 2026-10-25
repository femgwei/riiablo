package com.riiablo.attributes;

/** Runtime-only maximum-stamina callback shared by equipment, states and allocation. */
public final class StaminaMaximum {
  private int rawMaximum = Integer.MIN_VALUE;
  private int resolvedMaximum;
  private int equipmentPercent, equipmentPassivePercent, statePercent, statePassivePercent;
  private long bonusMaximum;
  private long equipmentBonus;
  private long revision = -1;
  private int rebuildMaximum, rebuildCurrent;

  /** Exact runtime operands, not a recalculation from the already modified maximum. */
  public long[] checkpoint() {
    return new long[] {rawMaximum, resolvedMaximum, equipmentPercent, equipmentPassivePercent,
        statePercent, statePassivePercent, bonusMaximum, equipmentBonus, revision,
        rebuildMaximum, rebuildCurrent};
  }

  public void restoreCheckpoint(long[] values) {
    if (values == null || values.length != 11 || values[8] < -1) {
      throw new IllegalArgumentException("stamina maximum checkpoint");
    }
    for (int i : new int[] {0, 1, 2, 3, 4, 5, 9, 10}) {
      if (values[i] != (int) values[i]) throw new IllegalArgumentException("stamina operand");
    }
    rawMaximum = (int) values[0]; resolvedMaximum = (int) values[1];
    equipmentPercent = (int) values[2]; equipmentPassivePercent = (int) values[3];
    statePercent = (int) values[4]; statePassivePercent = (int) values[5];
    bonusMaximum = values[6]; equipmentBonus = values[7]; revision = values[8];
    rebuildMaximum = (int) values[9]; rebuildCurrent = (int) values[10];
  }

  void beginRebuild(Attributes attrs) {
    StatListRef live = attrs.aggregate().containsAny(Stat.maxstamina) ? attrs.aggregate() : attrs.base();
    rebuildMaximum = encoded(live, Stat.maxstamina);
    rebuildCurrent = encoded(live, Stat.stamina);
    equipmentPercent = 0;
    equipmentPassivePercent = 0;
    equipmentBonus = 0;
  }

  void addEquipmentPercent(short stat, int percent) {
    if (stat == Stat.skill_passive_staminapercent) equipmentPassivePercent += percent;
    else equipmentPercent += percent;
  }

  void addEquipmentBonus(int value) { equipmentBonus += value; }

  void finishRebuild(Attributes attrs, int staminaPerVitality) {
    if (!attrs.aggregate().containsAny(Stat.maxstamina)) return;
    // D2StatList op9 uses BONUS vitality only and class quarter-points <<6.
    // Resolve after all equipment ops, so ordering relative to percent ops
    // or per-level vitality cannot change the result.
    int bonusVitality = attrs.aggregate().getValue(Stat.vitality, 0)
        - attrs.base().getValue(Stat.vitality, 0);
    rawMaximum = encoded(attrs, Stat.maxstamina);
    bonusMaximum = equipmentBonus + (long) bonusVitality * staminaPerVitality * 64;
    revision = attrs.aggregateRevision();
    write(attrs, rebuildMaximum, rebuildCurrent);
  }

  private void observe(Attributes attrs) {
    int maximum = encoded(attrs, Stat.maxstamina);
    if (rawMaximum == Integer.MIN_VALUE || revision != attrs.aggregateRevision()
        || maximum != resolvedMaximum) {
      // A direct aggregate replacement is an unmodified input, not our last output.
      rawMaximum = maximum;
      equipmentPercent = 0;
      equipmentPassivePercent = 0;
      bonusMaximum = 0;
      resolvedMaximum = maximum;
      revision = attrs.aggregateRevision();
    }
  }

  public int applyStatePercent(Attributes attrs, int percent) {
    return applyStatePercent(attrs, percent, 0);
  }

  public int applyStatePercent(Attributes attrs, int percent, int passivePercent) {
    if (!attrs.aggregate().containsAny(Stat.maxstamina)) return 0;
    observe(attrs);
    int previous = encoded(attrs, Stat.maxstamina);
    int current = encoded(attrs, Stat.stamina);
    statePercent = percent;
    statePassivePercent = passivePercent;
    return write(attrs, previous, current);
  }

  public void increaseForVitality(Attributes attrs, int gain) {
    int maximum = increaseBaseMaximum(attrs, gain);
    int current = bounded(Math.min((long) maximum, (long) encoded(attrs, Stat.stamina) + gain));
    attrs.aggregate().putEncoded(Stat.stamina, current);
    attrs.base().putEncoded(Stat.stamina, current);
  }

  /** Permanent growth, before source-specific refill/allocation behavior. */
  public int increaseBaseMaximum(Attributes attrs, int gain) {
    observe(attrs);
    int previous = encoded(attrs, Stat.maxstamina);
    int current = encoded(attrs, Stat.stamina);
    rawMaximum = bounded((long) rawMaximum + gain);
    attrs.base().putEncoded(Stat.maxstamina,
        bounded((long) encoded(attrs.base(), Stat.maxstamina) + gain));
    return write(attrs, previous, current);
  }

  private int write(Attributes attrs, int previous, int current) {
    // D2StatList's nPreviousOpStatValue excludes op2/op9 additions; different
    // percentage op stats read that same base and truncate independently.
    resolvedMaximum = bounded((long) rawMaximum
        + (long) rawMaximum * ((long) equipmentPercent + statePercent) / 100
        + (long) rawMaximum * ((long) equipmentPassivePercent + statePassivePercent) / 100
        + bonusMaximum);
    attrs.aggregate().putEncoded(Stat.maxstamina, resolvedMaximum);
    attrs.aggregate().putEncoded(Stat.stamina, rescale(current, previous, resolvedMaximum));
    return resolvedMaximum;
  }

  /** ItemMode.cpp: preserve zero, use a minimum one-point denominator and truncate. */
  public static int rescale(int current, int previous, int next) {
    if (previous != next && previous > 0 && current > 0 && next > 0) {
      return Math.max(1, Math.min(next,
          (int) ((double) current / Math.max(256, previous) * next)));
    }
    return Math.max(0, Math.min(next, current));
  }

  private static int bounded(long value) {
    return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
  }
  private static int encoded(Attributes attrs, short stat) { return encoded(attrs.aggregate(), stat); }
  private static int encoded(StatListRef stats, short stat) {
    StatRef value = stats.get(stat);
    return value == null ? 0 : value.encodedValues();
  }
}
