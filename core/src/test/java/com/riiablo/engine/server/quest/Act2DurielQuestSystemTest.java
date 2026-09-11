package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.d2moo.common.drlg.D2LevelIds;
import org.junit.jupiter.api.Test;

class Act2DurielQuestSystemTest {
  @Test
  void orificeIsOnlyActiveInsideActTwo() {
    assertTrue(Act2DurielQuestSystem.isAct2(D2LevelIds.LEVEL_TALRASHASTOMB1));
    assertTrue(Act2DurielQuestSystem.isAct2(D2LevelIds.LEVEL_DURIELSLAIR));
    assertFalse(Act2DurielQuestSystem.isAct2(D2LevelIds.LEVEL_ROGUEENCAMPMENT));
  }
}
