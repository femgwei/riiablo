package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.map.NativePresetObjectResolver;
import org.junit.jupiter.api.Test;

class Act2TombSelectionTest {
  @Test
  void matchesD2MooSeedOrder() {
    Act2TombSelection selection = Act2TombSelection.forGameSeed(1);
    assertEquals(D2LevelIds.LEVEL_TALRASHASTOMB2, selection.staffTombLevel());
    assertEquals(D2LevelIds.LEVEL_TALRASHASTOMB5, selection.bossTombLevel());
    assertNotEquals(selection.staffTombLevel(), selection.bossTombLevel());
  }

  @Test
  void everySeedChoosesTwoDifferentValidTombs() {
    for (int seed = -100; seed <= 100; seed++) {
      Act2TombSelection selection = Act2TombSelection.forGameSeed(seed);
      assertTrue(selection.isTomb(selection.staffTombLevel()));
      assertTrue(selection.isTomb(selection.bossTombLevel()));
      assertNotEquals(selection.staffTombLevel(), selection.bossTombLevel());
    }
  }

  @Test
  void arcaneSymbolsSkipStaffTombAndFollowNativeOrder() {
    Act2TombSelection selection = Act2TombSelection.forGameSeed(1);
    assertEquals(313, selection.arcaneSymbolObjectFor(D2LevelIds.LEVEL_TALRASHASTOMB1));
    assertEquals(-1, selection.arcaneSymbolObjectFor(selection.staffTombLevel()));
    assertEquals(312, selection.arcaneSymbolObjectFor(D2LevelIds.LEVEL_TALRASHASTOMB3));
    assertEquals(309, selection.arcaneSymbolObjectFor(D2LevelIds.LEVEL_TALRASHASTOMB7));
    assertEquals(309, NativePresetObjectResolver.resolve(2,
        D2LevelIds.LEVEL_TALRASHASTOMB7, 582, 1, 0, 0).classId);
  }
}
