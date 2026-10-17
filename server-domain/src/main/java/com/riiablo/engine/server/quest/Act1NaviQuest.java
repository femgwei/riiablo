package com.riiablo.engine.server.quest;

/** Native A1Q7 (Flavie/Navi) dialogue selection from D2MOO. */
public final class Act1NaviQuest {
  private Act1NaviQuest() {}

  public static final int MESSAGE_EARLY_0 = 59;
  public static final int MESSAGE_EARLY_1 = 60;
  public static final int MESSAGE_AFTER_0 = 61;
  public static final int MESSAGE_AFTER_1 = 62;
  public static final int MESSAGE_AFTER_2 = 63;

  public static boolean isAllowed(short denRecord, int message) {
    if (Act1DenOfEvilQuest.isFinished(denRecord)) {
      return message >= MESSAGE_AFTER_0 && message <= MESSAGE_AFTER_2;
    }
    return message == MESSAGE_EARLY_0 || message == MESSAGE_EARLY_1;
  }

  public static int select(short denRecord, int roll) {
    return Act1DenOfEvilQuest.isFinished(denRecord)
        ? MESSAGE_AFTER_0 + Math.floorMod(roll, 3)
        : MESSAGE_EARLY_0 + (Math.floorMod(roll, 2));
  }

  public static String speech(int message) {
    if (message == MESSAGE_EARLY_0 || message == MESSAGE_EARLY_1) {
      return "flavie_a1q1_warning";
    }
    return "flavie_a1q1_after";
  }
}
