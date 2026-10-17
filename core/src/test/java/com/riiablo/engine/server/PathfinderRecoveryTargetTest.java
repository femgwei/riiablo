package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativePathIdentity;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.map.Map;
import com.riiablo.map.MapGraph;
import com.riiablo.map.pfa.GraphPath;
import com.riiablo.map.pfa.Point2;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real ECS recovery/consumption; a scripted map isolates target-state transfer from search choice. */
class PathfinderRecoveryTargetTest {
  @Test void initialFailureWithoutDynamicGoalBlockDoesNotTryApproachPoints() {
    try (Fixture f = new Fixture()) {
      f.rejectTarget = true;
      assertFalse(f.paths.findPath(f.actor, f.targetPosition(), false, f.target));
      assertEquals(1, f.queries.size());
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
    }
  }

  @Test void skillOwnedMovementDoesNotUseOrdinaryApproachFallback() {
    try (Fixture f = new Fixture()) {
      f.rejectTarget = true;
      f.world.getMapper(Casting.class).create(f.actor);
      when(f.paths.dynamicCollision.isFreeForPath(f.actor, Engine.INVALID_ENTITY, 30, 10, 1))
          .thenReturn(false);
      assertFalse(f.paths.findPath(f.actor, f.targetPosition(), false, f.target));
      assertEquals(1, f.queries.size());
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
    }
  }

  @Test void initialBlockedEntityCenterCanUseAnApproachWithoutLosingTargetLifetime() {
    try (Fixture f = new Fixture()) {
      NativePathIdentity.Token token = f.world.getMapper(NativePathIdentity.class)
          .create(f.target).allocated(f.world, f.target).token();
      f.rejectTarget = true;
      when(f.paths.dynamicCollision.isFreeForPath(f.actor, Engine.INVALID_ENTITY, 30, 10, 1))
          .thenReturn(false);
      assertTrue(f.paths.findPath(f.actor, f.targetPosition(), false, f.target));
      assertNotEquals(f.targetPosition(), f.path().destination);
      assertEquals(f.target, f.path().targetEntityId);
      assertSame(token, f.path().targetIdentity);
      assertEquals(f.targetPosition(), f.path().lastTargetPosition);
      assertEquals(0, f.partialQueries);
    }
  }

  @Test void initialUnreachableEntityAndCoordinateCommandDoNotInventAPath() {
    try (Fixture f = new Fixture()) {
      f.rejectAll = true;
      when(f.paths.dynamicCollision.isFreeForPath(f.actor, Engine.INVALID_ENTITY, 30, 10, 1))
          .thenReturn(false);
      assertFalse(f.paths.findPath(f.actor, f.targetPosition(), false, f.target));
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
      int queries = f.queries.size();
      assertFalse(f.paths.findPath(f.actor, f.targetPosition(), false, Engine.INVALID_ENTITY));
      assertEquals(queries + 1, f.queries.size(), "coordinate commands must not search around their goal");
      assertEquals(0, f.partialQueries);
    }
  }

  @Test void nativeMovementRejectsForbiddenTownAndStopsExistingPath() {
    try (Fixture f = new Fixture()) {
      f.world.getMapper(NativePathIdentity.class).create(f.target).allocated(f.world, f.target);
      f.start();
      Map.Zone town = mock(Map.Zone.class);
      when(town.isTown()).thenReturn(true);
      when(f.map.getZone(any(Vector2.class))).thenReturn(town);
      assertFalse(f.paths.findNativeChase(f.actor, f.target));
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
      assertTrue(f.velocity().velocity.isZero());
      assertFalse(f.paths.findNativeOrbit(f.actor, f.target, 5, true));
    }
  }

  @Test void nativeMovementRejectsNonAdjacentRoom() {
    try (Fixture f = new Fixture()) {
      f.world.getMapper(NativePathIdentity.class).create(f.target).allocated(f.world, f.target);
      Map.Zone zone = new Map.Zone();
      Map.RoomEx first = zone.addRoomEx(0, 0, 20, 20);
      Map.RoomEx second = zone.addRoomEx(20, 0, 20, 20);
      first.setAdjacentRoomIds(new int[0]);
      second.setAdjacentRoomIds(new int[0]);
      f.world.getMapper(MapWrapper.class).create(f.actor).zone = zone;
      when(f.map.getZone(any(Vector2.class))).thenReturn(zone);
      assertFalse(f.paths.findNativeChase(f.actor, f.target));
      assertFalse(f.paths.findNativeOrbit(f.actor, f.target, 5, true));
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
    }
  }

