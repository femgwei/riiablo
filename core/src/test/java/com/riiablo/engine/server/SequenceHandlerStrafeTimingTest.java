package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pure regression coverage for the hard-coded Strafe rollback formula. */
class SequenceHandlerStrafeTimingTest {
  @Test
  void fastBowUsesRollbackMiddleFrameInsteadOfNormalAttackPenalty() {
    // A fast bow is represented by WSM -10 in the native signed convention.
    SequenceHandler.StrafeTiming timing = SequenceHandler.calculateStrafeTiming(
        256, 70, -10, false);

    assertEquals(44, timing.effectiveIAS);
    assertEquals(54, timing.eias);
    assertEquals(4, timing.initialFrame);
    assertEquals(2, timing.middleFrame);
    assertEquals(3, timing.rollbackFrame);
    assertEquals(394, timing.animationSpeed);
  }

  @Test
  void crossbowUsesNineActionAndTwentyBaseFrames() {
    SequenceHandler.StrafeTiming timing = SequenceHandler.calculateStrafeTiming(
        256, 0, 0, true);

    assertEquals(20, timing.baseFrame);
    assertEquals(9, timing.actionFrame);
    assertEquals(9, timing.initialFrame);
    assertEquals(5, timing.middleFrame);
  }
}
