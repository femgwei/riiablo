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

  @Test
  void usesNativeChaosSealRange() {
    assertTrue(Act4DiabloQuest.FIRST_SEAL == 392);
    assertTrue(Act4DiabloQuest.LAST_SEAL == 396);
    assertTrue(Act4DiabloQuest.CHAOS_SANCTUARY == 110);
    assertTrue(Act4DiabloQuest.isSealObject(392));
    assertTrue(Act4DiabloQuest.isSealObject(396));
    assertFalse(Act4DiabloQuest.isSealObject(391));
    assertFalse(Act4DiabloQuest.isSealObject(397));
  }

  @Test
  void enteringChaosSetsStartedAndEnteredAreaWithoutCompletingQuest() {
    short record = Act4DiabloQuest.enterArea((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.STARTED));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertTrue(Act4DiabloQuest.shouldRestoreCompletedState(
        Act4DiabloQuest.complete(record)));
  }
}
