package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NativeCharacterProgressionTest {
  @Test
  void expansionFinalActUnlocksNativeDifficultiesAndNeverRegresses() {
    int flags = 0x00400020;
    flags = NativeCharacterProgression.update(flags, 5, 0, true);
    assertEquals(5, NativeCharacterProgression.value(flags));
    assertTrue(NativeCharacterProgression.isDifficultyUnlocked(flags, 1, true));
    assertFalse(NativeCharacterProgression.isDifficultyUnlocked(flags, 2, true));

    flags = NativeCharacterProgression.update(flags, 5, 1, true);
    assertEquals(10, NativeCharacterProgression.value(flags));
    assertTrue(NativeCharacterProgression.isDifficultyUnlocked(flags, 2, true));
    assertEquals(10, NativeCharacterProgression.value(
        NativeCharacterProgression.update(flags, 1, 0, true)));
    assertEquals(0x00400020, flags & ~0xFF00);
  }

  @Test
  void classicUsesFourActsPerDifficulty() {
    int flags = NativeCharacterProgression.update(0, 4, 0, false);
    assertEquals(4, NativeCharacterProgression.value(flags));
    flags = NativeCharacterProgression.update(flags, 4, 2, false);
    assertEquals(12, NativeCharacterProgression.value(flags));
  }
}
