package com.riiablo.engine;

import com.riiablo.codec.Animation;

/** Shared native Diablo II simulation clock. */
public final class SimulationClock {
  public static final int TICKS_PER_SECOND = (int) Animation.FRAMES_PER_SECOND;
  public static final float STEP_SECONDS = Animation.FRAME_DURATION;
  public static final int STEP_MILLIS = Math.round(STEP_SECONDS * 1000f);

  private SimulationClock() {}
}
