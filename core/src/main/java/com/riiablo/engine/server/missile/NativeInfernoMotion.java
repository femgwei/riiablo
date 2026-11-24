package com.riiablo.engine.server.missile;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Missiles;

/** Native CltDo24/SrvDo19 stream velocity for Inferno and Arctic Blast. */
public final class NativeInfernoMotion {
  private NativeInfernoMotion() {}
  public static boolean handles(Missiles.Entry row) {
    return row != null && ("infernoflame1".equalsIgnoreCase(row.Missile)
        || "infernoflame2".equalsIgnoreCase(row.Missile)
        || "arcticblast1".equalsIgnoreCase(row.Missile)
        || "arcticblast2".equalsIgnoreCase(row.Missile));
  }
  public static Vector2 velocity(Missiles.Entry row, int level, Vector2 facing, Vector2 out) {
    int speed = (Math.max(0, row.Vel + Math.max(1, level) * row.VelLev / 8) << 8) * 75 / 100;
    return NativePoisonMissileMotion.velocity(Vector2.Zero, facing, speed, out);
  }
}
