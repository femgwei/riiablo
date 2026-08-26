package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act1BloodRavenQuestTest {
  @Test
  void followsNativeStartAreaAndCompletionFlags() {
    short record = Act1BloodRavenQuest.leaveTown((short) 0);
    assertEquals(0, Short.toUnsignedInt(record));

    record = Act1BloodRavenQuest.start(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.STARTED));
    record = Act1BloodRavenQuest.leaveTown(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN));
    record = Act1BloodRavenQuest.enterBurialGrounds(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA));

    record = Act1BloodRavenQuest.completeObjective(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_NOW));
  }

  @Test
  void commitsRewardOnlyOnceAfterConfirmation() {
    short pending = Act1BloodRavenQuest.completeObjective((short) 0);
    short granted = Act1BloodRavenQuest.claimReward(pending);
    assertTrue(NativeQuestRecord.has(granted, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(granted, NativeQuestRecord.REWARD_PENDING));
    assertEquals(granted, Act1BloodRavenQuest.claimReward(granted));
  }

  @Test
  void selectsNativeKashyaMessages() {
    short record = 0;
    assertEquals(81, Act1BloodRavenQuest.selectKashyaMessage(record));
    assertEquals("kashya_act1_q2_init", Act1BloodRavenQuest.getKashyaSpeech(81));
    record = Act1BloodRavenQuest.start(record);
    assertEquals(82, Act1BloodRavenQuest.selectKashyaMessage(record));
    record = Act1BloodRavenQuest.leaveTown(record);
    assertEquals(87, Act1BloodRavenQuest.selectKashyaMessage(record));
    record = Act1BloodRavenQuest.completeObjective(record);
    assertEquals(92, Act1BloodRavenQuest.selectKashyaMessage(record));
    assertEquals(-1, Act1BloodRavenQuest.selectKashyaMessage(
        Act1BloodRavenQuest.claimReward(record)));
  }

  @Test
  void recordsGameCompletionWithoutGrantingRemoteReward() {
    short record = Act1BloodRavenQuest.markCompletedNow((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_NOW));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
  }
}
