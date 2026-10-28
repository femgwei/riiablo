package com.riiablo.engine.server.skill;

import com.riiablo.engine.server.NamedRngStreams;
import com.riiablo.engine.server.NativeRng;

/** Deterministic compatibility RNG for legacy static skill formula helpers. */
final class SkillRandom {
  private static final ThreadLocal<NativeRng> RNG = ThreadLocal.withInitial(
      () -> NamedRngStreams.open(0, "skill-formula", 0));
  private SkillRandom() {}
  static int random(int min, int max) {
    if (max <= min) return min;
    return min + RNG.get().nextInt(max - min + 1);
  }
  static float random(float min, float max) {
    if (max <= min) return min;
    return min + (max - min) * (RNG.get().nextInt(1 << 24) / 16777216f);
  }
}
