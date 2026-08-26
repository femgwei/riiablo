package com.riiablo.engine.server.quest;

/** Native save-record transitions and Kashya speech for Act 1 Quest 2. */
public final class Act1BloodRavenQuest {
  /** Act-local D2S record index; index 0 is Warriv and index 1 is A1Q1. */
  public static final int RECORD = 2;

  // gpAct1Q2NpcMessages / ACT1Q2_Callback11_ScrollMessage.
  public static final int MESSAGE_NONE = -1;
  public static final int MESSAGE_INIT = 81;
  public static final int MESSAGE_AFTER_INIT = 82;
  public static final int MESSAGE_EARLY_RETURN = 87;
  public static final int MESSAGE_REWARD = 92;

  private Act1BloodRavenQuest() {}

  public static short start(short record) {
    if (isFinished(record)) return record;
    return NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  /** Leaving town advances only a quest which Kashya has already started. */
  public static short leaveTown(short record) {
    if (isFinished(record) || !NativeQuestRecord.has(record, NativeQuestRecord.STARTED)) {
      return record;
    }
    return NativeQuestRecord.set(record, NativeQuestRecord.LEFT_TOWN);
  }

  /** Native entry can be recorded even when the player skipped Kashya's introduction. */
  public static short enterBurialGrounds(short record) {
    if (isFinished(record)) return record;
    return NativeQuestRecord.set(record, NativeQuestRecord.ENTERED_AREA);
  }

  public static short completeObjective(short record) {
    if (isFinished(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
    return NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  /** Players outside the eligible room learn that Blood Raven died this game. */
  public static short markCompletedNow(short record) {
    if (isFinished(record)) return record;
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
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  public static int selectKashyaMessage(short record) {
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)) return MESSAGE_NONE;
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)) return MESSAGE_REWARD;
    if (!NativeQuestRecord.has(record, NativeQuestRecord.STARTED)) return MESSAGE_INIT;
    if (!NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN)
        && !NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA)) {
      return MESSAGE_AFTER_INIT;
    }
    return MESSAGE_EARLY_RETURN;
  }

  public static String getKashyaSpeech(int messageIndex) {
    switch (messageIndex) {
      case MESSAGE_INIT: return "kashya_act1_q2_init";
      case MESSAGE_AFTER_INIT: return "kashya_act1_q2_after";
      case MESSAGE_EARLY_RETURN: return "kashya_act1_q2_early";
      case MESSAGE_REWARD: return "kashya_act1_q2_success";
      default: return null;
    }
  }
}
