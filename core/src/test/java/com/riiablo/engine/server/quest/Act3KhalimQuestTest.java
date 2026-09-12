package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act3KhalimQuestTest {
  @Test
  void usesNativeResourceCodes() {
    assertEquals("qf1", Act3KhalimQuest.KHALIM_FLAIL);
    assertEquals("qey", Act3KhalimQuest.KHALIM_EYE);
    assertEquals("qhr", Act3KhalimQuest.KHALIM_HEART);
    assertEquals("qbr", Act3KhalimQuest.KHALIM_BRAIN);
    assertEquals("qf2", Act3KhalimQuest.KHALIM_WILL);
  }

  @Test
  void recordsEachPartWithoutMarkingReward() {
    short record = Act3KhalimQuest.start((short) 0);
    record = Act3KhalimQuest.markPicked(record, Act3KhalimQuest.KHALIM_FLAIL);
    record = Act3KhalimQuest.markPicked(record, Act3KhalimQuest.KHALIM_HEART);
    record = Act3KhalimQuest.markPicked(record, Act3KhalimQuest.KHALIM_BRAIN);
    record = Act3KhalimQuest.markPicked(record, Act3KhalimQuest.KHALIM_EYE);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.STARTED));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA));
  }
}
