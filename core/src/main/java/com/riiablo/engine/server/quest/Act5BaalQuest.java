package com.riiablo.engine.server.quest;

import com.d2moo.common.drlg.D2LevelIds;

/** Native D2MOO A5Q6 Eve of Destruction state transitions. */
public final class Act5BaalQuest {
  private Act5BaalQuest() {}

  public static final int RECORD = 6;
  public static final int WORLDSTONE_KEEP_1 = D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV1;
  public static final int THRONE_OF_DESTRUCTION = D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV2;
  public static final int WORLDSTONE_CHAMBER = D2LevelIds.LEVEL_WORLDSTONECHAMBER;
  public static final int MESSAGE_TYRAEL = 20175;
  public static final int BAAL_CLASS = 544;

  public static short start(short record) {
    return isFinished(record) ? record : NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  public static short enterArea(short record) {
    return NativeQuestRecord.set(start(record), NativeQuestRecord.ENTERED_AREA);
  }

  /** D2MOO grants A5Q6 immediately when Baal dies; there is no pending NPC turn-in. */
  public static short complete(short record) {
    if (isFinished(record)) return record;
    record = NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    return NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }
}
