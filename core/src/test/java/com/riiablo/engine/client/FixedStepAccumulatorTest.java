package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FixedStepAccumulatorTest {
  private static final float STEP = 1f / 25f;

  @Test
  void emitsWholeFixedSteps() {
    FixedStepAccumulator accumulator = new FixedStepAccumulator(STEP, 4);
    int[] count = {0};

    assertEquals(1, accumulator.advance(0.04f, ignored -> count[0]++));
    assertEquals(1, count[0]);
    assertEquals(0f, accumulator.getAccumulated(), 0.00001f);

    assertEquals(2, accumulator.advance(0.08f, ignored -> count[0]++));
    assertEquals(3, count[0]);
  }

  @Test
  void carriesFractionalRenderFrames() {
    FixedStepAccumulator accumulator = new FixedStepAccumulator(STEP, 4);
    int[] count = {0};

    assertEquals(0, accumulator.advance(0.016f, ignored -> count[0]++));
    assertEquals(1, accumulator.advance(0.024f, ignored -> count[0]++));
    assertEquals(1, count[0]);
  }

  @Test
  void boundsLongFrameCatchUpAndDropsBacklog() {
    FixedStepAccumulator accumulator = new FixedStepAccumulator(STEP, 4);
    int[] count = {0};

    assertEquals(4, accumulator.advance(2f, ignored -> count[0]++));
    assertEquals(4, count[0]);
    assertEquals(0f, accumulator.getAccumulated(), 0.00001f);
  }

  @Test
  void ignoresInvalidDeltaAndCanReset() {
    FixedStepAccumulator accumulator = new FixedStepAccumulator(STEP, 4);
    int[] count = {0};

    assertEquals(0, accumulator.advance(Float.NaN, ignored -> count[0]++));
    assertEquals(0, accumulator.advance(Float.POSITIVE_INFINITY, ignored -> count[0]++));
    assertEquals(0, accumulator.advance(-1f, ignored -> count[0]++));
    accumulator.advance(0.02f, ignored -> count[0]++);
    accumulator.reset();
    assertEquals(0f, accumulator.getAccumulated(), 0.00001f);
  }

  @Test
  void normalSixtyHertzFramesRemainStable() {
    FixedStepAccumulator accumulator = new FixedStepAccumulator(STEP, 4);
    int[] count = {0};
    for (int i = 0; i < 60; i++) {
      accumulator.advance(1f / 60f, ignored -> count[0]++);
    }

    assertEquals(25, count[0]);
    assertEquals(0f, accumulator.getAccumulated(), 0.0001f);
  }
}
