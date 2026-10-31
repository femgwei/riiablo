package com.riiablo.engine.server.quest;

/** Native A2Q0 Jerhyn introduction state. */
public final class Act2JerhynIntroQuest {
  private Act2JerhynIntroQuest() {}

  /** Act II record zero is the introductory Jerhyn quest. */
  public static final int RECORD = 0;
  public static final int MESSAGE = 253;

  public static boolean isAvailable(short record) {
    return !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  /** A2Q0 is completed when Jerhyn's introduction scroll finishes. */
  public static short acknowledge(short record) {
    return NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
  }
}
