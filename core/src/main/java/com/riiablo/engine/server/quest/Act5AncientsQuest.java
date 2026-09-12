package com.riiablo.engine.server.quest;

import com.d2moo.common.drlg.D2LevelIds;

/** Native D2MOO A5Q5 Rite of Passage state transitions. */
public final class Act5AncientsQuest {
  private Act5AncientsQuest() {}

  public static final int RECORD = 5;
  /** D2MOO's quest room. Rocky Summit is retained alongside the project's Arreat Summit alias. */
  public static final int ROCKY_SUMMIT = D2LevelIds.LEVEL_ROCKYSUMMIT;
  public static final int ARREAT_SUMMIT = D2LevelIds.LEVEL_ARREATSUMMIT;
  public static final int ANCIENT_WAY = D2LevelIds.LEVEL_ANCIENTSWAY;
  public static final int FIRST_ANCIENT_STATUE = 474;
  public static final int LAST_ANCIENT_STATUE = 476;
  public static final int MESSAGE_QUAL_KEHK_START = 20153;
  public static final int MESSAGE_ANCIENTS_ACTIVATE = 20002;
  public static final int MESSAGE_ENTER_AREA = 20169;
  public static final int SUPERUNIQUE_ANCIENT_1 = 43;
  public static final int SUPERUNIQUE_ANCIENT_2 = 44;
  public static final int SUPERUNIQUE_ANCIENT_3 = 45;

  public static int requiredLevel(int difficulty) {
    return 20 * (Math.max(0, Math.min(2, difficulty)) + 1);
  }

  public static short start(short record) {
    return isFinished(record) ? record : NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  public static short enterArea(short record) {
    return NativeQuestRecord.set(start(record), NativeQuestRecord.ENTERED_AREA);
  }

  /** A5Q5 grants experience and marks the quest complete immediately on the third death. */
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
