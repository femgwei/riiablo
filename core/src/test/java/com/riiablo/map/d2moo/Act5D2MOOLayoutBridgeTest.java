package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.D2LevelIds;

class Act5D2MOOLayoutBridgeTest {
  @Test
  void exportsTheNativeAct5MainProgressionAndLeavesNihlathakBranchSeparate() {
    assertEquals(D2LevelIds.LEVEL_HARROGATH, Act5D2MOOLayoutBridge.LEVELS[0]);
    assertEquals(D2LevelIds.LEVEL_BLOODYFOOTHILLS, Act5D2MOOLayoutBridge.LEVELS[1]);
    assertEquals(D2LevelIds.LEVEL_ARREATSUMMIT, Act5D2MOOLayoutBridge.LEVELS[9]);
    assertEquals(D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV1, Act5D2MOOLayoutBridge.LEVELS[10]);
    assertEquals(D2LevelIds.LEVEL_WORLDSTONECHAMBER,
        Act5D2MOOLayoutBridge.LEVELS[14]);
    for (int levelId : Act5D2MOOLayoutBridge.LEVELS) {
      assertNotEquals(D2LevelIds.LEVEL_NIHLATHAKSTEMPLE, levelId);
    }
  }
}
