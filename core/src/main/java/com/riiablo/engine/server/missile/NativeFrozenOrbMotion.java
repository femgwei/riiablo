package com.riiablo.engine.server.missile;

import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.path.D2PathStep;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Position;

/** Frozen Orb's captured 1.10f path units: 25 Hz, integer direction, fp16 step. */
public final class NativeFrozenOrbMotion {
  private NativeFrozenOrbMotion() {}

  public static boolean handles(Missiles.Entry row) {
    return row != null && ("frozenorb".equalsIgnoreCase(row.Missile)
        || "frozenorbbolt".equalsIgnoreCase(row.Missile)
        || "frozenorbnova".equalsIgnoreCase(row.Missile));
  }

  public static int nativeVelocity(Missiles.Entry row, int level, int slowPercent) {
    int speed = Math.max(0, row.Vel + Math.max(1, level) * row.VelLev / 8) << 8;
    if (slowPercent > 0) speed = speed * slowPercent / 100;
    return speed * 75 / 100;
  }

  public static Vector2 initialVelocity(Vector2 direction, int speed, Vector2 out) {
    return NativePoisonMissileMotion.velocity(Vector2.Zero, direction, speed, out);
  }

  /** Native PathSetTargetPos uses the current integer cell, not its fractional position. */
  public static Vector2 turnVelocity(Position position, int dx, int dy, int speed, Vector2 out) {
    int x = position.hasNativePrecision() ? position.nativePrecisionX()
        : NativePoisonMissileMotion.precision(position.position.x);
    int y = position.hasNativePrecision() ? position.nativePrecisionY()
        : NativePoisonMissileMotion.precision(position.position.y);
    int targetX = (x & 0xFFFF0000) + (dx << 16) + 0x8000;
    int targetY = (y & 0xFFFF0000) + (dy << 16) + 0x8000;
    D2PathStep.DirectionVector direction = D2PathStep.directionVector(
        x, y, targetX, targetY);
    return out.set(((direction.x * speed) >> 8) * (25f / 65536f),
        ((direction.y * speed) >> 8) * (25f / 65536f));
  }
}
