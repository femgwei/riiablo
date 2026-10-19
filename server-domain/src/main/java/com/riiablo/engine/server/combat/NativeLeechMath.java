package com.riiablo.engine.server.combat;

/** D2MOO 1.10f SUnitDmg percentage branch. All resource inputs/results are raw 8.8. */
public final class NativeLeechMath {
  private NativeLeechMath() {}

  public static int percentage(int physical, int lifeBefore, int percent, int divisor, int drain) {
    if (physical <= 0 || lifeBefore <= 0 || percent <= 0 || drain <= 0) return 0;
    long scaled = (long) percent * 64;
    if (divisor > 0) scaled /= divisor;
    long amount = Math.min(physical, lifeBefore) * scaled / 100;
    amount = amount * drain / 100;
    return (int) Math.min(Integer.MAX_VALUE, amount / 64);
  }
}
