package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act1AndarielQuestTest {
  @Test
  void progressesAndClaimsAtWarriv() {
    short record = Act1AndarielQuest.enterCatacombs((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.STARTED));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA));

    record = Act1AndarielQuest.completePending(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    record = Act1AndarielQuest.claimReward(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
  }
}
