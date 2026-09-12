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
}
