package com.riiablo.engine.server.quest;

import com.d2moo.common.drlg.D2LevelIds;

/** Native D2MOO A5Q3 Prison of Ice state transitions. */
public final class Act5PrisonQuest {
  private Act5PrisonQuest() {}

  public static final int RECORD = 3;
  public static final int FROZEN_RIVER = D2LevelIds.LEVEL_FROZENRIVER;
  public static final int FROZEN_ANYA_OBJECT = 558;
  /** FourCC ' ice' is represented by the resource code eci. */
  public static final String DEFROST_POTION = "eci";
  public static final int MESSAGE_MALAH_INIT = 20116;
  public static final int MESSAGE_MALAH_REWARD = 20122;

  public static short start(short record) {
    return isFinished(record) ? record : NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  public static short markPotion(short record) {
    return NativeQuestRecord.set(start(record), NativeQuestRecord.CUSTOM3);
  }

  public static boolean hasPotion(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM3);
  }

  public static short complete(short record) {
    if (isFinished(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
    return NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  public static boolean canClaimReward(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)
        && NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  public static short claimReward(short record) {
    if (!canClaimReward(record)) return record;
    record = NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM4);
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }
}
