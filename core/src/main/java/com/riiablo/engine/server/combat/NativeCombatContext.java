package com.riiablo.engine.server.combat;

/**
 * Immutable native combat inputs shared by damage and state resolution.
 *
 * <p>The context deliberately contains observable game rules rather than a
 * dark-magic implementation.  dark-magic is a behaviour and test reference;
 * riiablo's runtime values remain sourced from the 1.10f tables and D2MOO.
 */
public final class NativeCombatContext {
  public static final int NORMAL = 0;
  public static final int NIGHTMARE = 1;
  public static final int HELL = 2;

  private final int difficulty;

  private NativeCombatContext(int difficulty) {
    this.difficulty = NativeDifficultyRules.clamp(difficulty);
  }

  public static NativeCombatContext normal() {
    return new NativeCombatContext(NORMAL);
  }

  public static NativeCombatContext ofDifficulty(int difficulty) {
    return new NativeCombatContext(difficulty);
  }

  public int difficulty() {
    return difficulty;
  }

  public boolean isNormal() {
    return difficulty == NORMAL;
  }

  public boolean isNightmare() {
    return difficulty == NIGHTMARE;
  }

  public boolean isHell() {
    return difficulty == HELL;
  }
}
