package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act2DurielQuestTest {
  @Test
  void nativeTyraelJerhynMeshifChainUsesTemporaryFlags() {
    short record = Act2DurielQuest.markDurielKilled((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM1));

    record = Act2DurielQuest.acceptTyraelPortal(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));

    record = Act2DurielQuest.acknowledgeJerhyn(record);
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA));

    record = Act2DurielQuest.travelWithMeshif(record);
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertEquals(record, Act2DurielQuest.travelWithMeshif(record));
  }

  @Test
  void observersNeverReplacePrimaryGoalOrReward() {
    short observer = Act2DurielQuest.markCompletedNow((short) 0);
    assertTrue(NativeQuestRecord.has(observer, NativeQuestRecord.COMPLETED_NOW));
    assertFalse(NativeQuestRecord.has(observer, NativeQuestRecord.PRIMARY_GOAL_DONE));

    short credited = Act2DurielQuest.acceptTyraelPortal((short) 0);
    assertEquals(credited, Act2DurielQuest.markCompletedNow(credited));
  }

  @Test
  void repairsLegacyStaffInsertionLeftTownBitWithoutTouchingNativePair() {
    short legacy = NativeQuestRecord.set((short) 0, NativeQuestRecord.LEFT_TOWN);
    short killed = Act2DurielQuest.markDurielKilled(legacy);
    assertFalse(NativeQuestRecord.has(killed, NativeQuestRecord.LEFT_TOWN));
    assertTrue(NativeQuestRecord.has(killed, NativeQuestRecord.CUSTOM1));

    short nativePair = Act2DurielQuest.acceptTyraelPortal((short) 0);
    assertEquals(nativePair, Act2DurielQuest.repairLegacyOrificeFlags(nativePair));
  }

  @Test
  void staffInsertionIsOneShotAndRejectedAfterDurielProgress() {
    assertTrue(Act2DurielQuest.canInsertStaff((short) 0));
    assertFalse(Act2DurielQuest.canInsertStaff(
        Act2DurielQuest.markDurielKilled((short) 0)));
    assertFalse(Act2DurielQuest.canInsertStaff(
        Act2DurielQuest.acceptTyraelPortal(
            Act2DurielQuest.markDurielKilled((short) 0))));
    assertFalse(Act2DurielQuest.canInsertStaff(
        Act2DurielQuest.travelWithMeshif(
            Act2DurielQuest.acknowledgeJerhyn(
                Act2DurielQuest.acceptTyraelPortal(
                Act2DurielQuest.markDurielKilled((short) 0))))));
    assertFalse(Act2DurielQuest.staffAlreadyInserted((short) 0));
    short assembled = NativeQuestRecord.set((short) 0, NativeQuestRecord.REWARD_GRANTED);
    assembled = NativeQuestRecord.set(assembled, NativeQuestRecord.PRIMARY_GOAL_DONE);
    assertTrue(Act2DurielQuest.staffAlreadyInserted(assembled));
  }

  @Test
  void tyraelPortalRequiresDurielProgressButRemainsReplayable() {
    assertFalse(Act2DurielQuest.canAcceptTyraelPortal((short) 0));
    short killed = Act2DurielQuest.markDurielKilled((short) 0);
    assertTrue(Act2DurielQuest.canAcceptTyraelPortal(killed));
    short credited = Act2DurielQuest.acceptTyraelPortal(killed);
    assertTrue(Act2DurielQuest.canAcceptTyraelPortal(credited));
  }

  @Test
  void jerhynAndMeshifMessagesRemainReplayableAfterReconnect() {
    short killed = Act2DurielQuest.markDurielKilled((short) 0);
    short tyrael = Act2DurielQuest.acceptTyraelPortal(killed);
    assertTrue(Act2DurielQuest.canAcceptJerhynEnd(tyrael));
    short entered = Act2DurielQuest.acknowledgeJerhyn(tyrael);
    assertTrue(Act2DurielQuest.canAcceptJerhynEnd(entered));
    assertTrue(Act2DurielQuest.canAcceptMeshifTravel(entered));
    short rewarded = Act2DurielQuest.travelWithMeshif(entered);
    assertTrue(Act2DurielQuest.canAcceptJerhynEnd(rewarded));
    assertTrue(Act2DurielQuest.canAcceptMeshifTravel(rewarded));
  }

  @Test
  void reconnectRestoresDoorAfterDurielButNotTownPortalBeforeTyrael() {
    short killed = Act2DurielQuest.markDurielKilled((short) 0);
    assertTrue(Act2DurielQuest.shouldRestoreTyraelDoor(killed));
    assertFalse(Act2DurielQuest.shouldRestoreTownPortal(killed));
  }

  @Test
  void reconnectRestoresTownPortalUntilMeshifTurnIn() {
    short tyrael = Act2DurielQuest.acceptTyraelPortal(
        Act2DurielQuest.markDurielKilled((short) 0));
    assertTrue(Act2DurielQuest.shouldRestoreTyraelDoor(tyrael));
    assertTrue(Act2DurielQuest.shouldRestoreTownPortal(tyrael));

    short jerhyn = Act2DurielQuest.acknowledgeJerhyn(tyrael);
    assertTrue(Act2DurielQuest.shouldRestoreTownPortal(jerhyn));
    short meshif = Act2DurielQuest.travelWithMeshif(jerhyn);
    assertTrue(Act2DurielQuest.shouldRestoreTyraelDoor(meshif));
    assertFalse(Act2DurielQuest.shouldRestoreTownPortal(meshif));
  }
}
