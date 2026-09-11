package com.riiablo.engine.server.quest;

/** Native Act II quest 5 (The Summoner) record transitions. */
public final class Act2SummonerQuest {
  private Act2SummonerQuest() {}

  /** Act II records: Q0 gossip, Q1..Q4, then A2Q5. */
  public static final int RECORD = 5;

  public static short completeObjective(short record) {
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    return NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
  }

  /** Mirrors the native NPC scroll-message reward acknowledgement. */
  public static short claimReward(short record) {
    if (!NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    return NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
  }

  public static short markCompletedNow(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        ? record : NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }
}
