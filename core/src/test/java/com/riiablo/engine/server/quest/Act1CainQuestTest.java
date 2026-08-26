package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act1CainQuestTest {
  @Test
  void transitionsNativeAreaAndPortalFlags() {
    short record = Act1CainQuest.acquireScroll((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.STARTED));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA));
    record = Act1CainQuest.openTristramPortal(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA));
  }

  @Test
  void validatesNativeFiveStoneOrder() {
    int[] order = {17, 21, 18, 22, 19};
    assertTrue(Act1CainQuest.isExpectedStone(17, order, 0));
    assertFalse(Act1CainQuest.isExpectedStone(18, order, 0));
    assertTrue(Act1CainQuest.isExpectedStone(19, order, 4));
    assertEquals(0, Act1CainQuest.normalizeOrder(new int[] {17, 18}).length);
    assertEquals(5, Act1CainQuest.normalizeOrder(order).length);
  }

  @Test
  void releaseAndClaimUsePendingThenGranted() {
    short record = Act1CainQuest.releaseCain((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    record = Act1CainQuest.claimReward(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
  }
}
