package com.riiablo.engine.server;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Box2DSynchronizerPostTest {
  @Test
  void missilesKeepAuthoritativeFlightVelocity() {
    assertTrue(Box2DSynchronizerPost.shouldResolveActualVelocity(true, false));
    assertFalse(Box2DSynchronizerPost.shouldResolveActualVelocity(true, true));
  }

  @Test
  void stoppedBodyClearsRequestedVelocity() {
    Vector2 velocity = new Vector2(9f, 0f);

    Box2DSynchronizerPost.resolveActualVelocity(
        new Vector2(10f, 20f), new Vector2(10f, 20f), 0.04f, velocity);

    assertTrue(velocity.isZero());
  }

  @Test
  void reportsPostCollisionDisplacementAsVelocity() {
    Vector2 velocity = new Vector2();

    Box2DSynchronizerPost.resolveActualVelocity(
        new Vector2(10f, 20f), new Vector2(10.12f, 20.16f), 0.04f, velocity);

    assertEquals(3f, velocity.x, 0.0001f);
    assertEquals(4f, velocity.y, 0.0001f);
  }

  @Test
  void ignoresSolverScalePositionJitter() {
    Vector2 velocity = new Vector2(9f, 0f);

    Box2DSynchronizerPost.resolveActualVelocity(
        new Vector2(10f, 20f), new Vector2(10.0005f, 20f), 0.04f, velocity);

    assertTrue(velocity.isZero());
  }
}
