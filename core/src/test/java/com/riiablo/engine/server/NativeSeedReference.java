package com.riiablo.engine.server;

import java.math.BigInteger;
import java.util.function.IntUnaryOperator;

/** Test-only unsigned arithmetic oracle, independent of D2Seed/Seed/NativeUnitRandom. */
final class NativeSeedReference implements IntUnaryOperator {
  private static final BigInteger MASK = new BigInteger("ffffffff",16);
  private static final BigInteger MULTIPLIER = new BigInteger("6ac690c5",16);
  private BigInteger low, high;
  NativeSeedReference(int seed) {
    this(seed, 666);
  }
  NativeSeedReference(int lowSeed, int highSeed) {
    low = BigInteger.valueOf(Integer.toUnsignedLong(lowSeed));
    high = BigInteger.valueOf(Integer.toUnsignedLong(highSeed));
  }
  private BigInteger draw() {
    BigInteger value = low.multiply(MULTIPLIER).add(high);
    low = value.and(MASK); high = value.shiftRight(32); return value;
  }
  int nextInt(int bound) { return bound <= 0 ? 0 : draw().and(MASK).mod(BigInteger.valueOf(bound)).intValue(); }
  long nextUnsigned() { return draw().and(MASK).longValue(); }
  int percentage() { return draw().mod(BigInteger.valueOf(100)).intValue(); }
  @Override public int applyAsInt(int bound) { return nextInt(bound); }
  int state() { return low.intValue(); }
  int high() { return high.intValue(); }
}
