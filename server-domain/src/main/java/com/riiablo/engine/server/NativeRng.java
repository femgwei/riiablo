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

  /** Immutable state suitable for an authoritative checkpoint. */
  public Snapshot snapshot() {
    return new Snapshot(streamName, contextId, state, drawCount);
  }

  /** Restores this stream without reseeding or consuming a draw. */
  public void restore(int state, long drawCount) {
    if (drawCount < 0L) throw new IllegalArgumentException("drawCount");
    this.state = state;
    this.drawCount = drawCount;
  }

  /** Restores a snapshot captured from this stream identity. */
  public void restore(Snapshot snapshot) {
    if (snapshot == null) throw new NullPointerException("snapshot");
    if (!streamName.equals(snapshot.stream) || contextId != snapshot.contextId) {
      throw new IllegalArgumentException("snapshot stream identity mismatch");
    }
    restore(snapshot.state, snapshot.drawCount);
  }

  /** Creates a stream directly from checkpoint state. */
  public static NativeRng fromSnapshot(Snapshot snapshot) {
    if (snapshot == null) throw new NullPointerException("snapshot");
    NativeRng rng = new NativeRng(snapshot.state, snapshot.stream, snapshot.contextId);
    rng.restore(snapshot.state, snapshot.drawCount);
    return rng;
  }

  public static final class Snapshot {
    public final String stream;
    public final long contextId;
    public final int state;
    public final long drawCount;

    public Snapshot(String stream, long contextId, int state, long drawCount) {
      if (stream == null || stream.trim().isEmpty()) throw new IllegalArgumentException("stream");
      if (drawCount < 0L) throw new IllegalArgumentException("drawCount");
      this.stream = stream;
      this.contextId = contextId;
      this.state = state;
      this.drawCount = drawCount;
    }
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
