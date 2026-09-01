package com.riiablo.engine.server.quest;

import com.riiablo.Riiablo;
import com.riiablo.save.CharData;

/** Stable current-difficulty quest snapshot shared by entity and request responses. */
public final class QuestSnapshot {
  public static final int RECORDS_PER_ACT = 8;

  private QuestSnapshot() {}

  public static short[] records(CharData data) {
    short[] snapshot = new short[Riiablo.NUM_ACTS * RECORDS_PER_ACT];
    if (data == null) return snapshot;
    int index = 0;
    for (int act = 0; act < Riiablo.NUM_ACTS; act++) {
      short[] records = data.getQuests(act);
      for (int i = 0; i < RECORDS_PER_ACT; i++) snapshot[index++] = records[i];
    }
    return snapshot;
  }

  public static long revision(short[] records) {
    long revision = 1469598103934665603L;
    if (records == null) return revision;
    for (int i = 0; i < records.length; i++) {
      revision ^= (records[i] & 0xFFFFL) + ((long) i << 16);
      revision *= 1099511628211L;
    }
    return revision;
  }
}
