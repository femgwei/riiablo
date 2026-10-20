package com.riiablo.engine.server.quest;

import com.riiablo.save.CharData;

/** Stable current-difficulty quest snapshot shared by entity and request responses. */
public final class QuestSnapshot {
  public static final int RECORDS_PER_ACT = QuestRecords.PER_ACT;

  private QuestSnapshot() {}

  public static short[] records(CharData data) {
    return QuestRecords.snapshot(data == null ? null : data::getQuests);
  }

  public static boolean apply(CharData data, int count, java.util.function.IntUnaryOperator value) {
    return data != null && QuestRecords.apply(data::getQuests, count, value);
  }

  public static long revision(short[] records) {
    return QuestRecords.revision(records);
  }
}
