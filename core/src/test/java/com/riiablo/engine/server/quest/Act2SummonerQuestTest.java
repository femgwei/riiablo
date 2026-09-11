package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act2SummonerQuestTest {
  @Test
  void summonerDeathCreatesPendingReward() {
    short record = Act2SummonerQuest.completeObjective((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
  }

  @Test
  void rewardAcknowledgementIsIdempotent() {
    short pending = Act2SummonerQuest.completeObjective((short) 0);
    short granted = Act2SummonerQuest.claimReward(pending);
    assertTrue(NativeQuestRecord.has(granted, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(granted, NativeQuestRecord.REWARD_PENDING));
    assertTrue(Act2SummonerQuest.claimReward(granted) == granted);
  }

  @Test
  void unrelatedAct2PlayersOnlyReceiveCompletedNow() {
    short record = Act2SummonerQuest.markCompletedNow((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_NOW));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
  }
}
