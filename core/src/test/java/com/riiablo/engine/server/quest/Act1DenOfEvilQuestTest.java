package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act1DenOfEvilQuestTest {
  @Test
  void progressesFromAkaraToDenObjective() {
    short record = Act1DenOfEvilQuest.start((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.STARTED));

    record = Act1DenOfEvilQuest.leaveTown(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN));

    record = Act1DenOfEvilQuest.enterDen(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA));

    record = Act1DenOfEvilQuest.completeObjective(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_NOW));
    assertTrue(Act1DenOfEvilQuest.canClaimReward(record));
  }

  @Test
  void grantsRewardExactlyOnceAndKeepsCompletionBits() {
    short pending = Act1DenOfEvilQuest.completeObjective((short) 0);
    short claimed = Act1DenOfEvilQuest.claimReward(pending);
    assertTrue(NativeQuestRecord.has(claimed, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(claimed, NativeQuestRecord.REWARD_PENDING));
    assertFalse(NativeQuestRecord.has(claimed, NativeQuestRecord.STARTED));
    assertTrue(NativeQuestRecord.has(claimed, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(claimed, NativeQuestRecord.COMPLETED_NOW));
    assertEquals(claimed, Act1DenOfEvilQuest.claimReward(claimed));
  }

  @Test
  void completedQuestCannotBeRestarted() {
    short completed = NativeQuestRecord.set((short) 0, NativeQuestRecord.REWARD_GRANTED);
    assertEquals(completed, Act1DenOfEvilQuest.start(completed));
    assertEquals(completed, Act1DenOfEvilQuest.enterDen(completed));
    assertEquals(completed, Act1DenOfEvilQuest.completeObjective(completed));
  }
}
