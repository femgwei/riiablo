package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.codec.excel.SuperUniques;
import org.junit.jupiter.api.Test;

class Act1CountessQuestTest {
  @Test
  void progressesFromTomeThroughTowerCellar() {
    short record = Act1CountessQuest.discover((short) 0);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.STARTED));

    record = Act1CountessQuest.enterForgottenTower(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN));

    record = Act1CountessQuest.enterCellar(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM1));

    record = Act1CountessQuest.enterCountessLevel(record);
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM2));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.ENTERED_AREA));
  }

  @Test
  void countessDeathGrantsAutomaticRewardWithoutPendingState() {
    short record = Act1CountessQuest.complete(
        Act1CountessQuest.enterCountessLevel((short) 0), true);

    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_NOW));
    assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    assertEquals(record, Act1CountessQuest.complete(record, true));
  }

  @Test
  void selectsDifficultySpecificSuperUniqueTreasureClass() {
    SuperUniques.Entry countess = new SuperUniques.Entry();
    countess.TC = "Countess Normal";
    countess.TCNightmare = "Countess Nightmare";
    countess.TCHell = "Countess Hell";

    assertEquals("Countess Normal", NativeCountessRewardSystem.treasureClass(countess, 0));
    assertEquals("Countess Nightmare", NativeCountessRewardSystem.treasureClass(countess, 1));
    assertEquals("Countess Hell", NativeCountessRewardSystem.treasureClass(countess, 2));
  }
}
