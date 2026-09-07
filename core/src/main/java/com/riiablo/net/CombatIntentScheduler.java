package com.riiablo.net;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;

/** Per-connection ordering, replay protection and target-tick queue for combat input. */
public final class CombatIntentScheduler {
  public static final int MAX_FUTURE_TICKS = 8;
  public static final int MAX_LATE_TICKS = 8;
  private static final int REPLAY_CACHE_SIZE = 128;

  public enum Result {
    ACCEPTED,
    DUPLICATE,
    CONFLICT,
    STALE,
    INVALID_SEQUENCE,
    INVALID_TICK_ORDER,
    TOO_FAR_FUTURE,
    TOO_LATE
  }

  private final PriorityQueue<CombatIntent> pending = new PriorityQueue<>(
      Comparator.comparingLong((CombatIntent input) -> input.targetTick)
          .thenComparingLong(input -> input.sequence));
  private final LinkedHashMap<Long, Long> received = new LinkedHashMap<>();
  private long lastReceivedSequence;
  private long lastTargetTick;

  public Result submit(CombatIntent input, long currentTick) {
    if (input == null) return Result.INVALID_SEQUENCE;
    // Pre-clock clients and legacy headless probes use an all-zero envelope.
    if (input.sequence == 0L) {
      if (input.observedServerTick != 0L || input.targetTick != 0L) {
        return Result.INVALID_SEQUENCE;
      }
      pending.add(input);
      return Result.ACCEPTED;
    }
    if (input.sequence < 0L) return Result.INVALID_SEQUENCE;

    Long previous = received.get(input.sequence);
    if (previous != null) {
      return previous.longValue() == input.replayFingerprint()
          ? Result.DUPLICATE : Result.CONFLICT;
    }
    if (input.sequence <= lastReceivedSequence) return Result.STALE;
    if (input.observedServerTick < 0L || input.targetTick < 0L
        || input.targetTick < input.observedServerTick) {
      return Result.INVALID_TICK_ORDER;
    }
    if (input.targetTick != 0L && input.targetTick > currentTick + MAX_FUTURE_TICKS) {
      return Result.TOO_FAR_FUTURE;
    }
    if (input.targetTick != 0L && input.targetTick + MAX_LATE_TICKS < currentTick) {
      return Result.TOO_LATE;
    }
    if (input.targetTick < lastTargetTick) return Result.INVALID_TICK_ORDER;

    lastReceivedSequence = input.sequence;
    lastTargetTick = input.targetTick;
    received.put(input.sequence, input.replayFingerprint());
    trimReplayCache();
    pending.add(input);
    return Result.ACCEPTED;
  }

  public void drainReady(long currentTick, java.util.Collection<CombatIntent> out) {
    while (!pending.isEmpty()) {
      CombatIntent next = pending.peek();
      if (next.targetTick != 0L && next.targetTick > currentTick) break;
      out.add(pending.remove());
    }
  }

  public int pendingCount() {
    return pending.size();
  }

  public void reset() {
    pending.clear();
    received.clear();
    lastReceivedSequence = 0L;
    lastTargetTick = 0L;
  }

  private void trimReplayCache() {
    while (received.size() > REPLAY_CACHE_SIZE) {
      Iterator<Map.Entry<Long, Long>> iterator = received.entrySet().iterator();
      iterator.next();
      iterator.remove();
    }
  }
}
