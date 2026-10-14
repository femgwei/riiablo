package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Velocity;

@All({Box2DBody.class, Velocity.class})
public class Box2DSynchronizerPre extends IteratingSystem {
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<com.riiablo.engine.server.component.WhirlwindRuntime> mWhirlwindRuntime;
  private final Vector2 movement = new Vector2();

  @Override
  protected void process(int entityId) {
    Box2DBody wrapper = mBox2DBody.get(entityId);
    Body body = wrapper.body;
    if (body == null) return;
    applySkillMoveCollision(wrapper, mWhirlwindRuntime.has(entityId));
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

  /** The skill's swept native mask owns terrain tests while WW is active. */
  static void applySkillMoveCollision(Box2DBody wrapper, boolean active) {
    if (active) {
      for (Fixture fixture : wrapper.body.getFixtureList()) {
        if (wrapper.skillMoveFixtures.contains(fixture, true)) continue;
        wrapper.skillMoveFixtures.add(fixture);
        wrapper.skillMoveSensors.add(fixture.isSensor());
        fixture.setSensor(true);
      }
    } else {
      for (int i = 0; i < wrapper.skillMoveFixtures.size; i++) {
        Fixture fixture = wrapper.skillMoveFixtures.get(i);
        if (wrapper.body.getFixtureList().contains(fixture, true)) {
          fixture.setSensor(wrapper.skillMoveSensors.get(i));
        }
      }
      wrapper.skillMoveFixtures.clear();
      wrapper.skillMoveSensors.clear();
    }
  }
}
