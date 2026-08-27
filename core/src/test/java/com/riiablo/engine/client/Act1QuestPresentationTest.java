package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import com.riiablo.engine.server.quest.NativeQuestRecord;
import com.riiablo.codec.excel.Quests;

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

  @Test
  void mapsDisplayRowsToNativeRecordIdsInsteadOfVisualOrder() {
    Quests.Entry malus = quest(3, 0, "qstsa1q3");
    malus.order = 5;
    Quests.Entry cain = quest(4, 0, "qstsa1q4");
    cain.order = 3;
    assertEquals(3, Act1QuestPresentation.recordIndex(malus));
    assertEquals(4, Act1QuestPresentation.recordIndex(cain));
  }

  @Test
  void fallsBackToQstrAndRejectsNonAct1Rows() {
    assertEquals(5, Act1QuestPresentation.recordIndex(quest(0, 0, "qstsa1q5")));
    assertEquals(-1, Act1QuestPresentation.recordIndex(quest(8, 1, "qstsa2q1")));
    assertEquals(-1, Act1QuestPresentation.recordIndex(null));
  }

  private static Quests.Entry quest(int id, int act, String qstr) {
    Quests.Entry quest = new Quests.Entry();
    quest.id = id;
    quest.act = act;
    quest.qstr = qstr;
    return quest;
  }
}
