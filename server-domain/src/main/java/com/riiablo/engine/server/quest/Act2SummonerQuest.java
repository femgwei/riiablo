package com.riiablo.engine.server.quest;

import com.riiablo.engine.server.monster.MonsterType;

/** Native Act II quest 5 (The Summoner) record transitions. */
public final class Act2SummonerQuest {
  private Act2SummonerQuest() {}

  /** Act II records: Q0 gossip, Q1..Q4, then A2Q5. */
  public static final int RECORD = 5;
  public static final int FIRST_REWARD_MESSAGE = 419;
  public static final int LAST_REWARD_MESSAGE = 429;

  /** D2MOO A2Q5 active-filter NPC whitelist. */
  public static boolean isRewardNpc(int npcType) {
    switch (npcType) {
      case MonsterType.WARRIV2:
      case MonsterType.ATMA:
      case MonsterType.DROGNAN:
      case MonsterType.FARA:
      case MonsterType.ELZIX:
      case MonsterType.GEGLASH:
      case MonsterType.JERHYN:
      case MonsterType.LYSANDER:
      case MonsterType.MESHIF1:
      case MonsterType.DECKARDCAIN_ACT2:
        return true;
      default:
        return false;
    }
  }

  public static boolean isRewardMessage(int messageIndex) {
    return messageIndex >= FIRST_REWARD_MESSAGE && messageIndex <= LAST_REWARD_MESSAGE;
  }

  public static short completeObjective(short record) {
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    return NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
  }

  /** Mirrors the native NPC scroll-message reward acknowledgement. */
  public static short claimReward(short record) {
    if (!NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    return NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
  }

  public static short markCompletedNow(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        ? record : NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }
}
