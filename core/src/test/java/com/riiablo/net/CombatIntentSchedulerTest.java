package com.riiablo.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CombatIntentSchedulerTest {
  @Test
  void schedulesCombatAtItsTargetTick() {
    CombatIntentScheduler scheduler = new CombatIntentScheduler();
    CombatIntent input = intent(1, 10, 12, 0, 44, 3, 4);
    assertEquals(CombatIntentScheduler.Result.ACCEPTED, scheduler.submit(input, 10));
    List<CombatIntent> ready = new ArrayList<>();
    scheduler.drainReady(11, ready);
    assertEquals(0, ready.size());
    scheduler.drainReady(12, ready);
    assertEquals(1, ready.size());
    assertEquals(1L, ready.get(0).sequence);
  }

  @Test
  void exactRetransmitCannotApplyDamageTwiceAndConflictIsRejected() {
    CombatIntentScheduler scheduler = new CombatIntentScheduler();
    CombatIntent first = intent(7, 20, 22, 0, 44, 3, 4);
    assertEquals(CombatIntentScheduler.Result.ACCEPTED, scheduler.submit(first, 20));
    assertEquals(CombatIntentScheduler.Result.DUPLICATE, scheduler.submit(first, 20));
    assertEquals(CombatIntentScheduler.Result.CONFLICT,
        scheduler.submit(intent(7, 20, 22, 0, 45, 3, 4), 20));
    List<CombatIntent> ready = new ArrayList<>();
    scheduler.drainReady(22, ready);
    assertEquals(1, ready.size());
  }

  @Test
  void rejectsStaleFutureLateAndRegressingTicks() {
    CombatIntentScheduler scheduler = new CombatIntentScheduler();
    assertEquals(CombatIntentScheduler.Result.TOO_FAR_FUTURE,
        scheduler.submit(intent(1, 10, 19, 0, 1, 0, 0), 10));
    assertEquals(CombatIntentScheduler.Result.TOO_LATE,
        scheduler.submit(intent(1, 1, 2, 0, 1, 0, 0), 11));
    assertEquals(CombatIntentScheduler.Result.INVALID_TICK_ORDER,
        scheduler.submit(intent(1, 11, 10, 0, 1, 0, 0), 10));

    assertEquals(CombatIntentScheduler.Result.ACCEPTED,
        scheduler.submit(intent(2, 10, 12, 0, 1, 0, 0), 10));
    assertEquals(CombatIntentScheduler.Result.STALE,
        scheduler.submit(intent(1, 10, 12, 0, 1, 0, 0), 10));
    assertEquals(CombatIntentScheduler.Result.INVALID_TICK_ORDER,
        scheduler.submit(intent(3, 10, 11, 0, 1, 0, 0), 10));
  }

  @Test
  void legacyAllZeroEnvelopeRemainsImmediate() {
    CombatIntentScheduler scheduler = new CombatIntentScheduler();
    assertEquals(CombatIntentScheduler.Result.ACCEPTED,
        scheduler.submit(intent(0, 0, 0, 0, 1, 0, 0), 30));
    List<CombatIntent> ready = new ArrayList<>();
    scheduler.drainReady(30, ready);
    assertEquals(1, ready.size());
    assertEquals(CombatIntentScheduler.Result.INVALID_SEQUENCE,
        scheduler.submit(intent(0, 30, 30, 0, 1, 0, 0), 30));
  }

  private static CombatIntent intent(long sequence, long observed, long targetTick,
      int skill, int target, float x, float y) {
    return new CombatIntent(sequence, observed, targetTick, skill, target, x, y);
  }
}
