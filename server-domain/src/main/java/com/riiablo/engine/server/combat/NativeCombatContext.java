package com.riiablo.engine.server.combat;

import com.riiablo.codec.excel.DifficultyLevels;

/**
 * Native combat inputs shared by damage and state resolution.
 * Instances are immutable; an injected table row is borrowed read-only.
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
  private final DifficultyLevels.Entry difficultyRow;
  private final boolean attackerPlayer;
  private final boolean defenderPlayer;
  private final boolean defenderDemon;
  private final boolean defenderUndead;
  private final int playerCount;
  private final boolean expansion;
  private final boolean stateDurationsDeferred;

  private NativeCombatContext(int difficulty) {
    this(difficulty, null, false, false, false, false, 1);
  }

  private NativeCombatContext(int difficulty, DifficultyLevels.Entry difficultyRow,
      boolean attackerPlayer, boolean defenderPlayer, boolean defenderDemon,
      boolean defenderUndead, int playerCount) {
    this(difficulty, difficultyRow, attackerPlayer, defenderPlayer, defenderDemon,
        defenderUndead, playerCount, true, false);
  }

  private NativeCombatContext(int difficulty, DifficultyLevels.Entry difficultyRow,
      boolean attackerPlayer, boolean defenderPlayer, boolean defenderDemon,
      boolean defenderUndead, int playerCount, boolean expansion, boolean stateDurationsDeferred) {
    this.difficulty = NativeDifficultyPolicy.clamp(difficulty);
    this.difficultyRow = difficultyRow;
    this.attackerPlayer = attackerPlayer;
    this.defenderPlayer = defenderPlayer;
    this.defenderDemon = defenderDemon;
    this.defenderUndead = defenderUndead;
    this.playerCount = Math.max(1, Math.min(8, playerCount));
    this.expansion = expansion;
    this.stateDurationsDeferred = stateDurationsDeferred;
  }

  public static NativeCombatContext normal() {
    return new NativeCombatContext(NORMAL);
  }

  public static NativeCombatContext ofDifficulty(int difficulty) {
    return new NativeCombatContext(difficulty);
  }

  public static NativeCombatContext ofDifficulty(int difficulty,
      DifficultyLevels.Entry difficultyRow) {
    return new NativeCombatContext(difficulty, difficultyRow, false, false,
        false, false, 1);
  }

  public NativeCombatContext withCombatants(boolean attackerPlayer, boolean defenderPlayer) {
    return new NativeCombatContext(difficulty, difficultyRow, attackerPlayer, defenderPlayer,
        defenderDemon, defenderUndead, playerCount, expansion, stateDurationsDeferred);
  }

  public NativeCombatContext withTargetType(boolean defenderDemon, boolean defenderUndead) {
    return new NativeCombatContext(difficulty, difficultyRow, attackerPlayer, defenderPlayer,
        defenderDemon, defenderUndead, playerCount, expansion, stateDurationsDeferred);
  }

  public NativeCombatContext withPlayerCount(int playerCount) {
    return new NativeCombatContext(difficulty, difficultyRow, attackerPlayer, defenderPlayer,
        defenderDemon, defenderUndead, playerCount, expansion, stateDurationsDeferred);
  }

  /** Production states own cold/freeze difficulty divisors; do not pre-divide their packet. */
  public static NativeCombatContext forGameplay(int difficulty,
      DifficultyLevels.Entry row, boolean expansion) {
    return new NativeCombatContext(difficulty, row, false, false, false, false, 1,
        expansion, true);
  }

  public boolean expansion() { return expansion; }

  public boolean stateDurationsDeferred() { return stateDurationsDeferred; }

  public int difficulty() {
    return difficulty;
  }

  public DifficultyLevels.Entry difficultyRow() {
    return difficultyRow;
  }

  public boolean attackerPlayer() {
    return attackerPlayer;
  }

  public boolean defenderPlayer() {
    return defenderPlayer;
  }

  public boolean isPvp() {
    return attackerPlayer && defenderPlayer;
  }

  public boolean defenderDemon() {
    return defenderDemon;
  }

  public boolean defenderUndead() {
    return defenderUndead;
  }

  public int playerCount() {
    return playerCount;
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
