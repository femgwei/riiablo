package com.riiablo.engine.server.missile;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Missiles;

/** The captured 1.10f Amazon poison javelins use 75% table speed and fp16 steps. */
public final class NativePoisonJavelinMotion {
  private NativePoisonJavelinMotion() {}

  public static boolean handles(Missiles.Entry row) {
    return row != null && ("poisonjav".equalsIgnoreCase(row.Missile)
        || "plaguejavelin".equalsIgnoreCase(row.Missile));
  }

  public static boolean usesCellOrigin(Missiles.Entry row) {
    return handles(row) || row != null && ("poisonjavcloud".equalsIgnoreCase(row.Missile)
        || "plaguejavcloud".equalsIgnoreCase(row.Missile));
  }

  public static Vector2 initialVelocity(Missiles.Entry row, int level, int slowPercent,
      Vector2 direction, Vector2 out) {
    int speed = Math.max(0, row.Vel + Math.max(1, level) * row.VelLev / 8) << 8;
    if (slowPercent > 0) speed = speed * slowPercent / 100;
    return NativePoisonMissileMotion.velocity(Vector2.Zero, direction, speed * 75 / 100, out);
  }
}
