package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act4HellforgeQuestTest {
  @Test
  void hellforgeCompletionIsPendingUntilRewardClaim() {
    short record = Act4HellforgeQuest.complete(Act4HellforgeQuest.start((short) 0));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    record = Act4HellforgeQuest.claimReward(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
  }

  @Test
  void rewardClaimRequiresPendingAndIsIdempotent() {
    short started = Act4HellforgeQuest.start((short) 0);
    assertFalse(Act4HellforgeQuest.canClaimReward(started));
    assertTrue(Act4HellforgeQuest.claimReward(started) == started);

    short pending = Act4HellforgeQuest.complete(started);
    assertTrue(Act4HellforgeQuest.canClaimReward(pending));
    short granted = Act4HellforgeQuest.claimReward(pending);
    assertTrue(NativeQuestRecord.has(granted, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(Act4HellforgeQuest.canClaimReward(granted));
    assertTrue(Act4HellforgeQuest.claimReward(granted) == granted);
  }

  @Test
  void cainMessageSelectionUsesNativeIds() {
    assertTrue(Act4HellforgeQuest.MESSAGE_CAIN_INIT_NO_STONE == 678);
    assertTrue(Act4HellforgeQuest.MESSAGE_CAIN_INIT_HAS_STONE == 679);
    assertTrue(Act4HellforgeQuest.MESSAGE_CAIN_REWARD == 680);
    assertTrue(Act4HellforgeQuest.selectCainMessage((short) 0, false)
        == Act4HellforgeQuest.MESSAGE_CAIN_INIT_NO_STONE);
    short pending = Act4HellforgeQuest.complete((short) 0);
    assertTrue(Act4HellforgeQuest.selectCainMessage(pending, false)
        == Act4HellforgeQuest.MESSAGE_CAIN_REWARD);
  }
}