  @Test void nativeOrbitCannotLeaveExportedRoomsWithLegalTarget() {
    try (Fixture f = new Fixture()) {
      f.world.getMapper(NativePathIdentity.class).create(f.target).allocated(f.world, f.target);
      Map.Zone zone = new Map.Zone();
      Map.RoomEx room = zone.addRoomEx(0, 9, 40, 3);
      room.setAdjacentRoomIds(new int[0]);
      f.world.getMapper(MapWrapper.class).create(f.actor).zone = zone;
      when(f.map.getZone(any(Vector2.class))).thenReturn(zone);
      assertTrue(f.paths.findNativeChase(f.actor, f.target));
      assertFalse(f.paths.findNativeOrbit(f.actor, f.target, 5, true));
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
    }
  }

  @Test void nativeMovementStillAllowsTownCapableMonster() {
    try (Fixture f = new Fixture()) {
      f.world.getMapper(NativePathIdentity.class).create(f.target).allocated(f.world, f.target);
      f.world.getMapper(Monster.class).get(f.actor).monstats.inTown = true;
      Map.Zone town = mock(Map.Zone.class);
      when(town.isTown()).thenReturn(true);
      when(f.map.getZone(any(Vector2.class))).thenReturn(town);
      assertTrue(f.paths.findNativeChase(f.actor, f.target));
      assertEquals(13, f.path().nativePathType);
    }
  }

  @Test void nativePathRejectsUnallocatedTargetIdentityWithoutThrowing() {
    try (Fixture f = new Fixture()) {
      f.world.getMapper(NativePathIdentity.class).create(f.target);
      assertFalse(f.paths.findNativePath(f.actor, f.target, 5, 0));
      assertFalse(f.paths.findNativeOrbit(f.actor, f.target, 5, true));
      assertFalse(f.paths.findNativeChase(f.actor, f.target));
    }
  }

  @Test void knownTargetLifetimeSurvivesRecoveryAndCoordinateCommandClearsIt() {
    try (Fixture f = new Fixture()) {
      NativePathIdentity.Token token = f.world.getMapper(NativePathIdentity.class)
          .create(f.target).allocated(f.world, f.target).token();
      f.start();
      assertSame(token, f.path().targetIdentity);
      f.rejectTarget = true;
      f.stall();
      f.paths.process(f.actor);
      assertSame(token, f.path().targetIdentity);
      assertTrue(f.paths.findPath(f.actor, new Vector2(20, 20)));
      assertNull(f.path().targetIdentity);
    }
  }

  @Test void staleLifetimeStopsBeforeDynamicStallCanQueryTheReplacement() {
    try (Fixture f = new Fixture()) {
      NativePathIdentity identity = f.world.getMapper(NativePathIdentity.class)
          .create(f.target).allocated(f.world, f.target);
      f.start();
      int queries = f.queries.size();
      identity.allocated(f.world, f.target);
      f.targetPosition().set(40, 16);
      f.stall();
      f.paths.process(f.actor);
      assertEquals(queries, f.queries.size());
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
      assertTrue(f.velocity().velocity.isZero());
    }
  }

  @Test void actualRecycledTargetIdDoesNotBecomeANewPursuit() {
    try (Fixture f = new Fixture()) {
      f.world.getMapper(NativePathIdentity.class).create(f.target).allocated(f.world, f.target);
      f.start();
      f.paths.setEnabled(false);
      f.world.process();
      f.world.delete(f.target);
      f.world.process();
      int replacement = f.world.create();
      assertEquals(f.target, replacement);
      f.world.getMapper(Position.class).create(replacement).position.set(40, 16);
      f.world.getMapper(Velocity.class).create(replacement).setPlayer(6, 9);
      f.world.getMapper(NativePathIdentity.class).create(replacement).allocated(f.world, replacement);
      f.world.process();
      int queries = f.queries.size();
      f.paths.setEnabled(true);
      f.paths.process(f.actor);
      assertEquals(queries, f.queries.size());
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
      assertTrue(f.velocity().velocity.isZero());
    }
  }

