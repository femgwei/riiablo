package com.riiablo.engine.server.missile;

import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.path.D2PathStep;
import com.riiablo.engine.server.component.Position;

/** D2Common Step.cpp/PathMisc.cpp poison-cloud path adapter (25 Hz, fp16). */
public final class NativePoisonMissileMotion {
  private NativePoisonMissileMotion() {}

  // Legacy world integer positions denote native cell centres. This conversion
  // explicitly creates a presentation/path precision value; it does not recover
  // precision discarded by an older network sender.
  public static int precision(float world) {
    return (int) Math.round((world + 0.5) * 65536.0);
  }

  /** Native (direction * velocity) >> 8, NOT a normalized float vector. */
  public static Vector2 velocity(Vector2 from, Vector2 target, int nativeVelocity,
      Vector2 out) {
    // Direction depends only on the displacement. Translate to a safe native
    // origin so legacy/synthetic negative world targets do not look like a
    // uint32 wrap to the opposite edge of the map.
    int dxPrecision = precision(target.x) - precision(from.x);
    int dyPrecision = precision(target.y) - precision(from.y);
    if (dxPrecision == 0 && dyPrecision == 0) return out.setZero();
    D2PathStep.DirectionVector direction = D2PathStep.directionVector(
        0x40000000, 0x40000000, 0x40000000 + dxPrecision, 0x40000000 + dyPrecision);
    int dx = (direction.x * nativeVelocity) >> 8;
    int dy = (direction.y * nativeVelocity) >> 8;
    return out.set(dx * (25f / 65536f), dy * (25f / 65536f));
  }

  public static int frameStep(float perSecond) {
    return Math.round(perSecond * (65536f / 25f));
  }

  /** PATHTYPE_MISSILE never clamps or stops at the direction-reference target. */
  public static void advance(Position position, Vector2 velocity, int frames) {
    if (!position.hasNativePrecision()) {
      // Legacy map coordinates can be negative. They are NOT raw uint32
      // native coordinates: converting them into Position's unsigned adapter
      // teleports smoke to the opposite map edge and makes it disappear.
      position.position.set(advanceLegacy(position.position.x, velocity.x, frames),
          advanceLegacy(position.position.y, velocity.y, frames));
      return;
    }
    int x = position.nativePrecisionX();
    int y = position.nativePrecisionY();
    position.setNativePrecision(x + frameStep(velocity.x) * frames,
        y + frameStep(velocity.y) * frames);
  }

  public static float advanceLegacy(float world, float perSecond, int frames) {
    return (float) ((Math.round(world * 65536.0) + (long) frameStep(perSecond) * frames) / 65536.0);
  }
}
