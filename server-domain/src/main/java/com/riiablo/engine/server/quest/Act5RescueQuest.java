package com.riiablo.engine.server.quest;

import com.d2moo.common.drlg.D2LevelIds;

/** Native D2MOO A5Q2 Rescue the Barbarians state transitions. */
public final class Act5RescueQuest {
  private Act5RescueQuest() {}

  public static final int RECORD = 2;
  public static final int FRIGID_HIGHLANDS = D2LevelIds.LEVEL_FRIGIDHIGHLANDS;
  public static final int CAGED_SOLDIER_OBJECT = 473;
  public static final int REQUIRED_CAGES = 5;
  public static final int SOLDIERS_PER_CAGE = 3;
  public static final int MESSAGE_QUAL_KEHK_INIT = 20096;
  public static final int MESSAGE_QUAL_KEHK_REWARD = 20110;

  public static short start(short record) {
    return isFinished(record) ? record : NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  public static short complete(short record) {
    if (isFinished(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
    return NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  public static boolean canClaimReward(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)
        && NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  /** Returns whether a reconnecting player should inherit the game-wide
   * completed-cage state after RoomEx objects have been rebuilt. */
  public static boolean shouldRestoreCompletion(short record, int activatedCages) {
    return activatedCages >= REQUIRED_CAGES
        && !isFinished(record)
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
  }

  public static short claimReward(short record) {
    if (!canClaimReward(record)) return record;
    record = NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM1);
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }
}
