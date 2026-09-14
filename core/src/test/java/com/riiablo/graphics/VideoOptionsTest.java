package com.riiablo.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VideoOptionsTest {
  @Test
  void gammaAdvancesByTenPercentAndWraps() {
    assertEquals(1.1f, VideoOptions.nextGamma(1.0f), 0.0001f);
    assertEquals(0.6f, VideoOptions.nextGamma(0.5f), 0.0001f);
    assertEquals(0.5f, VideoOptions.nextGamma(4.0f), 0.0001f);
  }

  @Test
  void gammaClampsAndLabels() {
    assertEquals(0.5f, VideoOptions.normalizeGamma(-1f), 0.0001f);
    assertEquals(1.0f, VideoOptions.normalizeGamma(Float.NaN), 0.0001f);
    assertEquals("50%", VideoOptions.gammaLabel(0.1f));
    assertEquals("100%", VideoOptions.gammaLabel(1.0f));
    assertEquals("400%", VideoOptions.gammaLabel(9f));
  }
}
