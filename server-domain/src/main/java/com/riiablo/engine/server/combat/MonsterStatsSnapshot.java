package com.riiablo.engine.server.combat;

/**
 * Immutable pre-affix spawn attributes; HP values are whole HP, not encoded 8.8.
 * Attack profiles use the spawn base level. A later mode refresh must calculate
 * again at the unit's current level; this snapshot is not a live combat stat-list.
 */
public final class MonsterStatsSnapshot {
  public final int baseLevel, tableLevel, level, rank, difficulty, playerCount;
  public final int hpBonusPercent, combatMultiplier;
  public final int baseMinHp, baseMaxHp, minHp, maxHp, defense, experience;
  public final NativeMonsterStatsCalculator.Attack attack1, attack2, skill1;

  MonsterStatsSnapshot(int baseLevel, int tableLevel, int level, int rank, int difficulty,
      int playerCount, int hpBonusPercent, int combatMultiplier, int baseMinHp, int baseMaxHp,
      int defense, int experience, NativeMonsterStatsCalculator.Attack attack1,
      NativeMonsterStatsCalculator.Attack attack2, NativeMonsterStatsCalculator.Attack skill1) {
    this.baseLevel = baseLevel;
    this.tableLevel = tableLevel;
    this.level = level;
    this.rank = rank;
    this.difficulty = difficulty;
    this.playerCount = playerCount;
    this.hpBonusPercent = hpBonusPercent;
    this.combatMultiplier = combatMultiplier;
    this.baseMinHp = baseMinHp;
    this.baseMaxHp = baseMaxHp;
    this.minHp = hitpointsForBaseRoll(baseMinHp);
    this.maxHp = hitpointsForBaseRoll(baseMaxHp);
    this.defense = defense;
    this.experience = experience;
    this.attack1 = attack1;
    this.attack2 = attack2;
    this.skill1 = skill1;
  }

  /** Host draws from [baseMinHp, baseMaxHp], then scales that roll in native order. */
  public int hitpointsForBaseRoll(int baseHp) {
    if (baseHp < baseMinHp || baseHp > baseMaxHp) {
      throw new IllegalArgumentException("Base HP roll outside snapshot range");
    }
    return NativeMonsterStatsCalculator.spawnHitpoints(baseHp, playerCount, rank, difficulty);
  }
}