  @Test void switchingEntityTargetsReplacesRatherThanInheritsIdentity() {
    try (Fixture f = new Fixture()) {
      NativePathIdentity.Token first = f.world.getMapper(NativePathIdentity.class)
          .create(f.target).allocated(f.world, f.target).token();
      f.start();
      int other = f.world.create();
      f.world.getMapper(Position.class).create(other).position.set(35, 15);
      NativePathIdentity.Token second = f.world.getMapper(NativePathIdentity.class)
          .create(other).allocated(f.world, other).token();
      assertTrue(f.paths.findPath(f.actor, new Vector2(10, 10), new Vector2(34, 14),
          NativeGroundPathProfile.MONSTER.terrainFlags, 1, new GraphPath(), other));
      assertNotSame(first, f.path().targetIdentity);
      assertSame(second, f.path().targetIdentity);
      f.path().reset();
      assertNull(f.path().targetIdentity);
    }
  }

  @Test void dynamicStallRebuildRefreshesTargetSnapshotAndKeepsMovementIntent() {
    try (Fixture f = new Fixture()) {
      f.start();
      f.targetPosition().set(35, 14);
      f.stall();
      f.paths.process(f.actor);
      Pathfind path = f.path();
      assertEquals(f.target, path.targetEntityId);
      assertEquals(new Vector2(35, 14), path.lastTargetPosition);
      assertEquals(Pathfind.REPATH_INTERVAL, path.repathTimer);
      assertEquals(new Vector2(35, 14), path.destination);
      assertEquals(0.5f, f.velocity().modeSpeedBonusMultiplier);
      assertTrue(f.world.getMapper(Running.class).has(f.actor));
    }
  }

  @Test void detourRecreatedAfterStopKeepsTheEntityNotJustTheApproachCoordinate() {
    try (Fixture f = new Fixture()) {
      f.start();
      f.rejectTarget = true;
      f.stall();
      f.paths.process(f.actor);
      Pathfind path = f.path();
      assertEquals(new Vector2(29, 9), path.destination, "existing first free detour is unchanged");
      assertEquals(f.target, path.targetEntityId, "stop/recreate must not turn pursuit into a coordinate walk");
      assertEquals(new Vector2(30, 10), path.lastTargetPosition);
      assertEquals(Pathfind.REPATH_INTERVAL, path.repathTimer);
      assertEquals(0.5f, f.velocity().modeSpeedBonusMultiplier);
      assertTrue(f.world.getMapper(Running.class).has(f.actor));
    }
  }

  @Test void targetMovingAfterDetourTriggersAnotherActualQueryForThatEntity() {
    try (Fixture f = new Fixture()) {
      f.start();
      f.rejectTarget = true;
      f.stall();
      f.paths.process(f.actor);
      int queries = f.queries.size();
      f.rejectTarget = false;
      f.targetPosition().set(40, 16);
      f.paths.process(f.actor);
      assertEquals(queries + 1, f.queries.size(), "moving unit must not be forgotten after recovery");
      assertEquals(new Vector2(40, 16), f.queries.get(queries));
      assertEquals(f.target, f.path().targetEntityId);
      assertEquals(new Vector2(40, 16), f.path().destination);
    }
  }

  @Test void periodicRepathAfterDetourStillTracksTheStationaryUnit() {
    try (Fixture f = new Fixture()) {
      f.start();
      f.rejectTarget = true;
      f.stall();
      f.paths.process(f.actor);
      f.rejectTarget = false;
      f.path().repathTimer = 0f;
      int queries = f.queries.size();
      f.paths.process(f.actor);
      assertEquals(queries + 1, f.queries.size());
      assertEquals(f.targetPosition(), f.queries.get(queries));
      assertEquals(f.target, f.path().targetEntityId);
    }
  }

  @Test void newCoordinateCommandClearsAnOldEntityTarget() {
    try (Fixture f = new Fixture()) {
      f.start();
      assertTrue(f.paths.findPath(f.actor, new Vector2(20, 20)));
      assertEquals(Engine.INVALID_ENTITY, f.path().targetEntityId);
      assertTrue(f.path().lastTargetPosition.isZero());
      assertEquals(0f, f.path().repathTimer);
      assertEquals(new Vector2(20, 20), f.path().destination);
    }
  }

