package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act5ShenkQuestTest {
  @Test
  void shenkCompletionRequiresLarzukClaim() {
    short record = Act5ShenkQuest.complete(Act5ShenkQuest.start((short) 0));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(Act5ShenkQuest.canClaimReward(record));
    record = Act5ShenkQuest.claimReward(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    assertFalse(Act5ShenkQuest.canClaimReward(record));
  }
}
