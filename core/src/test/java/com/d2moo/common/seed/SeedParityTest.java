package com.d2moo.common.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.D2Seed;

class SeedParityTest {
  @Test
  void initializesNativeDefaultSeed() {
    D2Seed seed = new D2Seed();

    Seed.initSeed(seed);

    assertEquals(1, seed.getNLowSeed());
    assertEquals(666, seed.getNHighSeed());
    assertEquals(0x0000029A00000001L, seed.getLSeed());
  }

  @Test
  void rollsNativeUnsignedSeedSequence() {
    D2Seed seed = new D2Seed(1, 666);

    assertEquals(0x000000006AC6935FL, Seed.rollRandomNumber(seed));
    assertEquals(0x2C890AFD2F2ED81BL, Seed.rollRandomNumber(seed));
    assertEquals(0x2F2ED81B, seed.getNLowSeed());
    assertEquals(0x2C890AFD, seed.getNHighSeed());
  }

  @Test
  void limitedRollUsesNativeLowUint32Modulo() {
    D2Seed seed = new D2Seed(0x12345678, 0x9ABCDEF0);

    assertEquals(5, Seed.rollLimitedRandomNumber(seed, 7));
    assertEquals(0x9E76E948, seed.getNLowSeed());
    assertEquals(0x0797CA94, seed.getNHighSeed());
  }

  @Test
  void limitedPowerOfTwoRollUsesLowBits() {
    D2Seed seed = new D2Seed(0x12345678, 0x9ABCDEF0);

    assertEquals(8, Seed.rollLimitedRandomNumber(seed, 16));
  }

  @Test
  void processRandomValueUsesNativeInt32Wraparound() {
    assertEquals(0x4B7FB535, Seed.computeRandomValue(7, 1_700_000_000, 123_456_789));
  }
}
