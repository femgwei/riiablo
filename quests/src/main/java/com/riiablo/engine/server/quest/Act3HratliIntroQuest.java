package com.riiablo.engine.server.quest;

/** Native A3Q0 Hratli introduction state. */
public final class Act3HratliIntroQuest {
  private Act3HratliIntroQuest() {}

  /** Act III record zero is the introductory Hratli quest. */
  public static final int RECORD = 0;
  public static final int MESSAGE = 465;
  public static final int MESSAGE_SORCERESS = 466;

  public static boolean isAvailable(short record) {
    return !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  /** A3Q0 is completed when either Hratli introduction scroll finishes. */
  public static short acknowledge(short record) {
    return NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
  }
}
