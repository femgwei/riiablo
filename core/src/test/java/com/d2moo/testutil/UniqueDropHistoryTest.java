package com.d2moo.testutil;

import com.riiablo.item.UniqueDropHistory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UniqueDropHistoryTest {
  @Test void repeatsAreRejectedAcrossCallsButNotGames() {
    UniqueDropHistory game = new UniqueDropHistory();
    assertTrue(game.claim(4095, false));
    assertFalse(game.claim(4095, false));
    assertTrue(new UniqueDropHistory().claim(4095, false));
  }

  @Test void supportsNoLimitAndRejectsNativeBitmapBoundary() {
    UniqueDropHistory game = new UniqueDropHistory();
    assertFalse(game.claim(-1, false));
    assertFalse(game.claim(4096, false));
    assertFalse(game.claim(4096, false));
    assertTrue(game.claim(4096, true));
    assertTrue(game.claim(9000, true));
    assertFalse(game.claim(9000, false));
    assertFalse(game.claim(9000, false));
  }
}
