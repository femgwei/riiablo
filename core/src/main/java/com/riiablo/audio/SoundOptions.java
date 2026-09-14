package com.riiablo.audio;

/** Shared sound option semantics used by the options menu. */
public final class SoundOptions {
  public static final int VOLUME_STEP_PERCENT = 10;

  private SoundOptions() {}

  public static float normalizeVolume(float volume) {
    if (Float.isNaN(volume) || volume <= 0f) return 0f;
    if (volume >= 1f) return 1f;
    return volume;
  }

  /** Advances through 0%, 10%, ..., 100%, then wraps to 0%. */
  public static float nextVolume(float volume) {
    int percent = Math.round(normalizeVolume(volume) * 100f);
    if (percent >= 100) return 0f;
    int nextPercent = Math.min(100,
        ((percent / VOLUME_STEP_PERCENT) + 1) * VOLUME_STEP_PERCENT);
    return nextPercent / 100f;
  }

  public static String percentageLabel(float volume) {
    return Math.round(normalizeVolume(volume) * 100f) + "%";
  }
}