  @Test void failedRecoveryStopsRatherThanInstallingAnUnreachableOrPartialResult() {
    try (Fixture f = new Fixture()) {
      f.start();
      f.rejectAll = true;
      f.stall();
      f.paths.process(f.actor);
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
      assertTrue(f.velocity().velocity.isZero());
      assertEquals(0, f.partialQueries, "ordinary failure must not be silently promoted to partial");
    }
  }

  @Test void explicitDiabRunPartialKeepsTheReachableEndpointWithoutTrackingTheOldUnit() {
    try (Fixture f = new Fixture()) {
      f.start();
      NativeDiabRun run = new NativeDiabRun(f.target, f.targetPosition());
      f.world.getMapper(Casting.class).create(f.actor).diabRun = run;
      assertTrue(f.paths.findPath(f.actor, run.destination, false, Engine.INVALID_ENTITY));
      assertEquals(1, f.partialQueries);
      assertEquals(new Vector2(20, 10), f.path().destination);
      assertEquals(new Vector2(30, 10), run.destination, "partial endpoint must not replace the skill's goal");
      assertEquals(Engine.INVALID_ENTITY, f.path().targetEntityId);
      assertTrue(f.path().lastTargetPosition.isZero());
      assertEquals(0f, f.path().repathTimer);
    }
  }

  @Test void acceptedInternalPathReplacesRatherThanInheritsAnotherEntityTarget() {
    try (Fixture f = new Fixture()) {
      f.start();
      int other = f.world.create();
      f.world.getMapper(Position.class).create(other).position.set(35, 15);
      assertTrue(f.paths.findPath(f.actor, new Vector2(10, 10), new Vector2(34, 14),
          NativeGroundPathProfile.MONSTER.terrainFlags, 1, new GraphPath(), other));
      assertEquals(other, f.path().targetEntityId);
      assertEquals(new Vector2(35, 15), f.path().lastTargetPosition);
      assertEquals(new Vector2(34, 14), f.path().destination);
    }
  }

  @Test void roomPostCheckStillRejectsAPathBeforeTargetMetadataCanBeInstalled() {
    try (Fixture f = new Fixture()) {
      Map.Zone zone = new Map.Zone();
      Map.RoomEx a = zone.addRoomEx(0, 0, 40, 40);
      Map.RoomEx b = zone.addRoomEx(40, 0, 40, 40);
      Map.RoomEx c = zone.addRoomEx(80, 0, 40, 40);
      a.setAdjacentRoomIds(new int[] {b.id});
      b.setAdjacentRoomIds(new int[] {a.id, c.id});
      c.setAdjacentRoomIds(new int[] {b.id});
      f.world.getMapper(MapWrapper.class).create(f.actor).zone = zone;
      when(f.map.getZone(any(Vector2.class))).thenReturn(zone);
      f.intermediate = new Vector2(90, 10);
      assertFalse(f.paths.findPath(f.actor, f.targetPosition(), false, f.target));
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
      assertEquals(1, f.queries.size(), "entry passed but the detour entered a nonadjacent room");
    }
  }

  @Test void exhaustedFarDetourResumesUnitPursuitBeforePeriodicTimerExpires() {
    try (Fixture f = new Fixture()) {
      f.farDetour();
      f.arrive();
      f.rejectTarget = false;
      f.acceptedApproach = null;
      int queries = f.queries.size();
      f.paths.process(f.actor);
      assertEquals(queries + 1, f.queries.size(), "waypoint exhaustion must not forget the unreached unit");
      assertEquals(f.targetPosition(), f.queries.get(queries));
      assertEquals(f.target, f.path().targetEntityId);
      assertEquals(f.targetPosition(), f.path().destination);
      assertEquals(Pathfind.REPATH_INTERVAL, f.path().repathTimer);
      assertEquals(0.5f, f.velocity().modeSpeedBonusMultiplier);
      assertTrue(f.world.getMapper(Running.class).has(f.actor));
    }
  }

