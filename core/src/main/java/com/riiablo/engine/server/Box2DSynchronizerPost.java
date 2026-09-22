package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Size;

@All({Box2DBody.class, Position.class})
public class Box2DSynchronizerPost extends IteratingSystem {
  static final float STOPPED_EPSILON = 0.001f;

  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Size> mSize;
  protected DynamicUnitCollisionSystem dynamicCollision;

  @Override
  protected void process(int entityId) {
    Body body = mBox2DBody.get(entityId).body;
    Vector2 previous = mPosition.get(entityId).position;
    Vector2 current = body.getPosition();
    float oldX = previous.x;
    float oldY = previous.y;
    float resolvedX = current.x;
    float resolvedY = current.y;
    if (dynamicCollision != null && isCollidableUnit(entityId)
        && !dynamicCollision.tryMove(entityId, resolvedX, resolvedY)) {
      // Preserve D2's axis-slide behaviour while keeping the rejected unit
      // out of the other unit's footprint.  No Box2D impulse is applied.
      boolean moved = false;
      float dx = resolvedX - oldX;
      float dy = resolvedY - oldY;
      if (Math.abs(dx) >= Math.abs(dy)) {
        moved = dynamicCollision.tryMove(entityId, resolvedX, oldY);
        if (moved) { resolvedY = oldY; }
        if (!moved) {
          moved = dynamicCollision.tryMove(entityId, oldX, resolvedY);
          if (moved) { resolvedX = oldX; }
        }
      } else {
        moved = dynamicCollision.tryMove(entityId, oldX, resolvedY);
        if (moved) { resolvedX = oldX; }
        if (!moved) {
          moved = dynamicCollision.tryMove(entityId, resolvedX, oldY);
          if (moved) { resolvedY = oldY; }
        }
      }
      if (!moved) {
        resolvedX = oldX;
        resolvedY = oldY;
        // The failed tryMove restores the old grid footprint.
        dynamicCollision.tryMove(entityId, com.riiablo.map.Map.round(oldX),
            com.riiablo.map.Map.round(oldY), mSize.get(entityId).size);
      }
      body.setTransform(resolvedX, resolvedY, body.getAngle());
      current = body.getPosition();
    }
    // MissileCollisionSystem advances missiles before the Box2D phase. Their
    // bodies merely catch up to that new position, so interpreting the zero
    // delta here as a collision would erase their authoritative flight speed.
    if (shouldResolveActualVelocity(mVelocity.has(entityId), mMissile.has(entityId))) {
      resolveActualVelocity(previous, current, world.delta,
          mVelocity.get(entityId).velocity);
      // Pathfinder computes the requested direction before collision
      // resolution. If a unit slides along another unit or a wall, use the
      // displacement that actually happened so the animation does not turn
      // back and forth against the blocked path.
      if (mAngle.has(entityId)
          && !mVelocity.get(entityId).velocity.isZero(STOPPED_EPSILON)) {
        mAngle.get(entityId).target.set(mVelocity.get(entityId).velocity).nor();
      }
    }
    previous.set(current);
  }

  private boolean isCollidableUnit(int entityId) {
    if (!mClass.has(entityId) || !mSize.has(entityId)) return false;
    Class.Type type = mClass.get(entityId).type;
    return type == Class.Type.MON || type == Class.Type.PLR;
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
