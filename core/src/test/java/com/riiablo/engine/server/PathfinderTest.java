package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.riiablo.engine.server.component.Velocity;

class PathfinderTest {
  @Test
  void blockedRaycastFallbackStopsInsteadOfMovingDirectlyToTarget() {
    Velocity velocity = new Velocity();
    velocity.velocity.set(30f, -20f);

    Pathfinder.stopBlockedMovement(velocity);

    assertEquals(0f, velocity.velocity.x);
    assertEquals(0f, velocity.velocity.y);
  }
}
