package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.D2LevelIds;

class Act4D2MOOLayoutBridgeTest {
  @Test
  void exportsNativeAct4ProgressionIncludingMazeAndIndependentChaos() {
    assertEquals(D2LevelIds.LEVEL_THEPANDEMONIUMFORTRESS,
        Act4D2MOOLayoutBridge.LEVELS[0]);
    assertEquals(D2LevelIds.LEVEL_OUTERSTEPPES,
        Act4D2MOOLayoutBridge.LEVELS[1]);
    assertEquals(D2LevelIds.LEVEL_CITYOFTHEDAMNED,
        Act4D2MOOLayoutBridge.LEVELS[3]);
    assertEquals(D2LevelIds.LEVEL_RIVEROFFLAME,
        Act4D2MOOLayoutBridge.LEVELS[4]);
    assertEquals(D2LevelIds.LEVEL_CHAOSSANCTUM,
        Act4D2MOOLayoutBridge.LEVELS[5]);
    assertNotEquals(Act4D2MOOLayoutBridge.LEVELS[4],
        Act4D2MOOLayoutBridge.LEVELS[5]);
  }
}
