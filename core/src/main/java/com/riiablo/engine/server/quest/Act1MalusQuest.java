package com.riiablo.engine.server.quest;

/** Native save-record transitions for Act 1 Quest 3 (Tools of the Trade). */
public final class Act1MalusQuest {
  /** Act-local D2S record index; Q1 and Q2 occupy indices 1 and 2. */
  public static final int RECORD = 3;
  /** D2 item code dropped by the Horadric Malus object. */
  public static final String MALUS_CODE = "mdh";

  public static final int MESSAGE_INIT = 146;
  public static final int MESSAGE_MALUS = 163;
  public static final int MESSAGE_NONE = -1;

  private Act1MalusQuest() {}

  public static short start(short record) {
    if (isRewarded(record)) return record;
    return NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  public static short leaveTown(short record) {
    if (isRewarded(record)) return record;
    record = start(record);
    return NativeQuestRecord.set(record, NativeQuestRecord.LEFT_TOWN);
  }

  public static short markMalusPickedUp(short record) {
    if (isRewarded(record)) return record;
    record = leaveTown(record);
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM2);
  }

  public static boolean canOpenMalus(short record, int level) {
    return level >= 8
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
  }

  public static boolean canTurnIn(short record, int level, boolean hasMalus) {
    return level >= 8 && hasMalus
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
  }

  public static short completeObjective(short record) {
    if (isRewarded(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    return NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
  }

  public static short claimReward(short record) {
    if (!NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)) {
      return record;
    }
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    return NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
  }

  public static boolean isRewarded(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  /** Minimal native Charsi speech selection for the event bridge. */
  public static int selectCharsiMessage(short record, int level, boolean hasMalus) {
    if (isRewarded(record)) return MESSAGE_NONE;
    if (canTurnIn(record, level, hasMalus)) return MESSAGE_MALUS;
    if (!NativeQuestRecord.has(record, NativeQuestRecord.STARTED)) return MESSAGE_INIT;
    return MESSAGE_NONE;
  }

  public static String getCharsiSpeech(int messageIndex) {
    switch (messageIndex) {
      case MESSAGE_INIT: return "charsi_act1_q3_init";
      case MESSAGE_MALUS: return "charsi_act1_q3_successful";
      default: return null;
    }
  }
}
