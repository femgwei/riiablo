package com.riiablo.engine.server.quest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestRecordsTest {
  @Test void allActsUnsignedRoundTripAndStableRevision() {
    short[][] records = new short[5][8];
    assertTrue(QuestRecords.apply(a -> records[a], 40, i -> i == 39 ? 65535 : i));
    short[] snapshot = QuestRecords.snapshot(a -> records[a]);
    assertEquals(40, snapshot.length);
    assertEquals(65535, Short.toUnsignedInt(snapshot[39]));
    assertEquals(8, records[1][0]);
    long revision = QuestRecords.revision(snapshot);
    assertEquals(revision, QuestRecords.revision(QuestRecords.snapshot(a -> records[a])));
    records[4][7]--;
    assertNotEquals(revision, QuestRecords.revision(QuestRecords.snapshot(a -> records[a])));
  }

  @Test void partialOversizedAndMalformedSnapshotsNeverPartiallyMutate() {
    short[][] records = new short[5][8];
    records[0][1] = 77;
    short[] before = QuestRecords.snapshot(a -> records[a]);
    for (int count : new int[] {0, 1, 8, 39, 41}) {
      assertFalse(QuestRecords.apply(a -> records[a], count, i -> 42));
      assertArrayEquals(before, QuestRecords.snapshot(a -> records[a]));
    }
    for (int invalid : new int[] {-1, 65536}) {
      assertFalse(QuestRecords.apply(a -> records[a], 40, i -> i == 39 ? invalid : 42));
      assertArrayEquals(before, QuestRecords.snapshot(a -> records[a]));
    }
    assertFalse(QuestRecords.apply(a -> a == 4 ? null : records[a], 40, i -> 42));
    assertArrayEquals(before, QuestRecords.snapshot(a -> records[a]));
  }
}
