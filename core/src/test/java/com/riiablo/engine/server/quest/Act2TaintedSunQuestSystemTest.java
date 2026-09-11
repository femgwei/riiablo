package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.d2moo.common.drlg.D2LevelIds;
import org.junit.jupiter.api.Test;

class Act2TaintedSunQuestSystemTest {
  @Test
  void altarIsRestrictedToNativeTaintedSunLevels() {
    assertTrue(Act2TaintedSunQuestSystem.isTaintedSunLevel(
        D2LevelIds.LEVEL_VALLEYOFSNAKES));
    assertTrue(Act2TaintedSunQuestSystem.isTaintedSunLevel(
        D2LevelIds.LEVEL_CLAWVIPERTEMPLELEV2));
    assertFalse(Act2TaintedSunQuestSystem.isTaintedSunLevel(
        D2LevelIds.LEVEL_LOSTCITY));
  }
}
