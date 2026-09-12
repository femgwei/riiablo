package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act3MephistoQuestTest {
  @Test
  void mephistoCompletionIsImmediateAndIdempotent() {
    short completed = Act3MephistoQuest.complete((short) 0);
    assertTrue(NativeQuestRecord.has(completed, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(completed, NativeQuestRecord.REWARD_GRANTED));
    assertTrue(NativeQuestRecord.has(completed, NativeQuestRecord.CUSTOM7));
    assertTrue(Act3MephistoQuest.shouldOpenExit(completed));
    assertTrue(Act3MephistoQuest.isFinished(completed));
    assertTrue(Act3MephistoQuest.complete(completed) == completed);
  }

  @Test
  void observersDoNotReplaceAnEligibleCompletion() {
    short observer = Act3MephistoQuest.completeObserver((short) 0);
    assertTrue(NativeQuestRecord.has(observer, NativeQuestRecord.COMPLETED_NOW));
    assertFalse(NativeQuestRecord.has(observer, NativeQuestRecord.REWARD_GRANTED));
    short completed = Act3MephistoQuest.complete((short) 0);
    assertTrue(Act3MephistoQuest.completeObserver(completed) == completed);
  }
}
