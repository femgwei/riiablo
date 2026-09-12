package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act5BaalQuestTest {
  @Test
  void baalKillCompletesImmediatelyAndIsIdempotent() {
    short record = Act5BaalQuest.enterArea((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA));
    record = Act5BaalQuest.complete(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_NOW));
    assertEquals(record, Act5BaalQuest.complete(record));
  }

  @Test
  void onlyExpansionPlayersInNativeRewardAreasQualify() {
    assertTrue(Act5BaalQuest.canReceiveDirectReward(
        true, Act5BaalQuest.WORLDSTONE_CHAMBER));
    assertFalse(Act5BaalQuest.canReceiveDirectReward(
        true, Act5BaalQuest.THRONE_OF_DESTRUCTION));
    assertFalse(Act5BaalQuest.canReceiveDirectReward(
        false, Act5BaalQuest.WORLDSTONE_CHAMBER));
    assertTrue(Act5BaalQuest.canReceivePartyReward(true, Act5BaalQuest.WORLDSTONE_KEEP_1));
    assertFalse(Act5BaalQuest.canReceivePartyReward(
        true, com.d2moo.common.drlg.D2LevelIds.LEVEL_THEPANDEMONIUMFORTRESS));
  }

  @Test
  void pendingRewardIsNotConvertedAndObserversOnlyGetCompletedNow() {
    short pending = NativeQuestRecord.set((short) 0, NativeQuestRecord.REWARD_PENDING);
    assertEquals(pending, Act5BaalQuest.complete(pending));

    short observer = Act5BaalQuest.completeObserver((short) 0);
    assertTrue(NativeQuestRecord.has(observer, NativeQuestRecord.COMPLETED_NOW));
    assertFalse(NativeQuestRecord.has(observer, NativeQuestRecord.REWARD_GRANTED));

    short rewarded = Act5BaalQuest.complete((short) 0);
    assertEquals(rewarded, Act5BaalQuest.completeObserver(rewarded));
  }

  @Test
  void lastPortalRequiresChamberAndGrantedReward() {
    short granted = NativeQuestRecord.set((short) 0, NativeQuestRecord.REWARD_GRANTED);
    assertTrue(Act5BaalQuest.canUseLastPortal(granted, Act5BaalQuest.WORLDSTONE_CHAMBER));
    assertFalse(Act5BaalQuest.canUseLastPortal((short) 0,
        Act5BaalQuest.WORLDSTONE_CHAMBER));
    assertFalse(Act5BaalQuest.canUseLastPortal(granted, Act5BaalQuest.THRONE_OF_DESTRUCTION));
  }
}
