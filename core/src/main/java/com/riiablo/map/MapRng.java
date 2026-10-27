package com.riiablo.map;

import com.riiablo.engine.server.NamedRngStreams;
import com.riiablo.engine.server.NativeRng;

/**
 * Explicit deterministic random source for map builders.
 *
 * <p>Map generation must not consume the process-global LibGDX RNG: the stream
 * identity and difficulty are part of the map checkpoint/replay witness.</p>
 */
final class MapRng {
  private MapRng() {}

  static NativeRng open(int seed, String builder, int difficulty) {
    if (builder == null || builder.isEmpty()) throw new IllegalArgumentException("builder");
    return NamedRngStreams.openLegacy(seed, "map." + builder, difficulty);
  }

  static int nextInt(NativeRng rng, int start, int end) {
    if (rng == null) throw new NullPointerException("rng");
    if (start > end) throw new IllegalArgumentException("start must not exceed end");
    long span = (long) end - start + 1L;
    if (span > Integer.MAX_VALUE) return (int) (start + (rng.nextInt() % span));
    return start + rng.nextInt((int) span);
  }
}
