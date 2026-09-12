package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act4IzualQuestTest {
  @Test
  void killCreatesPendingRewardAndTyraelClaimIsIdempotent() {
    short record = Act4IzualQuest.completeObjective(Act4IzualQuest.start((short) 0));
    assertTrue(Act4IzualQuest.canClaimReward(record));
    record = Act4IzualQuest.claimReward(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    assertTrue(record == Act4IzualQuest.claimReward(record));
  }
}
