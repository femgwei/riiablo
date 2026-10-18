package com.riiablo.engine.server.combat;

/** Pure 1.10f difficulty rules used by the native combat layer. */
public final class NativeDifficultyRules {
  private NativeDifficultyRules() {}

  public static int clamp(int difficulty) {
    return Math.max(NativeCombatContext.NORMAL,
        Math.min(NativeCombatContext.HELL, difficulty));
  }

  /** Player elemental resistance penalty in Nightmare/Hell. */
  public static int playerResistancePenalty(int difficulty) {
    switch (clamp(difficulty)) {
      case NativeCombatContext.NIGHTMARE: return -40;
      case NativeCombatContext.HELL: return -100;
      default: return 0;
    }
  }

  /** Monster cold-duration divisor: 1/2/4 for Normal/Nightmare/Hell. */
  public static int monsterColdDurationDivisor(int difficulty) {
    switch (clamp(difficulty)) {
      case NativeCombatContext.NIGHTMARE: return 2;
      case NativeCombatContext.HELL: return 4;
      default: return 1;
    }
  }

  public static int scaleMonsterColdDuration(int duration, int difficulty) {
    if (duration <= 0) return 0;
    return duration / monsterColdDurationDivisor(difficulty);
  }
}
