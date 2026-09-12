package com.riiablo.engine.server.quest;

import com.d2moo.common.drlg.D2LevelIds;

/** Native D2MOO A5Q4 Nihlathak state transitions. */
public final class Act5NihlathakQuest {
  private Act5NihlathakQuest() {}

  public static final int RECORD = 4;
  public static final int NIHLATHAK_TEMPLE = D2LevelIds.LEVEL_NIHLATHAKSTEMPLE;
  public static final int HALLS_OF_VAUGHT = D2LevelIds.LEVEL_HALLSOFVAUGHT;
  /** Drehya's first A5Q4 dialogue (opens the quest/portal sequence). */
  public static final int MESSAGE_DREHYA_START = 20137;
  public static final int MESSAGE_DREHYA_REWARD = 20148;

  /** D2MOO's hard-coded SuperUnique index for the quest Nihlathak. */
  public static final int SUPERUNIQUE_NIHLATHAK_BOSS = 60;

  public static short start(short record) {
    return isFinished(record) ? record : NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  public static short complete(short record) {
    if (isFinished(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
    return NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  /** Marks that the player has entered the Nihlathak area. */
  public static short enterArea(short record) {
    return NativeQuestRecord.set(start(record), NativeQuestRecord.ENTERED_AREA);
  }

  public static boolean canClaimReward(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)
        && NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
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
