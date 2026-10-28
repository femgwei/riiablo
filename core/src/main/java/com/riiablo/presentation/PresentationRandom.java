package com.riiablo.presentation;

import com.riiablo.engine.server.NamedRngStreams;
import com.riiablo.engine.server.NativeRng;

/** Stable client-only randomness for cosmetic variation; never affects gameplay state. */
final class PresentationRandom {
  private static final ThreadLocal<NativeRng> RNG = ThreadLocal.withInitial(
      () -> NamedRngStreams.open(0, "presentation-cosmetic", 0));
  private PresentationRandom() {}
  static int range(int min, int max) {
    if (max <= min) return min;
    return min + RNG.get().nextInt(max - min + 1);
  }
  static float range(float min, float max) {
    if (max <= min) return min;
    return min + (max - min) * ((RNG.get().nextInt(1 << 24)) / 16777216f);
  }
  static boolean chance(int percent) { return range(0, 99) < percent; }
}
