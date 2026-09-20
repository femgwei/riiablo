package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;

@All({Box2DBody.class, Position.class})
public class Box2DSynchronizerPost extends IteratingSystem {
  static final float STOPPED_EPSILON = 0.001f;

  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;

  @Override
  protected void process(int entityId) {
    Body body = mBox2DBody.get(entityId).body;
    Vector2 previous = mPosition.get(entityId).position;
    Vector2 current = body.getPosition();
    // MissileCollisionSystem advances missiles before the Box2D phase. Their
    // bodies merely catch up to that new position, so interpreting the zero
    // delta here as a collision would erase their authoritative flight speed.
    if (shouldResolveActualVelocity(mVelocity.has(entityId), mMissile.has(entityId))) {
      resolveActualVelocity(previous, current, world.delta,
          mVelocity.get(entityId).velocity);
    }
    previous.set(current);
  }

  static boolean shouldResolveActualVelocity(boolean hasVelocity, boolean missile) {
    return hasVelocity && !missile;
  }

  static Vector2 resolveActualVelocity(
      Vector2 previous, Vector2 current, float delta, Vector2 out) {
    if (previous == null || current == null || out == null || delta <= 0f) {
      return out == null ? null : out.setZero();
    }
    out.set(current).sub(previous);
    if (out.isZero(STOPPED_EPSILON)) return out.setZero();
    return out.scl(1f / delta);
  }
}
