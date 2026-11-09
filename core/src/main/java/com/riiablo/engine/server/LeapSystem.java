package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Leap;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;

/** Advances native Leap movement while ordinary ground collision is suspended. */
@All({Leap.class, Position.class})
public class LeapSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(LeapSystem.class);

  protected ComponentMapper<Leap> mLeap;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<com.riiablo.engine.server.component.Sequence> mSequence;

  private final boolean authoritative;

  public LeapSystem() { this(true); }

  public LeapSystem(boolean authoritative) { this.authoritative = authoritative; }

  public boolean isAuthoritative() { return authoritative; }

  @Override
  protected void process(int entityId) {
    if (!authoritative) return;
    Leap leap = mLeap.get(entityId);
    com.riiablo.engine.server.component.Sequence sequence = mSequence.get(entityId);
    boolean playerLeap = sequence != null && sequence.nativeLeap;
    if (playerLeap && (!sequence.started
        || sequence.nativeLeapPoint < NativePlayerLeapSequence.TAKEOFF)) {
      if (mVelocity.has(entityId)) mVelocity.get(entityId).velocity.setZero();
      return;
    }
    if (leap.nativeTick != null || NativeLeapMotion.supports(world,entityId,leap)) {
      boolean active=NativeLeapMotion.advance(world,entityId,leap);
      if (mBox2DBody.has(entityId) && mBox2DBody.get(entityId).body != null) {
        mBox2DBody.get(entityId).body.setTransform(mPosition.get(entityId).position,0f);
        mBox2DBody.get(entityId).body.setLinearVelocity(0f,0f);
      }
      if (!active) {
        mLeap.remove(entityId);
        DynamicUnitCollisionSystem collision=world.getSystem(DynamicUnitCollisionSystem.class);
        if(collision!=null) collision.synchronizeNativeCollision(entityId);
      }
      return;
    }
    leap.elapsed = Math.min(leap.duration, leap.elapsed + Math.max(0f, world.delta));
    float alpha = MathUtils.clamp(leap.elapsed / leap.duration, 0f, 1f);
    // Like the reference client, move linearly during flight. The S1 frames
    // supply the vertical movement, with ground Position kept for collision.
    float smooth = playerLeap ? alpha : alpha * alpha * (3f - 2f * alpha);
    Position position = mPosition.get(entityId);
    position.invalidateNativePrecision();
    position.position.set(leap.start).lerp(leap.destination, smooth);
    if (mVelocity.has(entityId)) mVelocity.get(entityId).velocity.setZero();
    if (mBox2DBody.has(entityId) && mBox2DBody.get(entityId).body != null) {
      mBox2DBody.get(entityId).body.setTransform(position.position, 0f);
      mBox2DBody.get(entityId).body.setLinearVelocity(0f, 0f);
    }
    if (alpha < 1f) return;

    log.info("[MONSTER_LEAP] phase=land entity={} monster={} target={} destination=({}, {}) duration={}",
        entityId,
        mMonster.has(entityId) && mMonster.get(entityId).monstats != null
            ? mMonster.get(entityId).monstats.Id : "unknown",
        leap.targetId, leap.destination.x, leap.destination.y, leap.duration);
    mLeap.remove(entityId);
    if (playerLeap) {
      // A short flight can arrive before the S1 cursor reaches the hold.
      // Resume at the landing point on this tick instead of sliding along
      // the ground while the remaining airborne frames play out.
      sequence.nativeLeapPoint = Math.max(sequence.nativeLeapPoint, NativePlayerLeapSequence.FLIGHT);
    }
    net.mostlyoriginal.api.event.common.EventSystem events =
        world.getSystem(net.mostlyoriginal.api.event.common.EventSystem.class);
    if (playerLeap && events != null) {
      events.dispatch(com.riiablo.engine.server.event.LeapLandEvent.obtain(entityId));
    }
  }

  /** Finds the closest walkable landing subtile using the native three-pass intent. */
  public static boolean findLanding(Map map, Vector2 desired, int unitSize, Vector2 result) {
    if (map == null || desired == null || result == null) return false;
    int centerX = MathUtils.round(desired.x);
    int centerY = MathUtils.round(desired.y);
    int footprint = Math.max(0, unitSize);
    for (int radius = 0; radius <= 2; radius++) {
      for (int y = -radius; y <= radius; y++) {
        for (int x = -radius; x <= radius; x++) {
          if (radius > 0 && Math.abs(x) != radius && Math.abs(y) != radius) continue;
          int candidateX = centerX + x;
          int candidateY = centerY + y;
          if (walkable(map, candidateX, candidateY, footprint)) {
            result.set(candidateX, candidateY);
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean walkable(Map map, int x, int y, int size) {
    int radius = Math.max(0, size - 1);
    for (int dy = -radius; dy <= radius; dy++) {
      for (int dx = -radius; dx <= radius; dx++) {
        if ((map.flags(x + dx, y + dy) & DT1.Tile.FLAG_BLOCK_WALK) != 0) return false;
      }
    }
    return true;
  }
}
