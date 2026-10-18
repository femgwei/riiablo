package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.d2moo.common.path.D2PathStep;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.pfa.GraphPath;
import com.riiablo.map.pfa.Point2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMonsterMovementTest {
  @Test void localPhysicsFollowsNativePositionWithoutSecondIntegration() {
    com.badlogic.gdx.physics.box2d.Box2D.init();
    com.badlogic.gdx.physics.box2d.World physics =
        new com.badlogic.gdx.physics.box2d.World(new com.badlogic.gdx.math.Vector2(), true);
    World world = new World(new WorldConfigurationBuilder()
        .with(new DynamicUnitCollisionSystem(), new VelocityAdder(true),
            new Box2DSynchronizerPre(), new Box2DSynchronizerPost()).build());
    try {
      int id = world.create();
      Position position = world.getMapper(Position.class).create(id).initializeNativeCell(10, 20);
      Velocity velocity = world.getMapper(Velocity.class).create(id).setMonsterFromNativeTable(6);
      world.getMapper(Monster.class).create(id);
      GraphPath graph = new GraphPath();
      graph.add(new Point2(10, 20)); graph.add(new Point2(40, 20));
      world.getMapper(Pathfind.class).create(id).set(graph);
      com.badlogic.gdx.physics.box2d.BodyDef def = new com.badlogic.gdx.physics.box2d.BodyDef();
      def.type = com.badlogic.gdx.physics.box2d.BodyDef.BodyType.DynamicBody;
      com.badlogic.gdx.physics.box2d.Body body = physics.createBody(def);
      world.getMapper(Box2DBody.class).create(id).body = body;
      world.setDelta(SimulationClock.STEP_SECONDS);
      velocity.velocity.set(velocity.speed(false), 0);
      world.process();
      int raw = position.nativePrecisionX();
      assertEquals(D2PathStep.toFp16Center(10) + 18432, raw);
      assertEquals(com.badlogic.gdx.physics.box2d.BodyDef.BodyType.StaticBody, body.getType());
      physics.step(SimulationClock.STEP_SECONDS, 6, 2);
      assertEquals(position.position.x, body.getPosition().x);
      assertEquals(raw, position.nativePrecisionX());
      assertTrue(body.getLinearVelocity().isZero());
    } finally {
      world.dispose();
      physics.dispose();
    }
  }

  static class Fixture implements AutoCloseable {
    final World world = new World(new WorldConfigurationBuilder()
        .with(new DynamicUnitCollisionSystem(), new VelocityAdder()).build());
    final int id = world.create();
    final Position position = world.getMapper(Position.class).create(id).initializeNativeCell(10, 20);
    final Velocity velocity = world.getMapper(Velocity.class).create(id).setMonsterFromNativeTable(6);
    final Pathfind path = world.getMapper(Pathfind.class).create(id);
    Fixture() {
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type =
          com.riiablo.engine.server.component.Class.Type.MON;
      world.getMapper(Monster.class).create(id);
      world.getMapper(Size.class).create(id).size = 1;
      GraphPath graph = new GraphPath();
      graph.add(new Point2(10, 20)); graph.add(new Point2(40, 20));
      path.set(graph);
      velocity.velocity.set(velocity.speed(false), 0);
      world.setDelta(SimulationClock.STEP_SECONDS);
    }
    void tick() { world.process(); }
    @Override public void close() { world.dispose(); }
  }

  @Test void productionHeadlessRouteRetainsRawLowWordsForTwentyFiveTicks() {
    try (Fixture f = new Fixture()) {
      int nativeStep = (4096 * ((6 << 8) * 75 / 100)) >> 8;
      for (int i = 1; i <= 25; i++) {
        f.tick();
        assertTrue(f.velocity.nativeMovementOwned);
        assertEquals(D2PathStep.toFp16Center(10) + nativeStep * i, f.position.nativePrecisionX());
        assertEquals(D2PathStep.toFp16Center(20), f.position.nativePrecisionY());
      }
      assertEquals(7.03125f, f.position.position.x - 10f);
    }
  }

  @Test void waypointArrivalIsClampedToExactNativeCenterWithoutOvershoot() {
    try (Fixture f = new Fixture()) {
      GraphPath graph = new GraphPath();
      graph.add(new Point2(10, 20)); graph.add(new Point2(11, 20));
      f.path.set(graph);
      for (int i = 0; i < 10; i++) f.tick();
      assertEquals(D2PathStep.toFp16Center(11), f.position.nativePrecisionX());
      assertTrue(f.position.hasNativePrecision());
    }
  }

  @Test void nativeStepsDoNotPassThroughAnotherMonstersFootprint() {
    try (Fixture f = new Fixture()) {
      int blocker = f.world.create();
      f.world.getMapper(com.riiablo.engine.server.component.Class.class).create(blocker).type =
          com.riiablo.engine.server.component.Class.Type.MON;
      f.world.getMapper(Monster.class).create(blocker);
      f.world.getMapper(Size.class).create(blocker).size = 1;
      f.world.getMapper(Position.class).create(blocker).initializeNativeCell(14, 20);
      f.world.getMapper(Velocity.class).create(blocker).setMonsterFromNativeTable(0);
      boolean blocked = false;
      for (int i = 0; i < 25; i++) {
        f.velocity.velocity.set(f.velocity.speed(false), 0);
        f.tick();
        assertTrue(f.position.hasNativePrecision());
        assertTrue(f.position.position.x < 14f);
        blocked |= f.velocity.velocity.isZero();
      }
      assertTrue(blocked, "at least one cell transition must be rejected");
    }
  }

  @Test void integerLookingLegacyWaypointDoesNotEstablishNativeProvenance() {
    try (Fixture f = new Fixture()) {
      f.path.reset(); f.path.target.set(40, 20);
      f.tick();
      assertFalse(f.velocity.nativeMovementOwned);
      assertFalse(f.position.hasNativePrecision());
    }
  }

  @Test void alteredWaypointAndNonFixedDeltaRelinquishNativeOwnership() {
    try (Fixture f = new Fixture()) {
      f.path.target.set(39, 20);
      f.tick();
      assertFalse(f.velocity.nativeMovementOwned);
      assertFalse(f.position.hasNativePrecision());
    }
    try (Fixture f = new Fixture()) {
      f.world.setDelta(0.02f); f.tick();
      assertFalse(f.velocity.nativeMovementOwned);
      assertFalse(f.position.hasNativePrecision());
    }
  }

  @Test void freezeKeepsNativeOwnershipAndPrecisionWithoutApplyingVelocityTwice() {
    try (Fixture f = new Fixture()) {
      f.velocity.setModeSpeedBonusPercent(100);
      f.velocity.nativeMonsterVelocityBonus = 25;
      f.tick();
      assertEquals(D2PathStep.toFp16Center(10) + ((4096 * (6 << 9)) >> 8),
          f.position.nativePrecisionX());
      int raw = f.position.nativePrecisionX();
      f.velocity.stateMovementLocked = true;
      f.tick();
      assertTrue(f.velocity.nativeMovementOwned);
      assertEquals(raw, f.position.nativePrecisionX());
    }
  }

  @Test void graphWaypointSourceAdvancesResetsAndRejectsOutOfRangeCells() {
    Pathfind path = new Pathfind();
    GraphPath graph = new GraphPath();
    graph.add(new Point2(10, 20)); graph.add(new Point2(11, 20)); graph.add(new Point2(12, 20));
    path.set(graph);
    assertEquals(11, path.nativeWaypointX());
    assertTrue(path.advanceTarget()); assertEquals(12, path.nativeWaypointX());
    assertFalse(path.advanceTarget());
    path.reset(); assertFalse(path.hasNativeWaypoint());
    graph.clear(); graph.add(new Point2(10, 20)); graph.add(new Point2(65536, 20));
    path.set(graph); assertFalse(path.hasNativeWaypoint());
  }
}
