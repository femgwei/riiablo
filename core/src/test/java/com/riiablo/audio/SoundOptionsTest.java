package com.riiablo.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SoundOptionsTest {
  @Test
  void volumeCyclesInTenPercentStepsAndWraps() {
    float volume = 0f;
    for (int step = 1; step <= 10; step++) {
      volume = SoundOptions.nextVolume(volume);
      assertEquals(step / 10f, volume, 0.0001f);
    }
    assertEquals(0f, SoundOptions.nextVolume(volume), 0.0001f);
  }

  @Test
  void nonStepValuesAdvanceFromNearestMenuStep() {
    assertEquals(0.4f, SoundOptions.nextVolume(0.34f), 0.0001f);
    assertEquals(0.5f, SoundOptions.nextVolume(0.45f), 0.0001f);
  }

  @Test
  void labelsClampInvalidPersistedValues() {
    assertEquals("0%", SoundOptions.percentageLabel(-1f));
    assertEquals("0%", SoundOptions.percentageLabel(Float.NaN));
    assertEquals("50%", SoundOptions.percentageLabel(0.5f));
    assertEquals("100%", SoundOptions.percentageLabel(2f));
  }
}
