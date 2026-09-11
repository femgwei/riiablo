package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act2HorazonTomeQuestTest {
  @Test
  void tomeCompletesA2Q4AndIsIdempotent() {
    short completed = Act2HorazonTomeQuest.completeObjective((short) 0);
    assertTrue(NativeQuestRecord.has(completed, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(completed, NativeQuestRecord.REWARD_PENDING));
    assertTrue(NativeQuestRecord.has(completed, NativeQuestRecord.REWARD_GRANTED));
    assertTrue(NativeQuestRecord.has(completed, NativeQuestRecord.CUSTOM3));
    assertTrue(NativeQuestRecord.has(completed, NativeQuestRecord.CUSTOM4));
    assertFalse(NativeQuestRecord.has(completed, NativeQuestRecord.STARTED));
    assertTrue(Act2HorazonTomeQuest.isFinished(completed));
    assertTrue(Act2HorazonTomeQuest.completeObjective(completed) == completed);
  }

  @Test
  void pendingOrGrantedRecordsAreNotRewritten() {
    short pending = NativeQuestRecord.set((short) 0, NativeQuestRecord.REWARD_PENDING);
    short granted = NativeQuestRecord.set((short) 0, NativeQuestRecord.REWARD_GRANTED);
    assertTrue(Act2HorazonTomeQuest.completeObjective(pending) == pending);
    assertTrue(Act2HorazonTomeQuest.completeObjective(granted) == granted);
  }
}
