package com.riiablo.engine.server.quest;

/** Native D2MOO A4Q1 (The Fallen Angel) record transitions. */
public final class Act4IzualQuest {
  private Act4IzualQuest() {}

  /** Act IV record index: Q0 gossip, Q1 Izual. */
  public static final int RECORD = 1;
  public static final int MESSAGE_TYRAEL_INIT = 670;
  public static final int MESSAGE_TYRAEL_REWARD = 676;
  public static final int MESSAGE_IZUAL_GHOST = 675;

  public static short start(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        ? record : NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
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
}
