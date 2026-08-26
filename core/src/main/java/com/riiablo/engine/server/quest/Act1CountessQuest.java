package com.riiablo.engine.server.quest;

/** Native save-record transitions for Act 1 Quest 5, The Forgotten Tower. */
public final class Act1CountessQuest {
  /** Act-local D2S record index; A1Q5 follows Cain at index 4. */
  public static final int RECORD = 5;
  public static final int TOWER_TOME_MESSAGE = 127;

  private Act1CountessQuest() {}

  public static short discover(short record) {
    if (isFinished(record)) return record;
    return NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  public static short enterForgottenTower(short record) {
    if (isFinished(record)) return record;
    record = discover(record);
    return NativeQuestRecord.set(record, NativeQuestRecord.LEFT_TOWN);
  }

  public static short enterCellar(short record) {
    if (isFinished(record)) return record;
    record = enterForgottenTower(record);
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM1);
  }

  public static short enterCountessLevel(short record) {
    if (isFinished(record)) return record;
    record = enterCellar(record);
    record = NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM2);
    return NativeQuestRecord.set(record, NativeQuestRecord.ENTERED_AREA);
  }

  /** Native A1Q5 has no NPC claim: eligible players are rewarded on death. */
  public static short complete(short record, boolean completedNow) {
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    record = NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
    if (completedNow) {
      record = NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
    }
    return record;
  }

  public static short markCompletedNow(short record) {
    if (isFinished(record)) return record;
    return NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_BEFORE);
  }
}
