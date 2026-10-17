package com.riiablo.engine.server.quest;

/** Native Act II quest 1 ({@code A2Q1.cpp}) save-record transitions. */
public final class Act2RadamentQuest {
  private Act2RadamentQuest() {}

  /** Act II record zero is the introductory gossip quest. */
  public static final int RECORD = 1;

  public static final int MESSAGE_INIT = 304;
  public static final int MESSAGE_EARLY = 310;
  public static final int MESSAGE_SEWERS = 317;
  public static final int MESSAGE_REWARD = 334;
  public static final String SKILL_BOOK_CODE = "ass";

  public static short start(short record) {
    return canProgress(record)
        ? NativeQuestRecord.set(record, NativeQuestRecord.STARTED) : record;
  }

  public static short leaveTown(short record) {
    return canProgress(record)
        ? NativeQuestRecord.set(record, NativeQuestRecord.LEFT_TOWN) : record;
  }

  public static short enterSewers(short record) {
    return canProgress(record)
        ? NativeQuestRecord.set(record, NativeQuestRecord.ENTERED_AREA) : record;
  }

  /** Credits a player in Radament's room, or an eligible Act II party member. */
  public static short completeObjective(short record) {
    if (!canProgress(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM1);
  }

  /** Mirrors A2Q1's global completion pass for players who did not earn credit. */
  public static short markCompletedNow(short record) {
    return canProgress(record)
        ? NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW) : record;
  }

  public static boolean canClaimReward(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)
        && NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  /** Atma acknowledges the kill; the skill point itself comes from the dropped book. */
  public static short claimReward(short record) {
    if (!canClaimReward(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    return NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
  }

  public static boolean needsSkillBook(short record, boolean alreadyHasBook) {
    return NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM1) && !alreadyHasBook;
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  /** Selects Atma's current A2Q1 speech from the native message chains. */
  public static int selectAtmaMessage(short record) {
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)) return -1;
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)) return MESSAGE_REWARD;
    if (NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA)
        || NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN)) return MESSAGE_SEWERS;
    if (NativeQuestRecord.has(record, NativeQuestRecord.STARTED)) return MESSAGE_EARLY;
    return MESSAGE_INIT;
  }

  private static boolean canProgress(short record) {
    return !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
  }
}
