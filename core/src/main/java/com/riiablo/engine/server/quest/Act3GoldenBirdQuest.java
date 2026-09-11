package com.riiablo.engine.server.quest;

import com.riiablo.engine.server.monster.MonsterType;

/** Native Act III Golden Bird (D2MOO A3Q4) record transitions. */
public final class Act3GoldenBirdQuest {
  private Act3GoldenBirdQuest() {}

  /** Act III records: Q0 gossip, then A3Q1 in the Java quest table. */
  public static final int RECORD = 1;

  /** Resource-table item codes (D2MOO fourcc: ' 43j', ' 43g', ' zyx'). */
  public static final String JADE_FIGURINE = "j34";
  public static final String GOLDEN_BIRD = "g34";
  public static final String POTION_OF_LIFE = "xyz";

  public static final int MESSAGE_CAIN_INIT = 527;
  public static final int MESSAGE_CAIN_ENTERED_AREA = 531;
  public static final int MESSAGE_MESHIF_EXCHANGE = 529;
  public static final int MESSAGE_ALKOR_RECEIVE = 534;
  public static final int MESSAGE_ALKOR_REWARD = 538;

  /** Jade is held by the player; this is not a terminal quest state. */
  public static short markJadePicked(short record) {
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM2);
  }

  public static short start(short record) {
    return canProgress(record) ? NativeQuestRecord.set(record, NativeQuestRecord.STARTED) : record;
  }

  public static short enterArea(short record) {
    return canProgress(record) ? NativeQuestRecord.set(record, NativeQuestRecord.ENTERED_AREA) : record;
  }

  /** Cain/Meshif exchange leaves the player with the Golden Bird. */
  public static short exchangeForGoldenBird(short record) {
    return canProgress(record) ? NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM3) : record;
  }

  /** Alkor has accepted the bird; the reward dialogue is now available. */
  public static short acceptAtAlkor(short record) {
    if (!canProgress(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    return NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
  }

  /** Alkor reward is one-shot and must be committed before creating the item. */
  public static short claimReward(short record) {
    if (!NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)) return record;
    record = NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM1);
    return record;
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  public static boolean canProgress(short record) {
    return !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        && !NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING);
  }

  /** NPC class ids used by D2MOO's A3Q4 message table. */
  public static boolean isCain(int npcType) { return npcType == MonsterType.CAIN3; }
  public static boolean isMeshif(int npcType) { return npcType == MonsterType.MESHIF2; }
  public static boolean isAlkor(int npcType) { return npcType == MonsterType.ALKOR; }
}
