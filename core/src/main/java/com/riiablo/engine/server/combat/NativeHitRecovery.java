package com.riiablo.engine.server.combat;

import java.util.function.IntUnaryOperator;

/** 1.10 SUNITDMG_ShouldSkipHitRecovery, using raw 8.8 damage/life and defender seed masks. */
public final class NativeHitRecovery {
  private NativeHitRecovery() {}

  public static int divisor(int hitClass) {
    // Native switches on the whole HitClass, including its critical/element layer.
    switch (hitClass) {
      case 2: case 6: case 10: case 11: return 8;
      case 5: return 64;
      case 4: case 8: return 32;
      default: return 16;
    }
  }

  public static boolean shouldSkip(int totalFixed, int poisonFixed, int maxLifeFixed,
      int hitClass, boolean frozen, boolean hasGetHitMode, IntUnaryOperator mask) {
    if (frozen || poisonFixed != 0 && poisonFixed == totalFixed || totalFixed < 256) return true;
    int divisor = divisor(hitClass);
    if (totalFixed < maxLifeFixed / divisor) return true;
    if (totalFixed < maxLifeFixed / (divisor / 2) && mask.applyAsInt(1) == 0) return true;
    if (totalFixed < maxLifeFixed / (divisor / 4) && mask.applyAsInt(3) == 0) return true;
    return !hasGetHitMode;
  }
}
