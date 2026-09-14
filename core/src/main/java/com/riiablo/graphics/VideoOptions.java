package com.riiablo.graphics;

/** Shared display option semantics used by the video options menu. */
public final class VideoOptions {
  public static final float MIN_GAMMA = 0.5f;
  public static final float MAX_GAMMA = 4.0f;
  public static final float GAMMA_STEP = 0.1f;

  private VideoOptions() {}

  public static float normalizeGamma(float gamma) {
    if (Float.isNaN(gamma)) return 1.0f;
    if (gamma <= MIN_GAMMA) return MIN_GAMMA;
    if (gamma >= MAX_GAMMA) return MAX_GAMMA;
    return gamma;
  }

  /** Advances gamma by 10 percentage points and wraps 400% back to 50%. */
  public static float nextGamma(float gamma) {
    float normalized = normalizeGamma(gamma);
    int step = Math.round((normalized - MIN_GAMMA) / GAMMA_STEP);
    int maxStep = Math.round((MAX_GAMMA - MIN_GAMMA) / GAMMA_STEP);
    if (step >= maxStep) return MIN_GAMMA;
    return MIN_GAMMA + (step + 1) * GAMMA_STEP;
  }

  public static String gammaLabel(float gamma) {
    return Math.round(normalizeGamma(gamma) * 100f) + "%";
  }
}
