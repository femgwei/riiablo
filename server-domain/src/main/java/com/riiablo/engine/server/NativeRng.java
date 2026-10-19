package com.riiablo.engine.server;

/**
 * Legacy host LCG used by compatibility/loot/tool paths, NOT the native
 * D2Common two-word pSeed algorithm. Audited live unit combat and AI use
 * core's NativeUnitRandom; do not replace unrelated legacy streams globally.
 */
public final class NativeRng {
  private int state;

  public NativeRng(int seed) {
    state = seed == 0 ? 0x6D2B79F5 : seed;
  }

  public int state() {
    return state;
  }

  public int nextInt() {
    state = state * 1103515245 + 12345;
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
    return new NativeRng(state ^ (salt * 0x9E3779B9));
  }

  public static NativeRng forUnit(int gameSeed, int unitId) {
    return new NativeRng(gameSeed ^ (unitId * 0x45D9F3B));
  }
}
