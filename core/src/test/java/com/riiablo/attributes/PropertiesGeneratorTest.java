package com.riiablo.attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PropertiesGeneratorTest {
  @Test
  void encodesNativeByTimePayloadWithOffsetAndLayerClamp() {
    int encoded = PropertiesGenerator.encodeByTimeValue(7, -300, 900);

    // Layer is limited to 0..3.  Values are stored as (value + 256), then
    // clamped to the ten-bit ranges used by D2Common's func18.
    int expectedMin = 0;
    int expectedMax = 1023;
    assertEquals(3 + 4 * (expectedMin + (expectedMax << 10)), encoded);
  }

  @Test
  void encodesZeroByTimeRangeAtNativeOffset() {
    assertEquals(1 + 4 * (256 + (256 << 10)),
        PropertiesGenerator.encodeByTimeValue(1, 0, 0));
  }
}
