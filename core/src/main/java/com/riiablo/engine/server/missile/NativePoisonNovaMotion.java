package com.riiablo.engine.server.missile;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Missiles;

/** Native 1.10f Poison Nova missile path (SrvDo022 / poisonnova). */
public final class NativePoisonNovaMotion {
  private NativePoisonNovaMotion() {}

  public static boolean handles(Missiles.Entry row) {
    return row != null && "poisonnova".equalsIgnoreCase(row.Missile);
  }

  /** MISSILES_CreateMissileFromParams applies the native 75% velocity rule. */
  public static Vector2 initialVelocity(Missiles.Entry row, int level, int slowPercent,
      Vector2 direction, Vector2 out) {
    int speed = Math.max(0, row.Vel + Math.max(1, level) * row.VelLev / 8) << 8;
    if (slowPercent > 0) speed = speed * slowPercent / 100;
    return NativePoisonMissileMotion.velocity(Vector2.Zero, direction, speed * 75 / 100, out);
  }
}
