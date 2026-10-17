package com.riiablo.engine.server.quest;

import com.d2moo.common.drlg.D2LevelIds;

/** Native D2MOO A3Q6 (The Guardian / Mephisto) record transitions. */
public final class Act3MephistoQuest {
  private Act3MephistoQuest() {}

  /** Act III record index: Q0 gossip, A3Q1..A3Q6. */
  public static final int RECORD = 6;
  public static final int MEPHISTO_LEVEL = D2LevelIds.LEVEL_DURANCEOFHATELEVEL3;
  public static final int MEPHISTO_BRIDGE = 341;
  public static final int HELL_GATE_PORTAL = 342;
  public static final int DESTINATION_ACT4 = D2LevelIds.LEVEL_THEPANDEMONIUMFORTRESS;

  public static short start(short record) {
    return isFinished(record) ? record : NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  /** D2MOO completes A3Q6 immediately when Mephisto dies. */
  public static short complete(short record) {
    if (isFinished(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM7);
  }

  /** Non-qualifying players receive the native game-wide completion marker. */
  public static short completeObserver(short record) {
    return isFinished(record) ? record
        : NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  public static boolean shouldOpenExit(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM7);
  }

  public static boolean isAct3Level(int levelId) {
    return levelId >= D2LevelIds.LEVEL_SPIDERFOREST
        && levelId <= MEPHISTO_LEVEL;
  }
}
