package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import com.riiablo.engine.server.quest.NativeQuestRecord;

class Act1QuestPresentationTest {
  @Test
  void stagesFollowNativeRecordFlags() {
    assertEquals(0, Act1QuestPresentation.stage((short) 0));
    assertEquals(1, Act1QuestPresentation.stage(
        NativeQuestRecord.set((short) 0, NativeQuestRecord.LEFT_TOWN)));
    assertEquals(4, Act1QuestPresentation.stage(
        NativeQuestRecord.set((short) 0, NativeQuestRecord.REWARD_PENDING)));
    assertEquals(5, Act1QuestPresentation.stage(
        NativeQuestRecord.set((short) 0, NativeQuestRecord.REWARD_GRANTED)));
  }

  @Test
  void onlyFirstActQuestIsVisibleBeforeItsRecordIsStarted() {
    assertEquals("akara_act1_q1_init", Act1QuestPresentation.replaySpeech(1, (short) 0));
    assertFalse(Act1QuestPresentation.isComplete((short) 0));
  }
}
