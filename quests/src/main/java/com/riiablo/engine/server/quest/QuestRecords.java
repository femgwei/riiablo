package com.riiablo.engine.server.quest;

import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

/** Current-difficulty record layout shared by saves, replicas and reconnects.
 * Session state (encounters, portals, RNG) deliberately does not live here. */
public final class QuestRecords {
  public static final int ACTS = 5;
  public static final int PER_ACT = 8;
  public static final int SNAPSHOT_SIZE = ACTS * PER_ACT;

  private QuestRecords() {}

  public static short[] snapshot(IntFunction<short[]> records) {
    short[] snapshot = new short[SNAPSHOT_SIZE];
    if (records == null) return snapshot;
    for (int act = 0; act < ACTS; act++) {
      short[] source = records.apply(act);
      if (source != null) System.arraycopy(source, 0, snapshot, act * PER_ACT,
          Math.min(source.length, PER_ACT));
    }
    return snapshot;
  }

  /** Stage every value before committing; malformed/partial packets cannot mix acts. */
  public static boolean apply(IntFunction<short[]> records, int count,
      IntUnaryOperator value) {
    if (records == null || value == null || count != SNAPSHOT_SIZE) return false;
    short[][] targets = new short[ACTS][];
    short[] staged = new short[SNAPSHOT_SIZE];
    for (int act = 0; act < ACTS; act++) {
      targets[act] = records.apply(act);
      if (targets[act] == null || targets[act].length < PER_ACT) return false;
    }
    for (int i = 0; i < count; i++) {
      int entry = value.applyAsInt(i);
      if (entry < 0 || entry > 0xFFFF) return false;
      staged[i] = (short) entry;
    }
    for (int act = 0; act < ACTS; act++) {
      System.arraycopy(staged, act * PER_ACT, targets[act], 0, PER_ACT);
    }
    return true;
  }

  public static long revision(short[] records) {
    long hash = 1469598103934665603L;
    if (records == null) return hash;
    for (int i = 0; i < records.length; i++) {
      hash ^= (records[i] & 0xFFFFL) + ((long) i << 16);
      hash *= 1099511628211L;
    }
    return hash;
  }
}
