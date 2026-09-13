package com.riiablo.engine.server.quest;

/** Native D2MOO A4Q3 Hellforge state and resource codes. */
public final class Act4HellforgeQuest {
  private Act4HellforgeQuest() {}

  /** Act IV record index: Q0 gossip, Q1 Izual, Q2 Diablo, Q3 Hellforge. */
  public static final int RECORD = 3;
  public static final int HELLFORGE_OBJECT = 376;
  public static final String SOULSTONE = "mss";
  public static final String HAMMER = "hfh";

  /** Native Cain4 scroll-message ids (the speech table uses 166--168). */
  public static final int MESSAGE_CAIN_INIT_NO_STONE = 678;
  public static final int MESSAGE_CAIN_INIT_HAS_STONE = 679;
  public static final int MESSAGE_CAIN_REWARD = 680;

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

  public static short claimReward(short record) {
    if (!canClaimReward(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    return NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
  }

  /** D2MOO only accepts Cain4's reward speech while the reward is pending. */
  public static boolean canClaimReward(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)
        && NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  public static int selectCainMessage(short record, boolean hasSoulstone) {
    if (canClaimReward(record)) return MESSAGE_CAIN_REWARD;
    if (!NativeQuestRecord.has(record, NativeQuestRecord.STARTED)) {
      return hasSoulstone ? MESSAGE_CAIN_INIT_HAS_STONE : MESSAGE_CAIN_INIT_NO_STONE;
    }
    return hasSoulstone ? MESSAGE_CAIN_INIT_HAS_STONE : MESSAGE_CAIN_INIT_NO_STONE;
  }
}
