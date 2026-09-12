package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act5NihlathakQuestTest {
  @Test
  void areaEntryRequiresStartedAndRewardRemainsPendingUntilDrehya() {
    short record = Act5NihlathakQuest.enterArea((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.STARTED));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA));
    assertFalse(Act5NihlathakQuest.canClaimReward(record));

    record = Act5NihlathakQuest.complete(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(Act5NihlathakQuest.canClaimReward(record));

    record = Act5NihlathakQuest.claimReward(record);
    assertTrue(Act5NihlathakQuest.isFinished(record));
    assertFalse(Act5NihlathakQuest.canClaimReward(record));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM1));
  }

  @Test
  void completedQuestIsIdempotent() {
    short record = Act5NihlathakQuest.claimReward(
        Act5NihlathakQuest.complete(Act5NihlathakQuest.start((short) 0)));
    assertTrue(Act5NihlathakQuest.isFinished(record));
    assertTrue(Act5NihlathakQuest.claimReward(record) == record);
    assertTrue(Act5NihlathakQuest.start(record) == record);
  }
}
