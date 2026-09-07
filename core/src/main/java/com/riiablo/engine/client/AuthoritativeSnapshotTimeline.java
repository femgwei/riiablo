package com.riiablo.engine.client;

/** Monotonic client view of the authoritative simulation clock. */
public final class AuthoritativeSnapshotTimeline {
  private long tick;
  private long serverTimeMillis;

  /**
   * Accepts same-tick entity batches and newer frames, but rejects snapshots
   * that would roll the client back. A zero tick is a compatible legacy frame.
   */
  public boolean accept(long nextTick, long nextServerTimeMillis) {
    if (nextTick == 0L) return true;
    if (nextTick < tick || nextServerTimeMillis < serverTimeMillis) return false;
    tick = nextTick;
    serverTimeMillis = nextServerTimeMillis;
    return true;
  }

  public long tick() {
    return tick;
  }

  public long serverTimeMillis() {
    return serverTimeMillis;
  }
}
