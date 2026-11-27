package com.riiablo.engine.server.missile;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Missiles;

/** Native 1.10f Bone Spirit (SrvDo010 / guided pSrvDoFunc=7) motion. */
public final class NativeBoneSpiritMotion {
  private NativeBoneSpiritMotion() {}

  public static boolean handles(Missiles.Entry row) {
    return row != null && "bonespirit".equalsIgnoreCase(row.Missile);
  }

  /** MISSILES_CreateMissileFromParams applies 75% before path fp16 stepping. */
  public static Vector2 initialVelocity(Missiles.Entry row, int level, int slowPercent,
      Vector2 direction, Vector2 out) {
    int speed = Math.max(0, row.Vel + Math.max(1, level) * row.VelLev / 8) << 8;
    if (slowPercent > 0) speed = speed * slowPercent / 100;
    return NativePoisonMissileMotion.velocity(Vector2.Zero, direction, speed * 75 / 100, out);
  }
}
