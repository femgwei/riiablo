package com.riiablo.engine.server.missile;

import com.badlogic.gdx.math.Vector2;

/** D2Common PathMisc.cpp PATHTYPE_BLESSEDHAMMER waypoint construction. */
public final class NativeBlessedHammerPath {
  public static final int POINTS = 77;
  private static final float[] COS = new float[32];
  private static final float[] SIN = new float[32];
  static {
    // FOG's 512-angle LUT is sampled every sixteen entries by this path.
    for (int i = 0; i < 32; i++) {
      COS[i] = (float) Math.cos(i * (Math.PI / 16.0));
      SIN[i] = (float) Math.sin(i * (Math.PI / 16.0));
    }
    COS[0] = SIN[8] = 1f;
    COS[8] = SIN[16] = COS[24] = SIN[0] = 0f;
    COS[16] = SIN[24] = -1f;
  }

  private NativeBlessedHammerPath() {}

  /**
   * Returns accepted waypoint 1..77, not spiral iteration 1..77. Native
   * PATH_FromFP16 discards fractional coordinates and retries duplicate cells.
   * Host integer positions denote native cell centres, hence the +0.5 adapter.
   */
  public static Vector2 point(Vector2 origin, int pointIndex, Vector2 out) {
    if (out == null) out = new Vector2();
    if (origin == null || pointIndex <= 0) return out.set(origin == null ? Vector2.Zero : origin);
    if (pointIndex > POINTS) throw new IllegalArgumentException("hammer waypoint: " + pointIndex);
    long originX = Math.round((origin.x + 0.5) * 65536.0);
    long originY = Math.round((origin.y + 0.5) * 65536.0);
    long previousX = originX >> 16;
    long previousY = originY >> 16;
    int accepted = 0;
    for (int iteration = 1; ; iteration++) {
      int direction = iteration & 31;
      float distance = iteration * 9600f;
      // Native multiplies single precision, truncates to int64, then adds
      // the original precision coordinate before extracting its high word.
      long x = (originX + (long) (COS[direction] * distance)) >> 16;
      long y = (originY + (long) (SIN[direction] * distance)) >> 16;
      if (x == previousX && y == previousY) continue;
      previousX = x;
      previousY = y;
      if (++accepted == pointIndex) return out.set(x, y);
    }
  }
}
