package com.riiablo.engine.server.quest;

/** Native A1Q0 Warriv introductory conversation state. */
public final class Act1WarrivIntroQuest {
  /** Act-local D2S record index reserved for A1Q0. */
  public static final int RECORD = 0;
  public static final int MESSAGE_NORMAL = 0;
  public static final int MESSAGE_PALADIN = 1;

  private Act1WarrivIntroQuest() {}

  public static boolean isActive(short record) {
    return !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  public static boolean isAllowed(short record, int message) {
    return isActive(record) && (message == MESSAGE_NORMAL || message == MESSAGE_PALADIN);
  }

  public static short claim(short record) {
    return NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
  }
}