  @Test void exhaustedDetourCanChooseAnotherUsableApproachWithoutLosingTheUnit() {
    try (Fixture f = new Fixture()) {
      f.farDetour();
      f.arrive();
      f.acceptedApproach.set(27, 6);
      int queries = f.queries.size();
      f.paths.process(f.actor);
      assertTrue(f.queries.size() > queries);
      assertEquals(f.targetPosition(), f.queries.get(queries));
      assertEquals(new Vector2(27, 6), f.path().destination);
      assertEquals(f.target, f.path().targetEntityId);
      assertEquals(0, f.partialQueries);
    }
  }

  @Test void exhaustedDetourWithNoContinuationStopsAfterAnActualFailedQuery() {
    try (Fixture f = new Fixture()) {
      f.farDetour();
      f.arrive();
      f.rejectAll = true;
      int queries = f.queries.size();
      f.paths.process(f.actor);
      assertTrue(f.queries.size() > queries, "a short endpoint is not proof the target was reached");
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
      assertTrue(f.velocity().velocity.isZero());
      assertEquals(0, f.partialQueries);
    }
  }

  @Test void detourAlreadyInMeleeRangeStopsWithoutAContinuationQuery() {
    try (Fixture f = new Fixture()) {
      f.start();
      f.rejectTarget = true;
      f.stall();
      f.paths.process(f.actor);
      f.arrive(); // (29, 9), within the existing footprint-aware melee range.
      int queries = f.queries.size();
      f.paths.process(f.actor);
      assertEquals(queries, f.queries.size());
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
      assertTrue(f.velocity().velocity.isZero());
    }
  }

  @Test void unconsumedDetourDoesNotRepathBeforeTheExistingTimer() {
    try (Fixture f = new Fixture()) {
      f.farDetour();
      int queries = f.queries.size();
      f.paths.process(f.actor);
      assertEquals(queries, f.queries.size());
      assertEquals(new Vector2(26, 6), f.path().destination);
      assertEquals(Pathfind.REPATH_INTERVAL - 0.04f, f.path().repathTimer);
      assertFalse(f.velocity().velocity.isZero());
    }
  }

  @Test void reachedIntermediateWaypointIsNotAnExhaustedDetour() {
    try (Fixture f = new Fixture()) {
      f.farDetour();
      GraphPath route = new GraphPath();
      route.add(new Point2(10, 10));
      route.add(new Point2(20, 10));
      route.add(new Point2(26, 6));
      f.path().set(route);
      f.world.getMapper(Position.class).get(f.actor).position.set(20, 10);
      int queries = f.queries.size();
      f.paths.process(f.actor);
      assertEquals(queries, f.queries.size());
      assertEquals(new Vector2(26, 6), f.path().target);
      assertFalse(f.velocity().velocity.isZero());
    }
  }

  @Test void exhaustedStaticInteractionApproachAlsoContinuesWhenOutsideActivationRange() {
    try (Fixture f = new Fixture()) {
      f.start();
      f.world.getMapper(Velocity.class).remove(f.target);
      f.world.getMapper(Interactable.class).create(f.target).range = 1f;
      assertTrue(f.paths.findPath(f.actor, new Vector2(10, 10), new Vector2(26, 6),
          NativeGroundPathProfile.MONSTER.terrainFlags, 1, new GraphPath(), f.target));
      f.arrive();
      int queries = f.queries.size();
      f.paths.process(f.actor);
      assertEquals(queries + 1, f.queries.size());
      assertEquals(f.target, f.path().targetEntityId);
      assertEquals(f.targetPosition(), f.path().destination);
      assertEquals(Pathfind.REPATH_INTERVAL, f.path().repathTimer);
    }
  }

  @Test void completedCoordinateWalkDoesNotGainAnImplicitContinuationGoal() {
    try (Fixture f = new Fixture()) {
      assertTrue(f.paths.findPath(f.actor, new Vector2(20, 10)));
      f.arrive();
      int queries = f.queries.size();
      f.paths.process(f.actor);
      assertEquals(queries, f.queries.size());
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
      assertEquals(0, f.partialQueries);
    }
  }

  @Test void consumedDiabRunPartialDoesNotBecomeOrdinaryUnitContinuation() {
    try (Fixture f = new Fixture()) {
      NativeDiabRun run = new NativeDiabRun(f.target, f.targetPosition());
      f.world.getMapper(Casting.class).create(f.actor).diabRun = run;
      assertTrue(f.paths.findPath(f.actor, run.destination, false, Engine.INVALID_ENTITY));
      f.arrive();
      f.paths.process(f.actor);
      assertEquals(1, f.partialQueries);
      assertTrue(f.queries.isEmpty());
      assertEquals(new Vector2(30, 10), run.destination);
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
    }
  }

