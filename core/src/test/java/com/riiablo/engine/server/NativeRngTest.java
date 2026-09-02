package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.Test;

class NativeRngTest {
  @Test
  void sameGameAndUnitSeedsProduceTheSameSequence() {
    NativeRng a = NativeRng.forUnit(0x12345678, 17);
    NativeRng b = NativeRng.forUnit(0x12345678, 17);
    for (int i = 0; i < 8; i++) assertEquals(a.nextInt(100000), b.nextInt(100000));
  }

  @Test
  void unitStreamsAreIndependentAndBounded() {
    NativeRng a = NativeRng.forUnit(7, 1);
    NativeRng b = NativeRng.forUnit(7, 2);
    assertNotEquals(a.nextInt(), b.nextInt());
    for (int i = 0; i < 32; i++) {
      int roll = a.nextInt(13);
      assertEquals(true, roll >= 0 && roll < 13);
    }
  }
}
