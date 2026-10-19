package com.riiablo.engine.server.missile;

import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.Size;

/** D2MOO MISSMODE_SrvDo11: short, sign-based path corrections, not an orbit. */
public final class FingerMageFlight {
  private FingerMageFlight() {}

  public static void update(World world, int entityId, Missile missile,
      Vector2 position, Vector2 velocity, int elapsedFrame) {
    if (missile.missile == null || missile.missile.pSrvDoFunc != 11) return;
    int[] params = missile.missile.Param;
    // Synthetic/partial rows have no native fixed-size parameter array. Keep
    // their collision/lifetime processing, but do not invent homing settings.
    if (params == null || params.length < 2) return;
    int interval = params[0] > 0 ? params[0] : 5;
    if ((missile.nativeLifetimeFrames - elapsedFrame) % interval != 0) return;
    int targetId = missile.targetId;
    if (targetId < 0 && missile.ownerId >= 0) {
      Target target = world.getMapper(Target.class).get(missile.ownerId);
      if (target != null) targetId = target.target;
    }
    Position target = targetId >= 0 ? world.getMapper(Position.class).get(targetId) : null;
    if (target == null) return;
    Size size = world.getMapper(Size.class).get(targetId);
    int halfSize = (size == null ? 0 : size.size) / 2;
    int dx = Math.max(0, Math.abs((int) target.position.x - (int) position.x) - halfSize);
    int dy = Math.max(0, Math.abs((int) target.position.y - (int) position.y) - halfSize);
    // UNITS_GetDistanceToOtherUnit uses this size-adjusted integer approximation.
    if ((Math.min(dx, dy) + 2 * Math.max(dx, dy)) / 2 > params[1]) return;
    Vector2 step = new Vector2(Math.signum((int) target.position.x - (int) position.x),
        Math.signum((int) target.position.y - (int) position.y));
    if (step.isZero()) return;
    float speed = velocity.len();
    velocity.set(step).setLength(speed);
    Angle angle = world.getMapper(Angle.class).get(entityId);
    if (angle != null) angle.set(step.nor());
  }
}
