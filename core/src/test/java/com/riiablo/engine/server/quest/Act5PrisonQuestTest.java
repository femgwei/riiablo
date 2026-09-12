package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act5PrisonQuestTest {
  @Test
  void anyaDefrostAndMalahRewardAreIdempotent() {
    short record = Act5PrisonQuest.markPotion(Act5PrisonQuest.start((short) 0));
    assertTrue(Act5PrisonQuest.hasPotion(record));
    record = Act5PrisonQuest.complete(record);
    assertTrue(Act5PrisonQuest.canClaimReward(record));
    record = Act5PrisonQuest.claimReward(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(Act5PrisonQuest.canClaimReward(record));
  }

  @Test
  void defrostedObjectRestoresFromPersistentRecord() {
    assertFalse(Act5PrisonQuest.shouldRestoreDefrostedObject((short) 0));
    short pending = Act5PrisonQuest.complete(
        Act5PrisonQuest.markPotion(Act5PrisonQuest.start((short) 0)));
    assertTrue(Act5PrisonQuest.shouldRestoreDefrostedObject(pending));
    assertTrue(Act5PrisonQuest.shouldRestoreDefrostedObject(
        Act5PrisonQuest.claimReward(pending)));
  }
}
