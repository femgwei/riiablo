package com.riiablo.engine.server.combat;

/** Resource deltas only; eligibility, units, ordering and writes belong to the host. */
public final class NativeResourceMath {
  private NativeResourceMath() {}

  /** Clamp the requested delta before adding it (absorb application convention). */
  public static float cappedRestore(float current, float maximum, float requested) {
    return Math.max(0f, Math.min(requested, maximum - current));
  }

  /** Add before clamping (legacy melee resource convention). Keep float rounding order. */
  public static float restoreAfterAddition(float current, float maximum, float requested) {
    float after = Math.min(maximum, current + requested);
    return after <= current ? 0f : after - current;
  }
}
