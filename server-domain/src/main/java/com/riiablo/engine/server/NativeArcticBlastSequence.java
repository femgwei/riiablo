package com.riiablo.engine.server;

/** 1.10f D2Common+9FE10: fifteen S1 points, one action at point ten. */
public final class NativeArcticBlastSequence {
  public static final int LENGTH = 15 << 8;
  public static final int ACTION = 10 << 8;
  public static final int LOOP = 8 << 8;
  public static final int LOOP_REMAINING = 5 << 8;
  private static final int[] FRAMES = {0,1,2,3,4,5,6,7,8,9,9,10,11,12,13};
  private NativeArcticBlastSequence() {}
  public static int sourceFrame(int cursor) {
    return FRAMES[Math.max(0, Math.min(FRAMES.length - 1, cursor >>> 8))];
  }
}
