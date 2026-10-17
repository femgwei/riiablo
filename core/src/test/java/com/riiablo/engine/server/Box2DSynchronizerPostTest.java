package com.riiablo.engine.server;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Box2DSynchronizerPostTest {
  @Test
  void unchangedBodyProjectionPreservesRawLowBits() {
    Position position = new Position();
    position.setNativePrecision((10 << 16) | 0x8001, (20 << 16) | 0x8002);
    Box2DSynchronizerPost.commitBodyPosition(position, new Vector2(position.position));
    assertTrue(position.hasNativePrecision());
    assertEquals((10 << 16) | 0x8001, position.nativePrecisionX());
    assertEquals((20 << 16) | 0x8002, position.nativePrecisionY());
  }

  @Test
  void bodyMovementLatchesInvalidationEvenAfterProjectionIsRestored() {
    Position position = new Position().initializeNativeCell(10, 20);
    Box2DSynchronizerPost.commitBodyPosition(position, new Vector2(11, 20));
    position.position.set(10, 20);
    assertFalse(position.hasNativePrecision());
  }

  @Test
  void solverJitterBelowVelocityThresholdStillInvalidatesRaw() {
    Position position = new Position().initializeNativeCell(10, 20);
    Box2DSynchronizerPost.commitBodyPosition(position, new Vector2(10.0005f, 20));
    position.position.set(10, 20);
    assertFalse(position.hasNativePrecision());
  }

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
