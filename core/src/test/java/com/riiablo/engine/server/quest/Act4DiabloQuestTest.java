package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act4DiabloQuestTest {
  @Test
  void chaosCompletionIsPendingUntilClaimed() {
    short record = Act4DiabloQuest.complete(Act4DiabloQuest.start((short) 0));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    record = Act4DiabloQuest.claimCompletion(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    assertTrue(record == Act4DiabloQuest.claimCompletion(record));
  }
}
