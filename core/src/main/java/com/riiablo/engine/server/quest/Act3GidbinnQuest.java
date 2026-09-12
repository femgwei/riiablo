package com.riiablo.engine.server.quest;

/** Native D2MOO A3Q3 Gidbinn rules exposed as the project's A3Q2 quest. */
public final class Act3GidbinnQuest {
  private Act3GidbinnQuest() {}

  /** Act III records: Q0 gossip, Q1 Golden Bird, Q2 Gidbinn/Blade quest. */
  public static final int RECORD = 2;
  public static final String GIDBINN = "g33";
  /** Native Ormus reward item (' nir'), represented as the resource code "rin". */
  public static final String ORMUS_REWARD = "rin";

  public static final int GIDBINN_ALTAR_OBJECT = 251;
  public static final int GIDBINN_DECOY_OBJECT = 252;

  public static final int MESSAGE_HRATLI_INIT = 571;
  public static final int MESSAGE_ORMUS_TURN_IN = 587;
  public static final int MESSAGE_ORMUS_REWARD = 593;
  public static final int MESSAGE_ASHEARA_REWARD = 589;

  public static short start(short record) {
    return canProgress(record) ? NativeQuestRecord.set(record, NativeQuestRecord.STARTED) : record;
  }

  public static short leaveTown(short record) {
    return canProgress(record) ? NativeQuestRecord.set(record, NativeQuestRecord.LEFT_TOWN) : record;
  }

  public static short enterArea(short record) {
    return canProgress(record) ? NativeQuestRecord.set(record, NativeQuestRecord.ENTERED_AREA) : record;
  }

  public static short markGidbinnPicked(short record) {
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM1);
  }

  public static short markBroughtToOrmus(short record) {
    if (!canProgress(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM2);
    return NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
  }

  public static short markAshearaReward(short record) {
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM3);
  }

  public static short markOrmusReward(short record) {
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM4);
  }

  public static short completeIfBothRewards(short record) {
    if (!NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM3)
        || !NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM4)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    return NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
  }

  public static boolean canProgress(short record) {
    return !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }
}
