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

  public static boolean isSealObject(int objectClassId) {
    return objectClassId >= FIRST_SEAL && objectClassId <= LAST_SEAL;
  }

  public static short start(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        ? record : NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  /** Native level-change callback marks Chaos Sanctuary as entered while the
   * seal/Diablo state is still pending. */
  public static short enterArea(short record) {
    return NativeQuestRecord.set(start(record), NativeQuestRecord.ENTERED_AREA);
  }

  /** A saved pending/completed record is enough to rebuild the Chaos quest
   * presentation after the transient ECS state has been recreated. */
  public static boolean shouldRestoreCompletedState(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
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
