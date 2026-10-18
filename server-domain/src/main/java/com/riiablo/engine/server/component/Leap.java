package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.EntityId;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;

/** Server-authoritative in-flight state for native Leap skills. */
@Transient
@PooledWeaver
public class Leap extends PooledComponent {
  public final Vector2 start = new Vector2();
  public final Vector2 destination = new Vector2();
  public float elapsed;
  public float duration;
  /** Non-null only when native precision and a native Run table velocity are available. */
  public com.d2moo.common.path.D2PathTick.State nativeTick;
  @EntityId public int targetId = Engine.INVALID_ENTITY;

  public Leap set(Vector2 start, Vector2 destination, float duration, int targetId) {
    this.start.set(start);
    this.destination.set(destination);
    this.elapsed = 0f;
    nativeTick = null;
    this.duration = Math.max(0.001f, duration);
    this.targetId = targetId;
    return this;
  }

  @Override
  protected void reset() {
    start.setZero();
    destination.setZero();
    elapsed = 0f;
    nativeTick = null;
    duration = 0f;
    targetId = Engine.INVALID_ENTITY;
  }
}
