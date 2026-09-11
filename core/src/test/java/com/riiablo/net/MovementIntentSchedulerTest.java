package com.riiablo.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MovementIntentSchedulerTest {
  @Test
  void queuesFutureInputAndDrainsInTargetTickThenSequenceOrder() {
    MovementIntentScheduler scheduler = new MovementIntentScheduler();
    assertEquals(MovementIntentScheduler.Result.ACCEPTED,
        scheduler.submit(MovementIntent.location(2, 10, 11, true, 4, 5), 10));
    assertEquals(MovementIntentScheduler.Result.ACCEPTED,
        scheduler.submit(MovementIntent.location(3, 10, 12, true, 6, 7), 10));
    List<MovementIntent> ready = new ArrayList<>();
    scheduler.drainReady(10, ready);
    assertEquals(0, ready.size());
    scheduler.drainReady(11, ready);
    assertEquals(1, ready.size());
    assertEquals(2L, ready.get(0).sequence);
    scheduler.drainReady(12, ready);
    assertEquals(2, ready.size());
    assertEquals(3L, ready.get(1).sequence);
  }

  @Test
  void acceptsPacketLossAndExactReplayButRejectsConflictingReplay() {
    MovementIntentScheduler scheduler = new MovementIntentScheduler();
    MovementIntent first = MovementIntent.location(1, 20, 22, false, 8, 9);
    assertEquals(MovementIntentScheduler.Result.ACCEPTED, scheduler.submit(first, 20));
    assertEquals(MovementIntentScheduler.Result.DUPLICATE, scheduler.submit(first, 20));
    assertEquals(MovementIntentScheduler.Result.CONFLICT,
        scheduler.submit(MovementIntent.location(1, 20, 22, false, 9, 9), 20));
    assertEquals(MovementIntentScheduler.Result.ACCEPTED,
        scheduler.submit(MovementIntent.entity(3, 20, 22, true, 1, 44), 20));
    assertEquals(MovementIntentScheduler.Result.STALE,
        scheduler.submit(MovementIntent.location(2, 20, 22, false, 1, 1), 20));
  }

  @Test
  void rejectsInvalidSchedulingWindowAndSupportsStartupTickZero() {
    MovementIntentScheduler scheduler = new MovementIntentScheduler();
    assertEquals(MovementIntentScheduler.Result.INVALID_TICK_ORDER,
        scheduler.submit(MovementIntent.location(1, 11, 10, false, 0, 0), 10));
    assertEquals(MovementIntentScheduler.Result.TOO_FAR_FUTURE,
        scheduler.submit(MovementIntent.location(1, 10, 19, false, 0, 0), 10));
    assertEquals(MovementIntentScheduler.Result.TOO_LATE,
        scheduler.submit(MovementIntent.location(1, 1, 2, false, 0, 0), 11));
    assertEquals(MovementIntentScheduler.Result.ACCEPTED,
        scheduler.submit(MovementIntent.location(1, 0, 0, false, 0, 0), 11));
  }

  @Test
  void rejectsTargetTickRegressionSoOlderCommandsCannotOverwriteNewerOnes() {
    MovementIntentScheduler scheduler = new MovementIntentScheduler();
    assertEquals(MovementIntentScheduler.Result.ACCEPTED,
        scheduler.submit(MovementIntent.location(1, 10, 12, false, 1, 1), 10));
    assertEquals(MovementIntentScheduler.Result.INVALID_TICK_ORDER,
        scheduler.submit(MovementIntent.location(2, 10, 11, false, 2, 2), 10));
  }

  @Test
  void resetStartsANewConnectionEpoch() {
    MovementIntentScheduler scheduler = new MovementIntentScheduler();
    MovementIntent input = MovementIntent.location(1, 2, 3, false, 1, 1);
    assertEquals(MovementIntentScheduler.Result.ACCEPTED, scheduler.submit(input, 2));
    scheduler.reset();
    assertEquals(0, scheduler.pendingCount());
    assertEquals(MovementIntentScheduler.Result.ACCEPTED, scheduler.submit(input, 2));
  }

  @Test
  void receiveTickAnnotationDoesNotChangeReplayIdentity() {
    MovementIntentScheduler scheduler = new MovementIntentScheduler();
    MovementIntent input = MovementIntent.location(1, 10, 11, false, 1, 1);
    MovementIntent received = input.withReceivedTick(10);
    assertEquals(10L, received.receivedTick);
    assertEquals(MovementIntentScheduler.Result.ACCEPTED,
        scheduler.submit(received, 10));
    assertEquals(MovementIntentScheduler.Result.DUPLICATE,
        scheduler.submit(received.withReceivedTick(11), 10));
  }
}
