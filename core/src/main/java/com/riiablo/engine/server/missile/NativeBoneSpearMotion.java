package com.riiablo.engine.server.missile;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Missiles;

/** Bone Spear's 1.10f missile path: table velocity, 75% scale, fp16 at 25 Hz. */
public final class NativeBoneSpearMotion {
  private NativeBoneSpearMotion() {}

  public static boolean handles(Missiles.Entry row) {
    return row != null && "bonespear".equalsIgnoreCase(row.Missile);
  }

  public static Vector2 initialVelocity(Missiles.Entry row, int level, int slowPercent,
      Vector2 direction, Vector2 out) {
    int speed = Math.max(0, row.Vel + Math.max(1, level) * row.VelLev / 8) << 8;
    if (slowPercent > 0) speed = speed * slowPercent / 100;
    speed = speed * 75 / 100;
    return NativePoisonMissileMotion.velocity(Vector2.Zero, direction, speed, out);
  }
}
