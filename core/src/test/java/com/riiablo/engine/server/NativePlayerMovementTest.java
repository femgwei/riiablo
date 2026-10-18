package com.riiablo.engine.server;

import com.riiablo.engine.server.component.Velocity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NativePlayerMovementTest {
  @Test void ecsIntegratorAppliesNativeRunPercentageOnce() {
    com.artemis.World world = new com.artemis.World(new com.artemis.WorldConfigurationBuilder()
        .with(new DynamicUnitCollisionSystem(false), new VelocityAdder()).build());
    try {
      int id = world.create();
      com.riiablo.engine.server.component.Position position = world.getMapper(
          com.riiablo.engine.server.component.Position.class).create(id);
      Velocity velocity = world.getMapper(Velocity.class).create(id).setPlayer(6, 9);
      velocity.movementRunning = true;
      velocity.nativePlayerVelocityBonus = 50;
      velocity.velocity.set(velocity.speed(true), 0);
      world.setDelta(1f / 25f);
      world.process();
      assertEquals(0.75f, position.position.x, 0.0001f);
      assertEquals(202, NativePlayerMovement.animationRate(
          velocity.walkSpeed, velocity.runSpeed, true, velocity.nativePlayerVelocityBonus));
    } finally {
      world.dispose();
    }
  }

  @Test void nativePercentDrivesBothDisplacementAndAnimation() {
    Velocity velocity = new Velocity().setPlayer(6, 9);
    for (boolean running : new boolean[] {false, true}) {
      velocity.movementRunning = running;
      int base = running ? 150 : 100;
      for (int bonus : new int[] {0, 50, -50, -200}) {
        velocity.nativePlayerVelocityBonus = bonus;
        int percent = Math.max(25, base + bonus);
        assertEquals(9.375f * percent / 100f,
            velocity.speed(running) * velocity.movementMultiplier(), 0.0001f);
        assertEquals((running ? 101 : 213) * percent / 100,
            NativePlayerMovement.animationRate(velocity.walkSpeed, velocity.runSpeed,
                running, bonus));
      }
    }
  }

  @Test void nativeRunRateIncludesRunWalkRatioAndIgnoresShortPathVector() {
    Velocity velocity = new Velocity().setPlayer(6, 9);
    velocity.velocity.set(0.1f, 0);
    assertEquals(151, NativePlayerMovement.animationRate(
        velocity.walkSpeed, velocity.runSpeed, true, 0));
    assertEquals(213, NativePlayerMovement.animationRate(
        velocity.walkSpeed, velocity.runSpeed, false, 0));
    assertEquals(50, NativePlayerMovement.bonus(75, 0));
    assertEquals(340, NativePlayerMovement.animationRate(6, 9, false,
        NativePlayerMovement.bonus(100, 0)));
  }
}
