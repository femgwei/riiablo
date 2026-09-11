package com.riiablo.engine.server.quest;

/** Native Act II quest 4 (Horazon's Tome / Arcane Sanctuary) record rules. */
public final class Act2HorazonTomeQuest {
  private Act2HorazonTomeQuest() {}

  /** Act II records: Q0 gossip, Q1..Q3, then A2Q4. */
  public static final int RECORD = 4;

  /** OBJECTS_OperateFunction42_SanctuaryTome completion transition. */
  public static short completeObjective(short record) {
    if (NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED)
        || NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING)) return record;
    record = NativeQuestRecord.set(record, NativeQuestRecord.PRIMARY_GOAL_DONE);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_PENDING);
    record = NativeQuestRecord.set(record, NativeQuestRecord.REWARD_GRANTED);
    record = NativeQuestRecord.resetIntermediate(record);
    record = NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM4);
    return NativeQuestRecord.set(record, NativeQuestRecord.CUSTOM3);
  }

  public static boolean isFinished(short record) {
    return NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED);
  }
}
