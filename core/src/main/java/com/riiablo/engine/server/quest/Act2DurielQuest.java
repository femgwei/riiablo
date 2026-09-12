package com.riiablo.engine.server.quest;

import com.d2moo.common.drlg.D2LevelIds;

/** Native A2Q6 record transitions from Duriel death through Meshif travel. */
public final class Act2DurielQuest {
  public static final int RECORD = 6;
  public static final int MESSAGE_TYRAEL_PORTAL = 302;
  public static final int MESSAGE_JERHYN_END = 442;
  public static final int MESSAGE_MESHIF_TRAVEL = 450;
  public static final int TYRAELS_DOOR = 153;

  private Act2DurielQuest() {}

  public static short markDurielKilled(short record) {
    record = repairLegacyOrificeFlags(record);
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN)
        || NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA)
        || NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM1)) return record;
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM1);
  }

  /** Tyrael's message 302 grants the primary goal and enables the town return. */
  public static short acceptTyraelPortal(short record) {
    record = repairLegacyOrificeFlags(record);
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN)
        || NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    return NativeQuestRecord.set(record, NativeQuestRecord.LEFT_TOWN);
  }

  public static short markCompletedNow(short record) {
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN)
        || NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA)) return record;
    return NativeQuestRecord.set(record, NativeQuestRecord.COMPLETED_NOW);
  }

  /** Jerhyn moves the native temporary LEFT_TOWN flag to ENTERED_AREA. */
  public static short acknowledgeJerhyn(short record) {
    if (!NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN)) return record;
    record = NativeQuestRecord.clear(record, NativeQuestRecord.LEFT_TOWN);
    return NativeQuestRecord.set(record, NativeQuestRecord.ENTERED_AREA);
  }

  /** Meshif is the actual A2Q6 reward turn-in and clears ENTERED_AREA. */
  public static short travelWithMeshif(short record) {
    if (!NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA)) return record;
    record = NativeQuestRecord.clear(record, NativeQuestRecord.ENTERED_AREA);
    return NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
  }

  /**
   * Returns whether the Duriel-room Tyrael door must be restored when a
   * saved character or a rebuilt map enters the lair.  D2Game keeps the door
   * open after Duriel has been killed; the transient ECS object is not part of
   * the D2S file, so the quest record is the authoritative reconstruction
   * signal.
   */
  public static boolean shouldRestoreTyraelDoor(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM1)
        || NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)
        || NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN)
        || NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }

  /**
   * Tyrael's return-to-town portal exists until the A2Q6 Meshif turn-in.  It
   * is a runtime Warp/visual pair, therefore it has to be rebuilt after a
   * reconnect or map regeneration while the temporary quest flags remain.
   */
  public static boolean shouldRestoreTownPortal(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN)
        || NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA);
  }

  public static boolean isAct2(int levelId) {
    return levelId >= D2LevelIds.LEVEL_LUTGHOLEIN
        && levelId < D2LevelIds.LEVEL_KURASTDOCKTOWN;
  }

  /** Older Java builds incorrectly set LEFT_TOWN when the staff was inserted.
   * Native A2Q6 only sets it after Tyrael, where it is always paired with the
   * primary-goal bit. Clear only that impossible legacy combination. */
  static short repairLegacyOrificeFlags(short record) {
    if (NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN)
        && !NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE)) {
      return NativeQuestRecord.clear(record, NativeQuestRecord.LEFT_TOWN);
    }
    return record;
  }
}
