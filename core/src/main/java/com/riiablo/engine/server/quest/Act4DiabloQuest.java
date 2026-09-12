package com.riiablo.engine.server.quest;

/** Native D2MOO A4Q2 Diablo record transitions. */
public final class Act4DiabloQuest {
  private Act4DiabloQuest() {}

  /** Act IV record index: Q0 gossip, Q1 Izual, Q2 Diablo. */
  public static final int RECORD = 2;
  public static final int FIRST_SEAL = 392;
  public static final int LAST_SEAL = 396;
  public static final int CHAOS_SANCTUARY = 110;
  public static final int MESSAGE_TYRAEL_ACT5 = 20000;

  public static short start(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        ? record : NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  public static short complete(short record) {
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
    return NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  public static short claimCompletion(short record) {
    if (!NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    return NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
  }
}
