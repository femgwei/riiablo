package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.riiablo.Riiablo;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class QuestSnapshotTest {
  @Test
  void flattensOnlyCurrentDifficultyAndProducesStableRevision() {
    CharData data = CharData.obtain().set(Riiablo.NORMAL, false, "Quest", Riiablo.AMAZON);
    data.getQuests(Riiablo.ACT1)[2] = (short) 0x4321;
    short[] first = QuestSnapshot.records(data);
    long revision = QuestSnapshot.revision(first);

    assertEquals(Riiablo.NUM_ACTS * QuestSnapshot.RECORDS_PER_ACT, first.length);
    assertEquals(0x4321, Short.toUnsignedInt(first[2]));
    assertEquals(revision, QuestSnapshot.revision(QuestSnapshot.records(data)));

    data.getQuests(Riiablo.ACT1)[2]++;
    assertNotEquals(revision, QuestSnapshot.revision(QuestSnapshot.records(data)));
  }
}
