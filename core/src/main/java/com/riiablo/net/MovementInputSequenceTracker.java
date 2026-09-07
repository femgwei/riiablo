package com.riiablo.net;

/** Monotonic per-connection movement input gate. */
public final class MovementInputSequenceTracker {
  private long lastProcessed;

  /** Returns the positive sequence advance, zero for legacy, or -1 for stale. */
  public long accept(long sequence) {
    if (sequence == 0L) return lastProcessed == 0L ? 0L : -1L;
    if (sequence < 0L || sequence <= lastProcessed) return -1L;
    long previous = lastProcessed;
    lastProcessed = sequence;
    return previous == 0L ? sequence : sequence - previous;
  }

  public long lastProcessed() {
    return lastProcessed;
  }

  public void reset() {
    lastProcessed = 0L;
  }
}
