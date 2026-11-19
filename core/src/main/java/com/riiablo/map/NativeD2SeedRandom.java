package com.riiablo.map;

import java.util.Random;

import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;

/**
 * java.util.Random-shaped adapter for the two-word RNG used by Diablo II.
 *
 * <p>The map population code historically accepted {@link Random}, which made
 * it very easy to accidentally reintroduce Java's LCG.  This adapter keeps the
 * existing population helpers source-compatible while every bounded draw uses
 * {@code SEED_RollLimitedRandomNumber} (low unsigned word modulo, or a mask for
 * powers of two).</p>
 */
final class NativeD2SeedRandom extends Random {
  private static final long serialVersionUID = 1L;
  private final D2Seed seed;

  NativeD2SeedRandom(int lowSeed, int highSeed) {
    super(0L);
    this.seed = new D2Seed(lowSeed, highSeed);
  }

  NativeD2SeedRandom(D2Seed seed) {
    super(0L);
    if (seed == null) throw new NullPointerException("seed");
    this.seed = seed;
  }

  D2Seed seed() {
    return seed;
  }

  int raw() {
    return (int) Seed.rollRandomNumber(seed);
  }

  @Override
  protected int next(int bits) {
    if (bits < 1 || bits > 32) throw new IllegalArgumentException("bits");
    return raw() >>> (32 - bits);
  }

  @Override
  public int nextInt() {
    return raw();
  }

  @Override
  public int nextInt(int bound) {
    if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
    return Seed.rollLimitedRandomNumber(seed, bound);
  }

  @Override
  public long nextLong() {
    long high = Integer.toUnsignedLong(raw());
    long low = Integer.toUnsignedLong(raw());
    return (high << 32) | low;
  }

  @Override
  public float nextFloat() {
    return Integer.toUnsignedLong(raw()) / 4294967296f;
  }

  @Override
  public double nextDouble() {
    return Integer.toUnsignedLong(raw()) / 4294967296d;
  }

  @Override
  public boolean nextBoolean() {
    return (raw() & 1) != 0;
  }
}
