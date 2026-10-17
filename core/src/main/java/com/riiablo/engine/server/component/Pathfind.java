package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.map.pfa.GraphPath;

import java.util.Collections;
import java.util.Iterator;

@Transient
@PooledWeaver
public class Pathfind extends PooledComponent {
  public GraphPath path;
  /** A bounded native path must stop on obstruction rather than turn into A*. */
  public boolean nativeBounded;
  public int nativePathType;
  public Iterator<Vector2> targets = Collections.emptyIterator();
  public final Vector2 target = new Vector2();
  /** Final reachable destination used by network clients to send intent, not waypoints. */
  public final Vector2 destination = new Vector2();
  
  // Track target entity for dynamic repathing (e.g., following player)
  public int targetEntityId = com.riiablo.engine.Engine.INVALID_ENTITY;
  /** Null means a legacy/unmanaged target whose allocation identity is unknown. */
  public NativePathIdentity.Token targetIdentity;
  public final Vector2 lastTargetPosition = new Vector2();
  public float repathTimer = 0f;  // Timer for periodic repathing when following player
  public static final float REPATH_INTERVAL = 0.5f;  // Repath every 0.5 seconds when following player

  // Adjacent animation directions require a few consistent movement ticks.
  // This prevents repeated repaths from flipping a unit across a direction boundary.
  public int pendingDirection = -1;
  public int pendingDirectionFrames;

  /** Time spent requesting movement without making authoritative progress. */
  public float stalledTime;
  /** Whether the most recent stalled tick was rejected by dynamic-unit collision. */
  public boolean blockedByDynamic;
  public String movementBlockReason = "none";

  public void reset() {
    nativeBounded = false;
    nativePathType = 0;
    path = null;
    target.setZero();
    destination.setZero();
    targets = Collections.emptyIterator();
    targetEntityId = com.riiablo.engine.Engine.INVALID_ENTITY;
    targetIdentity = null;
    lastTargetPosition.setZero();
    repathTimer = 0f;
    pendingDirection = -1;
    pendingDirectionFrames = 0;
    stalledTime = 0f;
    blockedByDynamic = false;
    movementBlockReason = "none";
  }

  public Pathfind set(GraphPath path) {
    return set(path, false);
  }

  /** Retain the source centre when a fractional start cannot sweep to node two. */
  public Pathfind set(GraphPath path, boolean startAtSource) {
    nativeBounded = false;
    nativePathType = 0;
    this.path = path;
    targets = path.vectorIterator();
    Vector2 position = targets.next();
    target.set(!startAtSource && targets.hasNext() ? targets.next() : position);
    destination.set(path.getNodePosition(path.getCount() - 1));
    stalledTime = 0f;
    blockedByDynamic = false;
    movementBlockReason = "none";
    return this;
  }

  public void recordMovement(
      boolean requested, float distance2, float delta, boolean dynamicBlocked) {
    if (requested && distance2 <= 0.000001f && delta > 0f) {
      stalledTime += delta;
      blockedByDynamic = dynamicBlocked;
    } else {
      stalledTime = 0f;
      blockedByDynamic = false;
    }
  }
}
