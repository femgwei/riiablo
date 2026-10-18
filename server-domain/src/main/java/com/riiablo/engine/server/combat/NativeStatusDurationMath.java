package com.riiablo.engine.server.combat;

/** Scalar duration and poison-rate arithmetic; state eligibility and application stay in the host. */
public final class NativeStatusDurationMath {
  private NativeStatusDurationMath() {}

  /** Receives length after Half Freeze and resistance after world/state adjustments. */
  public static int coldLengthBeforeState(int base, boolean player, int resistance, int maximum, int pierce) {
    if (base <= 0 || (!player && resistance >= 100)) return 0;
    resistance -= Math.max(0, pierce);
    resistance = Math.max(NativeCombatConstants.MIN_RESISTANCE, player
        ? Math.min(Math.min(NativeCombatConstants.ABSOLUTE_MAX_RESISTANCE, maximum), resistance) : resistance);
    int length = (int) Math.min(Integer.MAX_VALUE, (long) base * (100 - resistance) / 100L);
    return Math.max(0, length);
  }

  public static int poisonDuration(int base, boolean player, int lengthResistance,
      int pierce, NativeCombatContext context) {
    if (base <= 0) return 0;
    int resistance = lengthResistance - Math.max(0, Math.min(100, pierce));
    // Elemental resistance may be folded into CharData, but length resistance never is.
    if (player) resistance += NativeDifficultyPolicy.playerResistancePenalty(context);
    resistance = player ? Math.max(-100, Math.min(75, resistance)) : Math.max(-100, Math.min(100, resistance));
    long resolved = (long) base * (100 - resistance) / 100L;
    return resolved >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) resolved);
  }

  public static int poisonMasterySnapshot(int rawFixed, int mastery) {
    long mastered = (long) Math.max(0, rawFixed) * Math.max(0, 100 + mastery) / 100L;
    return mastered >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) mastered;
  }

  public static float fixed8RateToPerFrame(int damageFixed) {
    return Math.max(0, damageFixed) / 256f;
  }

  public static int totalPoisonToBitRate(int damage, int frames) {
    if (damage <= 0 || frames <= 0) return 0;
    long rate = (long) damage * 256L / frames;
    return rate >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rate;
  }

  public static float totalPoisonToPerFrame(int damage, int frames) {
    return fixed8RateToPerFrame(totalPoisonToBitRate(damage, frames));
  }
}
