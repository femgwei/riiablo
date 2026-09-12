package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act5AncientsQuestTest {
  @Test
  void encounterResetsOnlyWhenLastLivingSummitPlayerDiesBeforeCompletion() {
    assertTrue(Act5AncientsQuest.shouldResetEncounter(true, false, 0));
    assertFalse(Act5AncientsQuest.shouldResetEncounter(false, false, 0));
    assertFalse(Act5AncientsQuest.shouldResetEncounter(true, false, 1));
    assertFalse(Act5AncientsQuest.shouldResetEncounter(true, true, 0));
  }

  @Test
  void difficultyLevelGateMatchesD2Moo() {
    assertEquals(20, Act5AncientsQuest.requiredLevel(0));
    assertEquals(40, Act5AncientsQuest.requiredLevel(1));
    assertEquals(60, Act5AncientsQuest.requiredLevel(2));
    assertEquals(60, Act5AncientsQuest.requiredLevel(99));
  }

  @Test
  void thirdAncientGrantsQuestImmediatelyAndIsIdempotent() {
    short record = Act5AncientsQuest.enterArea((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA));
    record = Act5AncientsQuest.complete(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    assertEquals(record, Act5AncientsQuest.complete(record));
  }
}
