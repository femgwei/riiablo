package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.Velocity;
import org.junit.jupiter.api.Test;

class MovementMultiplierContractTest {
  @Test
  void serverIntegratorMatchesPhysicsVelocityForSlowAndFastTerminalSteps() {
    for (float multiplier : new float[] {0.5f, 1.5f}) {
      com.artemis.World world = new com.artemis.World(
          new com.artemis.WorldConfigurationBuilder().with(
              new DynamicUnitCollisionSystem(false), new VelocityAdder()).build());
      try {
        int id = world.create();
        com.riiablo.engine.server.component.Position position = world.getMapper(
            com.riiablo.engine.server.component.Position.class).create(id);
        Velocity requested = world.getMapper(Velocity.class).create(id).set(10, 15);
        requested.stateSpeedMultiplier = multiplier;
        float delta = 1f / 25f;
        requested.velocity.set(Pathfinder.limitWaypointSpeed(10, delta * multiplier, 0.1f), 0);
        Vector2 physics = Box2DSynchronizerPre.resolveMovementVelocity(requested, new Vector2());
        world.setDelta(delta);
        world.process();
        assertEquals(0.1f, position.position.x, 0.0001f);
        assertEquals(physics.x * delta, position.position.x, 0.0001f);
      } finally {
        world.dispose();
      }
    }
  }

  @Test
  void slowAndFastMovementUseTheSameSingleMultiplierInBothIntegrators() {
    for (float multiplier : new float[] {0.5f, 1f, 1.5f}) {
      Velocity requested = new Velocity().set(10, 15);
      requested.stateSpeedMultiplier = multiplier;
      requested.velocity.set(requested.speed(false), 0);
      Vector2 physics = Box2DSynchronizerPre.resolveMovementVelocity(requested, new Vector2());
      float serverDisplacement = requested.velocity.x * requested.movementMultiplier() / 25f;
      assertEquals(10f * multiplier, physics.x, 0.0001f);
      assertEquals(serverDisplacement, physics.x / 25f, 0.0001f);
      assertEquals(10f, requested.velocity.x, "integrator must not alter requested velocity");
    }
  }

  @Test
  void terminalClippingUsesTheActualScaledStep() {
    for (float multiplier : new float[] {0.5f, 1f, 1.5f}) {
      Velocity requested = new Velocity().set(10, 15);
      requested.stateSpeedMultiplier = multiplier;
      float delta = 1f / 25f;
      float distance = 0.1f;
      float speed = Pathfinder.limitWaypointSpeed(requested.speed(false),
          delta * requested.movementMultiplier(), distance);
      requested.velocity.set(speed, 0);
      Vector2 physics = Box2DSynchronizerPre.resolveMovementVelocity(requested, new Vector2());
      assertEquals(distance, physics.x * delta, 0.0001f);
    }
  }

  @Test
  void movementLockStopsPhysicsEvenWhenTheRequestIsNonzero() {
    Velocity requested = new Velocity().set(10, 15);
    requested.velocity.set(10, 0);
    requested.stateMovementLocked = true;
    assertEquals(0f, Box2DSynchronizerPre.resolveMovementVelocity(requested, new Vector2()).len());
    assertEquals(10f, requested.velocity.x);
  }
}
