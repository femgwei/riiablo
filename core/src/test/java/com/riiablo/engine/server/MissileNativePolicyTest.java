package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.map.DT1;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Pure native Missiles.txt policy tests; no rendering or Box2D is required. */
class MissileNativePolicyTest {
  @Test
  void collisionAndCollideKillFlagsAreReadFromTheNativeRow() {
    Missile visual = new Missile();
    visual.missile = new Missiles.Entry();
    visual.missile.Collision = false;
    visual.missile.CollideKill = false;
    assertFalse(MissileCollisionSystem.hasNativeCollision(visual));
    assertFalse(MissileCollisionSystem.collidesKill(visual));

    Missile impact = new Missile();
    impact.missile = new Missiles.Entry();
    impact.missile.Collision = true;
    impact.missile.CollideKill = true;
    assertTrue(MissileCollisionSystem.hasNativeCollision(impact));
    assertTrue(MissileCollisionSystem.collidesKill(impact));
    assertTrue(MissileCollisionSystem.hasNativeCollision(new Missile()));
  }

  @Test
  void pierceRollUsesPerProjectileDeterministicState() {
    Missile first = new Missile();
    first.rngState = 0x12345678;
    boolean firstRoll = MissileCollisionSystem.rollPierce(first, 37);
    int nextState = first.rngState;

    Missile second = new Missile();
    second.rngState = 0x12345678;
    boolean secondRoll = MissileCollisionSystem.rollPierce(second, 37);
    assertEquals(firstRoll, secondRoll);
    assertEquals(nextState, second.rngState);
    assertTrue(MissileCollisionSystem.rollPierce(second, 100));
  }

  @Test
  void collideTypeMapsToNativeBarrierAndWallMasks() {
    assertEquals(0, MissileCollisionSystem.nativeMapCollisionMask(0));
    assertEquals(0, MissileCollisionSystem.nativeMapCollisionMask(4));
    assertEquals(0, MissileCollisionSystem.nativeMapCollisionMask(7));
    assertEquals(DT1.Tile.FLAG_BLOCK_JUMP,
        MissileCollisionSystem.nativeMapCollisionMask(6));
    assertEquals(DT1.Tile.FLAG_BLOCK_JUMP | DT1.Tile.FLAG_BLOCK_WALK,
        MissileCollisionSystem.nativeMapCollisionMask(8));
  }

  @Test
  void lastCollideIsReadFromTheNativeRow() {
    Missile missile = new Missile();
    missile.missile = new Missiles.Entry();
    assertFalse(MissileCollisionSystem.hasLastCollide(missile));
    missile.missile.LastCollide = true;
    assertTrue(MissileCollisionSystem.hasLastCollide(missile));
  }

  @Test
  void lastCollideEndpointGateIsSingleUseAndRequiresCollision() {
    Missile missile = new Missile();
    missile.missile = new Missiles.Entry();
    missile.missile.LastCollide = true;
    missile.missile.Collision = true;
    assertFalse(MissileCollisionSystem.shouldResolveLastCollide(missile, false));
    assertTrue(MissileCollisionSystem.shouldResolveLastCollide(missile, true));
    missile.lastCollideResolved = true;
    assertFalse(MissileCollisionSystem.shouldResolveLastCollide(missile, true));

    Missile visual = new Missile();
    visual.missile = new Missiles.Entry();
    visual.missile.LastCollide = true;
    visual.missile.Collision = false;
    assertFalse(MissileCollisionSystem.shouldResolveLastCollide(visual, true));
  }

  @Test
  void lastCollideUsesExactRangeEndpointWhenAFrameOvershoots() {
    Vector2 endpoint = MissileCollisionSystem.clampToRangeEndpoint(
        new Vector2(0, 0), new Vector2(10, 0), 8f, 10f, 10f, new Vector2());
    assertEquals(2f, endpoint.x, 0.001f);
    assertEquals(0f, endpoint.y, 0.001f);

    // A non-overshooting segment remains unchanged and a zero-length segment
    // never produces NaN coordinates.
    endpoint = MissileCollisionSystem.clampToRangeEndpoint(
        new Vector2(1, 2), new Vector2(3, 4), 0f, 2f, 10f, endpoint);
    assertEquals(3f, endpoint.x, 0.001f);
    assertEquals(4f, endpoint.y, 0.001f);
    endpoint = MissileCollisionSystem.clampToRangeEndpoint(
        new Vector2(1, 2), new Vector2(1, 2), 0f, 0f, 1f, endpoint);
    assertEquals(1f, endpoint.x, 0.001f);
    assertEquals(2f, endpoint.y, 0.001f);
  }

  @Test
  void shortLivedMissilesAreReclaimedUnderEntityPressure() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem()).build());
    try {
      for (int i = 0; i < 512; i++) {
        int id = world.create();
        Missile missile = world.getMapper(Missile.class).create(id);
        missile.missile = new Missiles.Entry();
        missile.missile.Collision = false;
        missile.range = 1f;
        missile.ownerId = -1;
        world.getMapper(Position.class).create(id).position.set(0, i);
        world.getMapper(Velocity.class).create(id).velocity.set(1, 0);
      }
      world.setDelta(1f);
      world.process();
      EntitySubscription subscription = world.getAspectSubscriptionManager().get(
          Aspect.all(Missile.class, Position.class, Velocity.class));
      IntBag entities = subscription.getEntities();
      assertEquals(0, entities.size(), "all range-limited missiles should be reclaimed");
    } finally {
      world.dispose();
    }
  }

  @Test
  void zeroVelocityMissilesExpireByNativeLifetimeUnderLongRunPressure() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem()).build());
    try {
      final int count = 2_048;
      for (int i = 0; i < count; i++) {
        int id = world.create();
        Missile missile = world.getMapper(Missile.class).create(id);
        missile.missile = new Missiles.Entry();
        missile.missile.Collision = false;
        missile.nativeLifetimeFrames = 5;
        missile.ownerId = -1;
        world.getMapper(Position.class).create(id).position.set(i % 64, i / 64);
        world.getMapper(Velocity.class).create(id).velocity.setZero();
      }
      world.setDelta(1f / 25f);
      for (int frame = 0; frame < 6; frame++) world.process();
      EntitySubscription subscription = world.getAspectSubscriptionManager().get(
          Aspect.all(Missile.class, Position.class, Velocity.class));
      assertEquals(0, subscription.getEntities().size(),
          "stationary missiles must be reclaimed at native lifetime");
    } finally {
      world.dispose();
    }
  }
}
