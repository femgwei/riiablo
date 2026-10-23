package com.riiablo.engine.server;

import java.util.Objects;

/**
 * Explicit deterministic RNG stream factory for rules that use the compatibility
 * {@link NativeRng} algorithm. Stream identity is part of the seed, so adding
 * draws to one named stream cannot consume another stream's sequence.
 *
 * <p>This factory does not replace D2Common's two-word {@code pSeed} streams;
 * callers that require native unit state must continue to use
 * {@link NativeUnitRandom}.</p>
 */
public final class NamedRngStreams {
  private NamedRngStreams() {}

  /** Opens a deterministic stream scoped by game seed, stream name and context id. */
  public static NativeRng open(int gameSeed, String streamName, long contextId) {
    return new NativeRng(seedFor(gameSeed, streamName, contextId));
  }

  /** Returns the derived seed for diagnostics and replay manifests. */
  public static int seedFor(int gameSeed, String streamName, long contextId) {
    Objects.requireNonNull(streamName, "streamName");
    if (streamName.trim().isEmpty()) throw new IllegalArgumentException("streamName");
    int hash = 0x811C9DC5;
    for (int i = 0; i < streamName.length(); i++) {
      hash ^= streamName.charAt(i);
      hash *= 0x01000193;
    }
    int mixed = gameSeed ^ hash;
    mixed ^= (int) contextId;
    mixed ^= (int) (contextId >>> 32);
    mixed ^= mixed >>> 16;
    mixed *= 0x7FEB352D;
    mixed ^= mixed >>> 15;
    mixed *= 0x846CA68B;
    return mixed ^ (mixed >>> 16);
  }
}
