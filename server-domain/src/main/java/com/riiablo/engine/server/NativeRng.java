package com.riiablo.engine.server;

/**
 * Legacy host LCG used by compatibility/loot/tool paths, NOT the native
 * D2Common two-word pSeed algorithm. Audited live unit combat and AI use
 * core's NativeUnitRandom; do not replace unrelated legacy streams globally.
 */
public final class NativeRng {
  private int state;
  private final String streamName;
  private final long contextId;
  private long drawCount;

  public NativeRng(int seed) {
    this(seed, "legacy", -1L);
  }

  NativeRng(int seed, String streamName, long contextId) {
    state = seed == 0 ? 0x6D2B79F5 : seed;
    this.streamName = streamName == null ? "legacy" : streamName;
    this.contextId = contextId;
  }

  public int state() {
    return state;
  }

  /** Number of random values consumed from this stream. */
  public long drawCount() {
    return drawCount;
  }

  /** Stable diagnostic name; it does not affect the compatibility sequence. */
  public String streamName() {
    return streamName;
  }

  /** Stable diagnostic scope supplied by the stream factory. */
  public long contextId() {
    return contextId;
  }

  public int nextInt() {
    state = state * 1103515245 + 12345;
    drawCount++;
    return state & 0x7FFFFFFF;
  }

  public int nextInt(int bound) {
    if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
    return nextInt() % bound;
  }

  public boolean roll(int numerator, int denominator) {
    if (denominator <= 0 || numerator <= 0) return false;
    if (numerator >= denominator) return true;
    return nextInt(denominator) < numerator;
  }

  public NativeRng fork(int salt) {
    return new NativeRng(state ^ (salt * 0x9E3779B9), streamName + ".fork", contextId);
  }

  public static NativeRng forUnit(int gameSeed, int unitId) {
    return new NativeRng(gameSeed ^ (unitId * 0x45D9F3B));
  }
}
