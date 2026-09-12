package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act5BaalQuestTest {
  @Test
  void baalKillCompletesImmediatelyAndIsIdempotent() {
    short record = Act5BaalQuest.enterArea((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA));
    record = Act5BaalQuest.complete(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    assertEquals(record, Act5BaalQuest.complete(record));
  }
}
