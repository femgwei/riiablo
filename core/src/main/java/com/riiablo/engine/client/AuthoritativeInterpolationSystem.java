package com.riiablo.engine.client;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.Direction;

/** Applies authoritative transform interpolation only around GPU rendering. */
public final class AuthoritativeInterpolationSystem extends BaseSystem {
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;

  private final IntMap<Entry> entries = new IntMap<>();
  private final IntArray applied = new IntArray();
  private final Vector2 tmp = new Vector2();
  private boolean rendering;

  @Override
  protected void processSystem() {
    // Snapshot recording happens in ClientNetworkReceiver. Simulation uses
    // authoritative Position/Angle directly and needs no interpolation work.
  }

  public boolean record(int entityId, long tick, long serverTimeMillis,
                        boolean positionPresent, float x, float y,
                        boolean anglePresent, float angleX, float angleY) {
    return record(entityId, tick, serverTimeMillis, positionPresent, x, y,
        anglePresent, angleX, angleY, false);
  }

  public boolean record(int entityId, long tick, long serverTimeMillis,
                        boolean positionPresent, float x, float y,
                        boolean anglePresent, float angleX, float angleY,
                        boolean forceSnap) {
    Entry entry = entries.get(entityId);
    if (entry == null) {
      entry = new Entry();
      entries.put(entityId, entry);
    }
    return entry.buffer.record(tick, serverTimeMillis,
        positionPresent, x, y, anglePresent, angleX, angleY, forceSnap);
  }

  public void remove(int entityId) {
    entries.remove(entityId);
  }

  public void beginRender(float renderDelta) {
    if (rendering) throw new IllegalStateException("render interpolation already active");
    rendering = true;
    applied.clear();
    for (IntMap.Entry<Entry> mapEntry : entries) {
      int entityId = mapEntry.key;
      Entry entry = mapEntry.value;
      entry.buffer.advance(renderDelta);
      boolean changed = false;
      if (entry.buffer.hasPosition() && mPosition.has(entityId)) {
        Position position = mPosition.get(entityId);
        entry.authoritativePosition.set(position.position);
        position.position.set(entry.buffer.samplePosition(tmp));
        entry.positionApplied = true;
        changed = true;
      }
      if (entry.buffer.hasAngle() && mAngle.has(entityId)) {
        Angle angle = mAngle.get(entityId);
        entry.authoritativeAngle.set(angle.angle);
        entry.authoritativeTarget.set(angle.target);
        tmp.set(entry.buffer.sampleAngle(tmp));
        angle.angle.set(tmp);
        angle.target.set(tmp);
        entry.angleApplied = true;
        if (mAnimationWrapper.has(entityId)) {
          com.riiablo.codec.Animation animation =
              mAnimationWrapper.get(entityId).animation;
          if (animation.getNumDirections() > 0) {
            entry.authoritativeDirection = animation.getDirection();
            animation.setDirection(Direction.radiansToDirection(
                tmp.angleRad(), animation.getNumDirections()));
            entry.directionApplied = true;
          }
        }
        changed = true;
      }
      if (changed) applied.add(entityId);
    }
  }

  public void endRender() {
    if (!rendering) return;
    for (int i = 0; i < applied.size; i++) {
      Entry entry = entries.get(applied.get(i));
      if (entry == null) continue;
      int entityId = applied.get(i);
      if (entry.positionApplied && mPosition.has(entityId)) {
        mPosition.get(entityId).position.set(entry.authoritativePosition);
      }
      if (entry.angleApplied && mAngle.has(entityId)) {
        Angle angle = mAngle.get(entityId);
        angle.angle.set(entry.authoritativeAngle);
        angle.target.set(entry.authoritativeTarget);
      }
      if (entry.directionApplied && mAnimationWrapper.has(entityId)) {
        com.riiablo.codec.Animation animation =
            mAnimationWrapper.get(entityId).animation;
        if (animation.getNumDirections() > 0) {
          animation.setDirection(entry.authoritativeDirection);
        }
      }
      entry.positionApplied = false;
      entry.angleApplied = false;
      entry.directionApplied = false;
    }
    applied.clear();
    rendering = false;
  }

  public int trackedEntities() {
    return entries.size;
  }

  private static final class Entry {
    final AuthoritativeTransformInterpolator buffer =
        new AuthoritativeTransformInterpolator();
    final Vector2 authoritativePosition = new Vector2();
    final Vector2 authoritativeAngle = new Vector2();
    final Vector2 authoritativeTarget = new Vector2();
    boolean positionApplied;
    boolean angleApplied;
    boolean directionApplied;
    int authoritativeDirection;
  }
}
