package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;

class NativeD2SeedRandomTest {
  @Test
  void boundedDrawsUseTheNativeSeedSequence() {
    D2Seed expected = new D2Seed(0x12345678, 0x9ABCDEF0);
    NativeD2SeedRandom actual = new NativeD2SeedRandom(0x12345678, 0x9ABCDEF0);

    assertEquals(Seed.rollLimitedRandomNumber(expected, 7), actual.nextInt(7));
    assertEquals(Seed.rollLimitedRandomNumber(expected, 16), actual.nextInt(16));
    assertEquals(expected.getNLowSeed(), actual.seed().getNLowSeed());
    assertEquals(expected.getNHighSeed(), actual.seed().getNHighSeed());
  }

  @Test
  void monsterSeedIsTheFirstGameSeedRoll() {
    int gameSeed = 127897359;
    D2Seed expected = new D2Seed(gameSeed, 666);
    int monsterSeed = (int) Seed.rollRandomNumber(expected);
    NativeD2SeedRandom random = new NativeD2SeedRandom(monsterSeed, 666);

    D2Seed region = new D2Seed(monsterSeed, 666);
    assertEquals(Seed.rollLimitedRandomNumber(region, 11), random.nextInt(11));
  }
}
