package com.riiablo.engine.server.combat;

/** Core attribute adapter and source-compatible bridge to the domain difficulty policy. */
public final class NativeDifficultyRules {
  private NativeDifficultyRules() {}

  /** Synthetic callers supply raw stats; CharData already includes this adjustment. */
  public static int remainingResistancePenalty(
      com.riiablo.attributes.Attributes defender, int difficulty) {
    return remainingResistancePenalty(defender, NativeCombatContext.ofDifficulty(difficulty));
  }

  /** Prefer the current game's table; integer-only compatibility callers use stock values. */
  public static int remainingResistancePenalty(
      com.riiablo.attributes.Attributes defender, NativeCombatContext context) {
    if (defender.difficultyResistanceIncluded()) return 0;
    return playerResistancePenalty(context);
  }

  public static int playerResistancePenalty(NativeCombatContext context) {
    return NativeDifficultyPolicy.playerResistancePenalty(context);
  }

  public static int tableResistancePenalty(NativeCombatContext context) {
    return NativeDifficultyPolicy.tableResistancePenalty(context);
  }

  public static int monsterCorpseExplosionMaximum(int baseHp, NativeCombatContext context) {
    return NativeDifficultyPolicy.monsterCorpseExplosionMaximum(baseHp, context);
  }

  public static int monsterCorpseExplosionMaximumForPercent(int baseHp, int percentage) {
    return NativeDifficultyPolicy.monsterCorpseExplosionMaximumForPercent(baseHp, percentage);
  }

  public static int tableMonsterColdDivisor(NativeCombatContext context) {
    return NativeDifficultyPolicy.tableMonsterColdDivisor(context);
  }

  public static int tableMonsterFreezeDivisor(NativeCombatContext context) {
    return NativeDifficultyPolicy.tableMonsterFreezeDivisor(context);
  }

  public static int aiCurseDivisor(NativeCombatContext context) {
    return NativeDifficultyPolicy.aiCurseDivisor(context);
  }

  public static int staticFieldLifeFloorPercent(NativeCombatContext context, boolean expansion) {
    return NativeDifficultyPolicy.staticFieldLifeFloorPercent(context, expansion);
  }

  public static int clamp(int difficulty) {
    return NativeDifficultyPolicy.clamp(difficulty);
  }

  public static int playerResistancePenalty(int difficulty) {
    return NativeDifficultyPolicy.playerResistancePenalty(difficulty);
  }

  public static int monsterColdDurationDivisor(int difficulty) {
    return NativeDifficultyPolicy.monsterColdDurationDivisor(difficulty);
  }

  public static int scaleMonsterColdDuration(int duration, int difficulty) {
    return NativeDifficultyPolicy.scaleMonsterColdDuration(duration, difficulty);
  }

}
