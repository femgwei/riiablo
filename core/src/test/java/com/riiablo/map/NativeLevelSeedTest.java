package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class NativeLevelSeedTest {
  @Test
  void followsDrlgStartSeedThenLevelId() {
    int gameSeed = 0x12345678;
    assertEquals(NativeLevelSeed.startSeed(gameSeed) + 75,
        NativeLevelSeed.forLevel(gameSeed, 75));
  }

  @Test
  void contextOverloadKeepsNativeFormula() {
    assertEquals(NativeLevelSeed.forLevel(42, 75),
        NativeLevelSeed.forLevel(42, 1, 75, 0));
    assertNotEquals(NativeLevelSeed.forLevel(42, 75),
        NativeLevelSeed.forLevel(43, 75));
  }
}
