package com.riiablo.engine.client;

import com.riiablo.engine.server.quest.QuestRecords;
import java.util.function.IntUnaryOperator;

/** Tick-ordered records; questRevision is a content hash, never an ordering counter. */
public final class AuthoritativeQuestState {
  private AuthoritativeSnapshotTimeline timeline = new AuthoritativeSnapshotTimeline();
  private Snapshot snapshot;

  public Snapshot snapshot() { return snapshot; }

  boolean commit(long tick, long serverTimeMillis, int difficulty, long revision,
      int count, IntUnaryOperator value) {
    if (count != QuestRecords.SNAPSHOT_SIZE || value == null) return false;
    int[] records = new int[count];
    for (int i = 0; i < count; i++) {
      records[i] = value.applyAsInt(i);
      if (records[i] < 0 || records[i] > 0xffff) return false;
    }
    if (!timeline.accept(tick, serverTimeMillis)) return false;
    snapshot = new Snapshot(tick, serverTimeMillis, difficulty, revision, records);
    return true;
  }

  void clear() { snapshot = null; timeline = new AuthoritativeSnapshotTimeline(); }

  public static final class Snapshot {
    public final long tick, serverTimeMillis, revision;
    public final int difficulty;
    private final int[] records;
    private Snapshot(long tick, long serverTimeMillis, int difficulty, long revision, int[] records) {
      this.tick = tick; this.serverTimeMillis = serverTimeMillis;
      this.difficulty = difficulty; this.revision = revision; this.records = records;
    }
    public int length() { return records.length; }
    public int record(int index) { return records[index]; }
    public int[] records() { return records.clone(); }
  }
}