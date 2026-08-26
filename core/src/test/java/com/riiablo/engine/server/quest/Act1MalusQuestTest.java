package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act1MalusQuestTest {
  @Test
  void requiresLevelEightAndTracksObjectAndPickup() {
    short record = 0;
    assertFalse(Act1MalusQuest.canOpenMalus(record, 7));
    assertTrue(Act1MalusQuest.canOpenMalus(record, 8));
    record = Act1MalusQuest.leaveTown(record);
    record = Act1MalusQuest.markMalusPickedUp(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.STARTED));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM2));
  }

  @Test
  void turnInSetsPendingAndClaimMatchesNativeRewardFlags() {
    short record = Act1MalusQuest.markMalusPickedUp((short) 0);
    assertTrue(Act1MalusQuest.canTurnIn(record, 8, true));
    record = Act1MalusQuest.completeObjective(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    record = Act1MalusQuest.claimReward(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    // ACT1Q3_SetRewardGranted does not reset the intermediate record bits.
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.STARTED));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM2));
  }

  @Test
  void selectsCharsiSpeechOnlyWhenMalusIsPresent() {
    assertEquals(Act1MalusQuest.MESSAGE_INIT,
        Act1MalusQuest.selectCharsiMessage((short) 0, 1, false));
    assertEquals(Act1MalusQuest.MESSAGE_INIT,
        Act1MalusQuest.selectCharsiMessage((short) 0, 8, false));
    short record = Act1MalusQuest.markMalusPickedUp((short) 0);
    assertEquals(Act1MalusQuest.MESSAGE_MALUS,
        Act1MalusQuest.selectCharsiMessage(record, 8, true));
  }

  @Test
  void nativeRewardEventIsRequiredAfterTurnIn() {
    short pending = Act1MalusQuest.completeObjective(
        Act1MalusQuest.markMalusPickedUp((short) 0));
    assertTrue(NativeQuestRecord.has(pending, NativeQuestRecord.REWARD_PENDING));
    assertFalse(Act1MalusQuest.isRewarded(pending));
  }
}
