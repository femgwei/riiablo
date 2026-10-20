package com.riiablo.engine.client;

import com.riiablo.codec.excel.Quests;
import com.riiablo.engine.server.quest.Act3GidbinnQuest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestPresentationTest {
  @Test void gidbinnRequiresBothIndividualRewardsBeforeCompletionIcon() {
    Quests.Entry row = new Quests.Entry(); row.act = 2; row.qstr = "qstsa3q3";
    assertEquals(2, QuestPresentation.recordIndex(row));
    short record = Act3GidbinnQuest.markBroughtToOrmus((short) 0);
    assertFalse(QuestPresentation.isComplete(row, record));
    record = Act3GidbinnQuest.markOrmusReward(record);
    assertFalse(QuestPresentation.isComplete(row, record));
    record = Act3GidbinnQuest.completeIfBothRewards(Act3GidbinnQuest.markAshearaReward(record));
    assertTrue(QuestPresentation.isComplete(row, record));
    assertNull(QuestPresentation.replaySpeech(row, 2, record));
  }

  @Test void firstQuestInLaterActsDoesNotInheritCharsiLevelRequirement() {
    Quests.Entry row = new Quests.Entry(); row.act = 4; row.qstr = "qstsa5q1";
    assertTrue(QuestPresentation.isAvailable(row, 1, (short) 0, 1));
  }
}
