package com.riiablo.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MovementInputSequenceTrackerTest {
  @Test
  void rejectsDuplicateAndOutOfOrderInputsButAllowsPacketLoss() {
    MovementInputSequenceTracker tracker = new MovementInputSequenceTracker();
    assertEquals(1L, tracker.accept(1L));
    assertEquals(2L, tracker.accept(3L));
    assertEquals(-1L, tracker.accept(3L));
    assertEquals(-1L, tracker.accept(2L));
    assertEquals(3L, tracker.lastProcessed());
  }

  @Test
  void resetStartsANewConnectionEpoch() {
    MovementInputSequenceTracker tracker = new MovementInputSequenceTracker();
    assertEquals(0L, tracker.accept(0L));
    assertEquals(1L, tracker.accept(1L));
    assertEquals(-1L, tracker.accept(0L));
    tracker.reset();
    assertEquals(1L, tracker.accept(1L));
  }

  @Test
  void firstVisibleSequencePreservesInitialPacketLossWindow() {
    MovementInputSequenceTracker tracker = new MovementInputSequenceTracker();
    assertEquals(3L, tracker.accept(3L));
  }
}
