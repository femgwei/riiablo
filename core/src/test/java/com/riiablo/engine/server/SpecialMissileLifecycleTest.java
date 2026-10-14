package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** MissMode.cpp shared collision clock and the two attached refresh callbacks. */
class SpecialMissileLifecycleTest {
  private static World world() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem()).build());
    world.setDelta(1f / 25f);
    return world;
  }

  private static int missile(World world, int callback, int frames, float speed) {
    int id = world.create();
    Missiles.Entry row = new Missiles.Entry();
    row.pSrvDoFunc = callback;
    row.Range = frames;
    row.Vel = (int) speed;
    world.getMapper(Missile.class).create(id).set(row, Vector2.Zero, frames);
    world.getMapper(Position.class).create(id);
    world.getMapper(Velocity.class).create(id).velocity.set(speed, 0f);
    return id;
  }

  private static int count(World world) {
    EntitySubscription subscription = world.getAspectSubscriptionManager().get(
        Aspect.all(Missile.class, Position.class, Velocity.class));
    return subscription.getEntities().size();
  }

  @Test
  void specialCallbackLifetimesAreIndependentOfVelocity() {
    for (int callback = 2; callback <= 37; callback++) {
      if (callback == 4 || callback == 20 || callback == 21) continue;
      for (float speed : new float[] {0f, 1f, 1000f}) {
        World world = world();
        try {
          missile(world, callback, 5, speed);
          for (int frame = 1; frame < 5; frame++) {
            world.process();
            assertEquals(1, count(world), "callback=" + callback + " frame=" + frame);
          }
          world.process();
          assertEquals(0, count(world), "callback=" + callback + " expires at frame 5");
        } finally {
          world.dispose();
        }
      }
    }
  }

  @Test
  void zeroAndNegativeNativeRangeExpireOnFirstUpdate() {
    for (int frames : new int[] {0, -3}) {
      World world = world();
      try {
        missile(world, 16, frames, 1f);
        world.process();
        assertEquals(0, count(world));
      } finally {
        world.dispose();
      }
    }
    Missiles.Entry row = new Missiles.Entry();
    row.Range = Integer.MAX_VALUE;
    row.LevRange = Integer.MAX_VALUE;
    assertEquals(Integer.MAX_VALUE, Missile.nativeRangeFrames(row, 20));
  }

  @Test
  void attachedCallbacksFollowOwnerUntilOwnerIsRemoved() {
    for (int callback : new int[] {20, 21}) {
      World world = world();
      try {
        int owner = world.create();
        world.getMapper(Position.class).create(owner);
        int id = missile(world, callback, 1, 1000f);
        Missile missile = world.getMapper(Missile.class).get(id);
        missile.attached = true;
        missile.attachedEntityId = owner;
        for (int frame = 1; frame <= 30; frame++) {
          world.getMapper(Position.class).get(owner).position.set(frame, frame);
          world.process();
          assertEquals(1, count(world));
          assertEquals(new Vector2(frame, frame), world.getMapper(Position.class).get(id).position);
        }
        world.delete(owner);
        // Artemis flushes the controller deletion at the end of this tick.
        world.process();
        world.process();
        assertEquals(0, count(world));
      } finally {
        world.dispose();
      }
    }
  }

  @Test
  void persistentEffectsKeepTheirExplicitRemainingFramePolicy() {
    World world = world();
    try {
      int id = missile(world, 5, 1, 1000f);
      Missile missile = world.getMapper(Missile.class).get(id);
      missile.persistent = true;
      missile.remainingFrames = 5;
      for (int frame = 1; frame < 5; frame++) {
        world.process();
        assertEquals(1, count(world));
      }
      world.process();
      assertEquals(0, count(world));
    } finally {
      world.dispose();
    }
  }

  @Test
  void stalledWakeMakerExpiresWithoutEmittingWaves() {
    World world = world();
    try {
      int id = missile(world, 31, 3, 0f);
      Missile maker = world.getMapper(Missile.class).get(id);
      maker.wakeMaker = true;
      maker.wakeTargetX = 10f;
      world.process();
      world.process();
      assertEquals(1, count(world));
      assertFalse(maker.wakeSpawned);
      world.process();
      assertEquals(0, count(world));
    } finally {
      world.dispose();
    }
  }

  @Test
  void guidedRetargetUsesCountdownBeforeDecrement() {
    World world = world();
    try {
      int target = world.create();
      world.getMapper(Position.class).create(target).position.set(10, 0);
      int id = missile(world, 7, 7, 1f);
      Missile missile = world.getMapper(Missile.class).get(id);
      missile.missile.Param = new int[] {3};
      missile.homing = true;
      missile.targetId = target;
      Velocity velocity = world.getMapper(Velocity.class).get(id);
      velocity.velocity.set(0, 1);
      world.process();
      assertEquals(0f, velocity.velocity.x, 0.0001f, "CurrentFrame=7 does not turn");
      world.process();
      assertTrue(velocity.velocity.x > 0.99f, "CurrentFrame=6 turns, not elapsed=3");
    } finally {
      world.dispose();
    }
  }

  @Test
  void novaRotatesElapsedIntegerOffsetsAndPreservesSlowedSpeed() {
    Missile nova = new Missile();
    nova.missile = new Missiles.Entry();
    nova.missile.Param = new int[] {4, 2};
    nova.missile.Vel = 100;
    nova.nativeLifetimeFrames = 25;
    nova.frozenOrbOrigin.set(999, 999);
    nova.frozenOrbTargetX = 5;
    nova.frozenOrbTargetY = -2;
    Velocity velocity = new Velocity();
    velocity.velocity.set(3, 0);
    MissileCollisionSystem.processFrozenOrbNova(nova, velocity, 0);
    assertEquals(3, nova.frozenOrbTargetX);
    assertEquals(1, nova.frozenOrbTargetY);
    assertEquals(3f, velocity.velocity.len(), 0.0001f);
    assertTrue(velocity.velocity.y > 0f);
    MissileCollisionSystem.processFrozenOrbNova(nova, velocity, 1);
    assertEquals(3, nova.frozenOrbTargetX);
    MissileCollisionSystem.processFrozenOrbNova(nova, velocity, 2);
    assertEquals(1, nova.frozenOrbTargetX);
    assertEquals(2, nova.frozenOrbTargetY);
    MissileCollisionSystem.processFrozenOrbNova(nova, velocity, 4);
    assertEquals(1, nova.frozenOrbTargetX, "window excludes elapsed=Param1");
    assertEquals(2, nova.frozenOrbTargetY);
  }
}
