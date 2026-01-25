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
  public Iterator<Vector2> targets = Collections.emptyIterator();
  public final Vector2 target = new Vector2();
  
  // Track target entity for dynamic repathing (e.g., following player)
  public int targetEntityId = com.riiablo.engine.Engine.INVALID_ENTITY;
  public final Vector2 lastTargetPosition = new Vector2();
  public float repathTimer = 0f;  // Timer for periodic repathing when following player
  public static final float REPATH_INTERVAL = 0.5f;  // Repath every 0.5 seconds when following player

  public void reset() {
    path = null;
    target.setZero();
    targets = Collections.emptyIterator();
    targetEntityId = com.riiablo.engine.Engine.INVALID_ENTITY;
    lastTargetPosition.setZero();
    repathTimer = 0f;
  }

  public Pathfind set(GraphPath path) {
    this.path = path;
    targets = path.vectorIterator();
    Vector2 position = targets.next();
    target.set(targets.hasNext() ? targets.next() : position);
    return this;
  }
}
