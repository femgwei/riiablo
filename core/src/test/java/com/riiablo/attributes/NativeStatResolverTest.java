package com.riiablo.attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeStatResolverTest {
  @Test
  void addSourcesAlwaysPrecedeTheCombinedPercentPhase() {
    List<NativeStatResolver.Source> shuffled = Arrays.asList(
        new NativeStatResolver.Source("late-flat", 30, NativeStatResolver.Operation.ADD, 25),
        new NativeStatResolver.Source("first-percent", 10,
            NativeStatResolver.Operation.PERCENT, 20),
        new NativeStatResolver.Source("early-flat", 5, NativeStatResolver.Operation.ADD, 75),
        new NativeStatResolver.Source("second-percent", 20,
            NativeStatResolver.Operation.PERCENT, 10));

    assertEquals(260, NativeStatResolver.resolveEncoded(100, shuffled));
  }

  @Test
  void percentageTruncatesAtEncodedBoundaryAndPreservesFixedFractions() {
    assertEquals(13, NativeStatResolver.applyPercentEncoded(10, 33));
    assertEquals(61 * 256 + 128,
        NativeStatResolver.applyPercentEncoded(41 * 256, 50));
  }
}
