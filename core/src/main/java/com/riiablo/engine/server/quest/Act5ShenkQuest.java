package com.riiablo.engine.server.quest;

import com.d2moo.common.drlg.D2LevelIds;

/** Native D2MOO A5Q1 (Siege on Harrogath) state transitions. */
public final class Act5ShenkQuest {
  private Act5ShenkQuest() {}

  /** Act V record 0 is intro; record 1 is the first playable quest. */
  public static final int RECORD = 1;
  public static final int BLOODY_FOOTHILLS = D2LevelIds.LEVEL_BLOODYFOOTHILLS;
  public static final int MESSAGE_LARZUK_REWARD = 20090;
  public static final int SUPERUNIQUE_SIEGE_BOSS = 42;

  public static short start(short record) {
    return isFinished(record) ? record : NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
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

  /**
   * D2MOO's Shenk init path does not create the quest super-unique after the
   * primary goal has already been recorded.  This also covers a reconnect
   * while Larzuk's reward is still pending: the quest state, rather than the
   * transient monster entity id, is authoritative.
   */
  public static boolean shouldSpawnBoss(short record) {
    return !NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  public static short claimReward(short record) {
    if (!canClaimReward(record)) return record;
    record = NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM1);
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }
}
