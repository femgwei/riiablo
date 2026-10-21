package com.riiablo.engine.client;

import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Position;

/** Copy-only view shared by GPU consumers; never lends an ECS or sampler vector. */
public final class ClientTransformView {
  private ClientTransformView() {}

  public static Vector2 position(World world, int entityId, Vector2 out) {
    AuthoritativeInterpolationSystem interpolation =
        world.getSystem(AuthoritativeInterpolationSystem.class);
    return interpolation != null ? interpolation.position(entityId, out)
        : out.set(world.getMapper(Position.class).get(entityId).position);
  }

  public static Vector2 angle(World world, int entityId, Vector2 out) {
    AuthoritativeInterpolationSystem interpolation =
        world.getSystem(AuthoritativeInterpolationSystem.class);
    return interpolation != null ? interpolation.angle(entityId, out)
        : out.set(world.getMapper(Angle.class).get(entityId).angle);
  }
}
