package com.riiablo.engine.server.quest;

/** Native save-record transitions for A1Q1 (Den of Evil). */
public final class Act1DenOfEvilQuest {
  private Act1DenOfEvilQuest() {}

  /** Act-local D2S record index; index 0 is the Warriv gossip quest. */
  public static final int RECORD = 1;

  public static short start(short record) {
    if (isFinished(record)) return record;
    return NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  public static short leaveTown(short record) {
    if (isFinished(record)) return record;
    record = start(record);
    return NativeQuestRecord.set(record, NativeQuestRecord.LEFT_TOWN);
  }

  public static short enterDen(short record) {
    if (isFinished(record)) return record;
    record = leaveTown(record);
    return NativeQuestRecord.set(record, NativeQuestRecord.ENTERED_AREA);
  }

  public static short completeObjective(short record) {
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
    return NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  public static boolean canClaimReward(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)
        && NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  public static short claimReward(short record) {
    if (!canClaimReward(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    record = NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
    return NativeQuestRecord.resetIntermediate(record);
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
  }
}
