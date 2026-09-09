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
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeAiTargetOverride;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.state.StateList;
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
  void nextHitAndNextDelayGateAttachedMissilesPerNativeFrame() {
    Missile missile = new Missile();
    missile.attached = true;
    missile.missile = new Missiles.Entry();
    missile.missile.NextHit = true;
    missile.missile.NextDelay = 3;
    StateList targetStates = new StateList(7);

    missile.nativeFrame = 10;
    assertTrue(MissileCollisionSystem.claimTargetHit(missile, 7, targetStates));
    assertFalse(MissileCollisionSystem.claimTargetHit(missile, 7, targetStates),
        "JUSTHIT must suppress contacts during NextDelay");
    targetStates.update();
    targetStates.update();
    assertFalse(MissileCollisionSystem.claimTargetHit(missile, 7, targetStates));
    targetStates.update();
    assertTrue(MissileCollisionSystem.claimTargetHit(missile, 7, targetStates),
        "the target becomes eligible again when the native delay expires");

    Missile otherMissile = new Missile();
    otherMissile.attached = true;
    otherMissile.missile = missile.missile;
    assertFalse(MissileCollisionSystem.claimTargetHit(otherMissile, 7, targetStates),
        "JUSTHIT is target-wide and must suppress other NextHit missiles");

    Missile noNextHit = new Missile();
    noNextHit.attached = true;
    noNextHit.missile = new Missiles.Entry();
    noNextHit.missile.NextHit = false;
    noNextHit.nativeFrame = 4;
    assertTrue(MissileCollisionSystem.claimTargetHit(noNextHit, 7, targetStates));
    assertFalse(MissileCollisionSystem.claimTargetHit(noNextHit, 7, targetStates),
        "a single native frame cannot resolve the same attached contact twice");
    noNextHit.nativeFrame = 5;
    assertTrue(MissileCollisionSystem.claimTargetHit(noNextHit, 7, targetStates),
        "without NextHit an attached missile may contact again next frame");
  }

  @Test
  void projectileAndSharedCastHitSetsRemainIdempotent() {
    Missile projectile = new Missile();
    assertTrue(MissileCollisionSystem.claimTargetHit(projectile, 9, null));
    assertFalse(MissileCollisionSystem.claimTargetHit(projectile, 9, null),
        "a non-attached projectile may not damage one unit twice");

    IntSet shared = new IntSet();
    Missile first = new Missile().shareHitTargets(shared);
    Missile second = new Missile().shareHitTargets(shared);
    assertTrue(MissileCollisionSystem.claimTargetHit(first, 11, null));
    assertFalse(MissileCollisionSystem.claimTargetHit(second, 11, null),
        "overlapping missiles from one cast share one target claim");
    assertTrue(MissileCollisionSystem.claimTargetHit(second, 12, null));
  }

  @Test
  void redirectedMonsterMissileTreatsItsTemporaryTargetAsHostile() {
    MissileCollisionSystem missiles = new MissileCollisionSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), missiles).build());
    try {
      int source = world.create();
      int target = world.create();
      world.getMapper(Monster.class).create(source);
      world.getMapper(Monster.class).create(target);
      assertTrue(missiles.areAligned(source, target));

      world.getMapper(NativeAiTargetOverride.class).create(source)
          .setAttract(target, 99, 59, 10);
      assertFalse(missiles.areAligned(source, target),
          "the temporary AI target must pass monster missile collision filtering");
    } finally {
      world.dispose();
    }
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
