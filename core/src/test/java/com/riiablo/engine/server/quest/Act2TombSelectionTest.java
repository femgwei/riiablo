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

  @Test
  void preset582NeverCreatesSymbolInStaffTomb() {
    for (int seed = -32; seed <= 32; seed++) {
      Act2TombSelection selection = Act2TombSelection.forGameSeed(seed);
      NativePresetObjectResolver.Resolution resolution = NativePresetObjectResolver.resolve(
          2, selection.staffTombLevel(), 582, seed, 10, 20);
      assertEquals(NativePresetObjectResolver.Kind.SKIP, resolution.kind,
          "staff tomb must not receive an Arcane Symbol for seed " + seed);
      assertEquals(-1, resolution.classId);
      assertTrue(!resolution.shouldCreate());
    }
  }

  @Test
  void preset582UsesSixNativeSymbolsAndFallbackOutsideTombs() {
    for (int seed = -16; seed <= 16; seed++) {
      Act2TombSelection selection = Act2TombSelection.forGameSeed(seed);
      int symbols = 0;
      for (int level = Act2TombSelection.FIRST_TOMB_LEVEL;
          level <= Act2TombSelection.LAST_TOMB_LEVEL; level++) {
        NativePresetObjectResolver.Resolution resolution =
            NativePresetObjectResolver.resolve(2, level, 582, seed, 0, 0);
        if (level == selection.staffTombLevel()) {
          assertEquals(NativePresetObjectResolver.Kind.SKIP, resolution.kind);
        } else {
          assertEquals(NativePresetObjectResolver.Kind.ARCANE_SYMBOL, resolution.kind);
          assertTrue(resolution.classId >= 307 && resolution.classId <= 313);
          symbols++;
        }
      }
      assertEquals(6, symbols);
      assertEquals(307, NativePresetObjectResolver.resolve(2, 46, 582, seed, 0, 0).classId);
    }
  }
}
