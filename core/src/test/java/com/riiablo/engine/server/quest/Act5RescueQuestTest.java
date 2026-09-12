package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act5RescueQuestTest {
  @Test
  void rescueCompletionRequiresQualKehkClaim() {
    short record = Act5RescueQuest.complete(Act5RescueQuest.start((short) 0));
    assertTrue(Act5RescueQuest.canClaimReward(record));
    record = Act5RescueQuest.claimReward(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(Act5RescueQuest.canClaimReward(record));
  }
}