  private static final class Fixture implements AutoCloseable {
    final Pathfinder paths = new Pathfinder();
    final Map map = mock(Map.class);
    final World world;
    final int actor, target;
    final List<Vector2> queries = new ArrayList<>();
    boolean rejectTarget, rejectAll;
    int partialQueries;
    Vector2 intermediate;
    Vector2 acceptedApproach;

    Fixture() {
      when(map.findPath(nullable(Map.Zone.class), any(Vector2.class), any(Vector2.class),
          anyInt(), anyInt(), any(GraphPath.class), nullable(MapGraph.Obstacle.class),
          anyInt(), anyInt(), anyBoolean())).thenAnswer(call -> {
        Vector2 src = call.getArgument(1), dst = call.getArgument(2);
        GraphPath out = call.getArgument(5);
        queries.add(new Vector2(dst));
        out.clear();
        if (rejectAll || (rejectTarget && dst.epsilonEquals(targetPosition(), 0.01f))) return false;
        if (acceptedApproach != null && !dst.epsilonEquals(acceptedApproach, 0.01f)) return false;
        out.add(new Point2(Map.round(src.x), Map.round(src.y)));
        if (intermediate != null) out.add(new Point2(Map.round(intermediate.x), Map.round(intermediate.y)));
        out.add(new Point2(Map.round(dst.x), Map.round(dst.y)));
        return true;
      });
      when(map.findPartialPath(nullable(Map.Zone.class), any(Vector2.class), any(Vector2.class),
          anyInt(), anyInt(), any(GraphPath.class), nullable(MapGraph.Obstacle.class), anyBoolean()))
          .thenAnswer(call -> {
            partialQueries++;
            Vector2 src = call.getArgument(1);
            GraphPath out = call.getArgument(5);
            out.clear();
            out.add(new Point2(Map.round(src.x), Map.round(src.y)));
            out.add(new Point2(20, 10));
            return true;
          });
      world = new World(new WorldConfigurationBuilder().with(paths).build().register("map", map));
      paths.actioneer = new Actioneer() {
        @Override public boolean isInMeleeRange(int a, int b, int bonus) { return false; }
        @Override public int getMeleeRange(int a) { return 1; }
      };
      paths.dynamicCollision = mock(DynamicUnitCollisionSystem.class);
      when(paths.dynamicCollision.isFreeForPath(anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
          .thenReturn(true);
      actor = world.create();
      target = world.create();
      world.getMapper(Position.class).create(actor).position.set(10, 10);
      world.getMapper(Position.class).create(target).position.set(30, 10);
      world.getMapper(Size.class).create(actor).size = 1;
      world.getMapper(Size.class).create(target).size = 1;
      world.getMapper(Velocity.class).create(actor).setMonster(6);
      world.getMapper(Velocity.class).create(target).setPlayer(6, 9);
      world.getMapper(Angle.class).create(actor).set(Vector2.X);
      MonStats.Entry stats = new MonStats.Entry();
      stats.Id = "recovery-fixture";
      world.getMapper(Monster.class).create(actor).monstats = stats;
      world.setDelta(0.04f);
    }

    void start() {
      assertTrue(paths.findPath(actor, targetPosition(), false, target));
      velocity().setModeSpeedBonusPercent(50);
      world.getMapper(Running.class).create(actor);
    }
    void stall() { path().recordMovement(true, 0f, 0.3f, true); }
    void farDetour() {
      start();
      rejectTarget = true;
      acceptedApproach = new Vector2(26, 6);
      stall();
      paths.process(actor);
      assertEquals(acceptedApproach, path().destination);
      assertEquals(Pathfind.REPATH_INTERVAL, path().repathTimer);
    }
    void arrive() { world.getMapper(Position.class).get(actor).position.set(path().destination); }
    Vector2 targetPosition() { return world.getMapper(Position.class).get(target).position; }
    Pathfind path() { return world.getMapper(Pathfind.class).get(actor); }
    Velocity velocity() { return world.getMapper(Velocity.class).get(actor); }
    @Override public void close() { world.dispose(); }
  }
}
