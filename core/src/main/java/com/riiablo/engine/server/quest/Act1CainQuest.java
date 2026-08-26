package com.riiablo.engine.server.quest;

import com.riiablo.item.Quality;

/** Native save-record transitions and stone-sequence rules for Act 1 Quest 4. */
public final class Act1CainQuest {
  public static final int RECORD = 4;
  public static final String BARK_SCROLL_CODE = "skb";
  public static final String DECIPHERED_SCROLL_CODE = "dkb";
  public static final int MESSAGE_DECIPHER_SCROLL = 112;
  public static final int MESSAGE_REWARD = 118;
  public static final int MESSAGE_CAIN_TOWN = 125;
  public static final String REWARD_RING_CODE = "rin";
  public static final int FIRST_STONE_OBJECT = 17;
  public static final int LAST_STONE_OBJECT = 21;
  public static final int STONE_COUNT = 5;

  private Act1CainQuest() {}

  /** Native A1Q4 reward parameters selected from the character difficulty. */
  public static RewardSpec rewardSpec(int difficulty) {
    switch (difficulty) {
      case 0: return new RewardSpec(REWARD_RING_CODE, 7, Quality.MAGIC);
      case 1: return new RewardSpec(REWARD_RING_CODE, 30, Quality.RARE);
      case 2: return new RewardSpec(REWARD_RING_CODE, 60, Quality.RARE);
      default: throw new IllegalArgumentException("Unsupported difficulty: " + difficulty);
    }
  }

  public static final class RewardSpec {
    public final String code;
    public final int itemLevel;
    public final Quality quality;

    private RewardSpec(String code, int itemLevel, Quality quality) {
      this.code = code;
      this.itemLevel = itemLevel;
      this.quality = quality;
    }
  }

  public static short start(short record) {
    if (isFinished(record)) return record;
    return NativeQuestRecord.set(record, NativeQuestRecord.STARTED);
  }

  public static short leaveTown(short record) {
    if (isFinished(record)) return record;
    record = start(record);
    return NativeQuestRecord.set(record, NativeQuestRecord.LEFT_TOWN);
  }

  public static short enterDarkWood(short record) {
    if (isFinished(record)) return record;
    return leaveTown(record);
  }

  public static short acquireScroll(short record) {
    if (isFinished(record)) return record;
    return leaveTown(record);
  }

  public static boolean canDecipherScroll(short record, boolean hasBarkScroll,
      boolean hasDecipheredScroll) {
    return !isFinished(record) && hasBarkScroll && !hasDecipheredScroll;
  }

  public static short openTristramPortal(short record) {
    if (isFinished(record)) return record;
    return NativeQuestRecord.set(record, NativeQuestRecord.ENTERED_AREA);
  }

  public static short releaseCain(short record) {
    if (isFinished(record)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    return NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
  }

  public static short claimReward(short record) {
    if (!NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    return NativeQuestRecord.clear(record, NativeQuestRecord.REWARD_PENDING);
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  /** Returns the next expected stone in a native five-stone order. */
  public static boolean isExpectedStone(int objectId, int[] order, int operated) {
    if (order == null || operated < 0 || operated >= order.length) return false;
    return objectId == order[operated]
        && objectId >= FIRST_STONE_OBJECT && objectId <= LAST_STONE_OBJECT;
  }

  public static int[] normalizeOrder(int[] order) {
    if (order == null || order.length != STONE_COUNT) return new int[0];
    int[] copy = order.clone();
    int seen = 0;
    for (int id : copy) {
      if (id < FIRST_STONE_OBJECT || id > LAST_STONE_OBJECT) return new int[0];
      int bit = 1 << (id - FIRST_STONE_OBJECT);
      if ((seen & bit) != 0) return new int[0];
      seen |= bit;
    }
    return copy;
  }
}
