package com.riiablo.engine.server.quest;

import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.attributes.ExperienceTable;

/** Native D2MOO A5Q5 Rite of Passage state transitions. */
public final class Act5AncientsQuest {
  private Act5AncientsQuest() {}

  public static final int RECORD = 5;
  /** D2MOO's quest room. Rocky Summit is retained alongside the project's Arreat Summit alias. */
  public static final int ROCKY_SUMMIT = D2LevelIds.LEVEL_ROCKYSUMMIT;
  public static final int ARREAT_SUMMIT = D2LevelIds.LEVEL_ARREATSUMMIT;
  public static final int ANCIENT_WAY = D2LevelIds.LEVEL_ANCIENTSWAY;
  public static final int FIRST_ANCIENT_STATUE = 474;
  public static final int LAST_ANCIENT_STATUE = 476;
  public static final int MESSAGE_QUAL_KEHK_START = 20153;
  public static final int MESSAGE_ANCIENTS_ACTIVATE = 20002;
  public static final int MESSAGE_ENTER_AREA = 20169;
  public static final int SUPERUNIQUE_ANCIENT_1 = 43;
  public static final int SUPERUNIQUE_ANCIENT_2 = 44;
  public static final int SUPERUNIQUE_ANCIENT_3 = 45;
  public static final long NORMAL_REWARD_EXPERIENCE = 1_400_000L;
  public static final long NIGHTMARE_REWARD_EXPERIENCE = 20_000_000L;
  public static final long HELL_REWARD_EXPERIENCE = 40_000_000L;

  public static int requiredLevel(int difficulty) {
    return 20 * (Math.max(0, Math.min(2, difficulty)) + 1);
  }

  public static long baseRewardExperience(int difficulty) {
    switch (difficulty) {
      case 1: return NIGHTMARE_REWARD_EXPERIENCE;
      case 2: return HELL_REWARD_EXPERIENCE;
      default: return NORMAL_REWARD_EXPERIENCE;
    }
  }

  /** Mirrors D2MOO ACT5Q5_RewardPlayer: the difficulty reward is capped to
   * the complete XP span of the player's current level, so the Ancients can
   * advance a character by at most one level. */
  public static long rewardExperience(
      int difficulty, int currentLevel, int classId, ExperienceTable experience) {
    if (experience == null || currentLevel < 1
        || currentLevel >= experience.getMaxLevel(classId)) return 0L;
    long currentThreshold = experience.getExperienceForCurrentLevel(currentLevel, classId);
    long nextThreshold = experience.getExperienceForNextLevel(currentLevel, classId);
    return cappedRewardExperience(
        baseRewardExperience(difficulty), currentThreshold, nextThreshold);
  }

  static long cappedRewardExperience(
      long difficultyReward, long currentThreshold, long nextThreshold) {
    long levelSpan = Math.max(0L, nextThreshold - currentThreshold);
    return Math.min(Math.max(0L, difficultyReward), levelSpan);
  }

  public static short start(short record) {
    return isFinished(record) ? record : NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  public static short enterArea(short record) {
    return NativeQuestRecord.set(start(record), NativeQuestRecord.ENTERED_AREA);
  }

  /** A5Q5 grants experience and marks the quest complete immediately on the third death. */
  public static short complete(short record) {
    if (isFinished(record)) return record;
    record = NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    return NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  /** D2MOO ACT5Q5_OnPlayerDied reset predicate, excluding portal tracking
   * which is not yet represented by the Java quest state. */
  public static boolean shouldResetEncounter(
      boolean encounterActive, boolean ancientsDefeated, int livingPlayersOnSummit) {
    return encounterActive && !ancientsDefeated && livingPlayersOnSummit <= 0;
  }
}
