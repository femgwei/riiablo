package com.riiablo.engine.server.quest;

/** Native save-record transitions for Act 1 Quest 6, Sisters to the Slaughter. */
public final class Act1AndarielQuest {
  public static final int RECORD = 6;
  public static final int MESSAGE_CAIN_INIT = 166;
  public static final int MESSAGE_WARRIV_REWARD = 183;

  private Act1AndarielQuest() {}

  public static short start(short record) {
    if (isFinished(record)) return record;
    return NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  public static short leaveTown(short record) {
    if (isFinished(record)) return record;
    record = start(record);
    return NativeQuestRecord.set(record, NativeQuestRecord.LEFT_TOWN);
  }

  public static short enterCatacombs(short record) {
    if (isFinished(record)) return record;
    record = leaveTown(record);
    return NativeQuestRecord.set(record, NativeQuestRecord.ENTERED_AREA);
  }

  public static short completePending(short record) {
    if (isFinished(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
    return NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  public static short markCompletedNow(short record) {
    if (isFinished(record)) return record;
    return NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  public static short claimReward(short record) {
    if (!NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        || !NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)) return record;
    record = NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
    return NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_BEFORE);
  }
}
