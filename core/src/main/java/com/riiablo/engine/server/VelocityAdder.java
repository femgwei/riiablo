package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Missile;

@All({Position.class, Velocity.class})
public class VelocityAdder extends IteratingSystem {
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Missile> mMissile;

  @Override
  protected void process(int entityId) {
    // MissileCollisionSystem performs swept movement and collision resolution
    // in one place. Moving missiles here as well would advance them twice in
    // the server pipeline (and make range/collision results frame-dependent).
    if (mMissile.has(entityId)) return;
    mPosition.get(entityId).position.mulAdd(mVelocity.get(entityId).velocity, world.delta);
  }
}
