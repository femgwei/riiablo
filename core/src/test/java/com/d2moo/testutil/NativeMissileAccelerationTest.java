package com.d2moo.testutil;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.missile.NativeMissileAcceleration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMissileAccelerationTest {
  @Test void appliesSignedIncrementOnlyOnFifthStep() {
    NativeMissileAcceleration path = new NativeMissileAcceleration();
    path.initialize(-256, 20);
    Vector2 velocity = new Vector2(6, 8);
    for (int i = 0; i < 4; i++) path.advance(velocity);
    assertEquals(10f, velocity.len(), 0.0001f);
    path.advance(velocity);
    assertEquals(9f, velocity.len(), 0.0001f);
    assertEquals(0.75f, velocity.x / velocity.y, 0.0001f);
  }

  @Test void capsAndDisablesAccelerationAndResetsPooledState() {
    NativeMissileAcceleration path = new NativeMissileAcceleration();
    path.initialize(512, 11);
    Vector2 velocity = new Vector2(10, 0);
    for (int i = 0; i < 20; i++) path.advance(velocity);
    assertEquals(11f, velocity.len(), 0.0001f);
    path.initialize(-32768, 11);
    for (int i = 0; i < 5; i++) path.advance(velocity);
    assertEquals(0f, velocity.len(), 0.0001f);
    path.initialize(0, 0);
    velocity.set(10, 0);
    for (int i = 0; i < 5; i++) path.advance(velocity);
    assertEquals(10f, velocity.len(), 0.0001f);
  }
}
