package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Velocity;

@All({Box2DBody.class, Velocity.class})
public class Box2DSynchronizerPre extends IteratingSystem {
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Velocity> mVelocity;
  private final Vector2 movement = new Vector2();

  @Override
  protected void process(int entityId) {
    Body body = mBox2DBody.get(entityId).body;
    Vector2 velocity = resolveMovementVelocity(mVelocity.get(entityId), movement);
    body.setLinearVelocity(velocity);

    // FIXME: This is a temp fix to prevent shoving NPCs -- need to explore Contact Filters
    if (velocity.isZero()) {
      body.setType(BodyDef.BodyType.StaticBody);
    } else {
      body.setType(BodyDef.BodyType.DynamicBody);
    }
  }

  static Vector2 resolveMovementVelocity(Velocity requested, Vector2 out) {
    return out.set(requested.velocity).scl(requested.movementMultiplier());
  }
}
