package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.item.Quality;
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
    int[] order = {17, 21, 18, 20, 19};
    assertTrue(Act1CainQuest.isExpectedStone(17, order, 0));
    assertFalse(Act1CainQuest.isExpectedStone(18, order, 0));
    assertTrue(Act1CainQuest.isExpectedStone(19, order, 4));
    assertEquals(0, Act1CainQuest.normalizeOrder(new int[] {17, 18}).length);
    assertEquals(5, Act1CainQuest.normalizeOrder(order).length);
    assertEquals(0, Act1CainQuest.normalizeOrder(new int[] {17, 18, 19, 20, 22}).length);
    assertEquals(0, Act1CainQuest.normalizeOrder(new int[] {17, 18, 19, 20, 20}).length);
  }

  @Test
  void onlyDeciphersAnOwnedBarkScrollBeforeCompletion() {
    assertTrue(Act1CainQuest.canDecipherScroll((short) 0, true, false));
    assertFalse(Act1CainQuest.canDecipherScroll((short) 0, false, false));
    assertFalse(Act1CainQuest.canDecipherScroll((short) 0, true, true));
    assertFalse(Act1CainQuest.canDecipherScroll(
        Act1CainQuest.releaseCain((short) 0), true, false));
  }

  @Test
  void releaseAndClaimUsePendingThenGranted() {
    short record = Act1CainQuest.releaseCain((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    record = Act1CainQuest.claimReward(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
  }

  @Test
  void usesNativeDifficultyRewardSpecifications() {
    Act1CainQuest.RewardSpec normal = Act1CainQuest.rewardSpec(Riiablo.NORMAL);
    assertEquals("rin", normal.code);
    assertEquals(7, normal.itemLevel);
    assertEquals(Quality.MAGIC, normal.quality);

    Act1CainQuest.RewardSpec nightmare = Act1CainQuest.rewardSpec(Riiablo.NIGHTMARE);
    assertEquals(30, nightmare.itemLevel);
    assertEquals(Quality.RARE, nightmare.quality);

    Act1CainQuest.RewardSpec hell = Act1CainQuest.rewardSpec(Riiablo.HELL);
    assertEquals(60, hell.itemLevel);
    assertEquals(Quality.RARE, hell.quality);
  }

  @Test
  void repeatedRewardMessageCannotClaimTwice() {
    short claimed = Act1CainQuest.claimReward(
        Act1CainQuest.claimReward(Act1CainQuest.releaseCain((short) 0)));
    assertTrue(NativeQuestRecord.has(claimed, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(claimed, NativeQuestRecord.REWARD_PENDING));
    assertEquals(claimed, Act1CainQuest.claimReward(claimed));
  }
}
