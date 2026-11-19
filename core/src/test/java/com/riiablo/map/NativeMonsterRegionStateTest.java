package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import org.junit.jupiter.api.Test;

class NativeMonsterRegionStateTest {
  @Test
  void componentVariantsAdvanceTheSharedSeedLikeNative() {
    D2Seed expected = new D2Seed(0x12345678, 0x9ABCDEF0);
    // One component with two choices gives unk0x25 == 1, so the native
    // routine creates two variants: one base draw and one alternate draw.
    // With this seed the first alternate repeats the base, so the native
    // duplicate retry consumes one additional draw before accepting it.
    Seed.rollLimitedRandomNumber(expected, 2);
    Seed.rollLimitedRandomNumber(expected, 2);
    Seed.rollLimitedRandomNumber(expected, 2);

    D2Seed actual = new D2Seed(0x12345678, 0x9ABCDEF0);
    NativeMonsterRegionState.consumeComponentVariants(actual,
        new int[] {2}, 1, 3);

    assertEquals(expected.getNLowSeed(), actual.getNLowSeed());
    assertEquals(expected.getNHighSeed(), actual.getNHighSeed());
  }

  @Test
  void componentsWithNoVariantsDoNotConsumeTheRegionSeed() {
    D2Seed actual = new D2Seed(0xCAFEBABE, 666);
    NativeMonsterRegionState.consumeComponentVariants(actual,
        new int[] {1, 0, 1}, 0, 3);

    assertEquals(0xCAFEBABE, actual.getNLowSeed());
    assertEquals(666, actual.getNHighSeed());
  }
}
