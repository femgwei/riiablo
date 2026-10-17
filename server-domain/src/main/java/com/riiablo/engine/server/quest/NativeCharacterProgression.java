package com.riiablo.engine.server.quest;

/** D2MOO CLIENTS_UpdateCharacterProgression encoded in D2S flag bits 8..15. */
public final class NativeCharacterProgression {
  private static final int SHIFT = 8;
  private static final int MASK = 0xFF << SHIFT;
  private static final int CLASSIC_ACTS = 4;
  private static final int EXPANSION_ACTS = 5;

  private NativeCharacterProgression() {}

  public static int value(int flags) {
    return flags >>> SHIFT & 0xFF;
  }

  public static int update(int flags, int act, int difficulty, boolean expansion) {
    int actsPerDifficulty = expansion ? EXPANSION_ACTS : CLASSIC_ACTS;
    int candidate = Math.max(0, act) + Math.max(0, difficulty) * actsPerDifficulty;
    int progression = Math.max(value(flags), Math.min(0xFF, candidate));
    return flags & ~MASK | progression << SHIFT;
  }

  /** Difficulty zero is always available; each subsequent difficulty needs
   * the preceding difficulty's final-act progression marker. */
  public static boolean isDifficultyUnlocked(int flags, int difficulty, boolean expansion) {
    if (difficulty <= 0) return true;
    int actsPerDifficulty = expansion ? EXPANSION_ACTS : CLASSIC_ACTS;
    return value(flags) >= Math.min(0xFF, difficulty * actsPerDifficulty);
  }
}
