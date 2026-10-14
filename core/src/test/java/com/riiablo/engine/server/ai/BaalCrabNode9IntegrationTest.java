package com.riiablo.engine.server.ai;

import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.DynamicUnitCollisionSystem;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeAiTargetOverride;
import com.riiablo.engine.server.component.NativeAiPathCache;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.map.pfa.GraphPath;
import com.riiablo.map.pfa.Point2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Node9 membership/selection and read-only TOWARD projection, not full native path state. */
class BaalCrabNode9IntegrationTest extends RiiabloTest {
  @Test
  void node9TiesFollowCurseInsertionAndReentryButNotRefresh() {
    for (String ai : new String[] {"BaalCrab", "BaalCrabClone"}) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(ai)) {
        terrain(f, true);
        int firstEntity = enemy(f, 14, 10);
        int secondEntity = enemy(f, 14, 10);
        StateList second = f.world.getMapper(UnitStates.class)
            .create(secondEntity).init(secondEntity).stateList;
        StateList first = f.world.getMapper(UnitStates.class)
            .create(firstEntity).init(firstEntity).stateList;
        second.addState(StateId.ATTRACT, 100, 1, f.target);
        first.addState(StateId.CONFUSE, 100, 1, f.target);
        f.world.process();
        assertEquals(firstEntity, select(f), "later curse wins despite lower entity ID");
        second.addState(StateId.ATTRACT, 200, 2, f.target);
        assertEquals(firstEntity, select(f), "refresh does not reinsert the node");
        second.removeState(StateId.ATTRACT);
        second.addState(StateId.ATTRACT, 1, 1, f.target);
        assertEquals(secondEntity, select(f), "remove/reapply is a new insertion");
        second.update();
        assertEquals(firstEntity, select(f), "expired curse no longer belongs to node9");
        first.removeState(StateId.CONFUSE);
        assertEquals(f.target, select(f), "removal does not keep a stale candidate");
        assertEquals(1, baal(f).lastTargetCount());
      }
    }
  }

  @Test
  void node9WallAndCurseInsertionUseOneSharedOrder() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      terrain(f, true);
      int cursed = enemy(f, 14, 10);
      int wall = wall(f, 14, 10);
      StateList states = f.world.getMapper(UnitStates.class)
          .create(cursed).init(cursed).stateList;
      states.addState(StateId.ATTRACT, 100, 1, f.target);
      f.world.process();
      assertEquals(cursed, select(f), "curse applied after higher-ID wall insertion");
      states.removeState(StateId.ATTRACT);
      states.addState(StateId.ATTRACT, 100, 1, f.target);
      int newerWall = wall(f, 14, 10);
      f.world.process();
      assertEquals(newerWall, select(f), "later wall insertion wins against curse order");
      assertEquals(baal(f).targetScore(wall), baal(f).targetScore(cursed));
    }
  }

  @Test
  void conversionInsertionSharesChildOrderAndDeathRemovesItsCount() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      int converted = enemy(f, 30, 10);
      int pet = enemy(f, 30, 10);
      f.world.getMapper(SummonedPet.class).create(pet).set(f.target, "skeleton", 70, 1, false, 0);
      Monster monster = f.world.getMapper(Monster.class).get(converted);
      monster.converted = true;
      monster.conversionOwnerId = f.target;
      f.world.getMapper(UnitStates.class).create(converted).init(converted).stateList
          .addState(StateId.CONVERSION, 100, 1, f.target);
      for (int id : new int[] {converted, pet}) {
        attributes(f, id).base().put(Stat.maxdamage, 1000);
        attributes(f, id).reset();
      }
      f.world.process();
      assertEquals(converted, select(f));
      assertEquals(3, baal(f).lastTargetCount());
      f.world.getMapper(Sequence.class).create(converted)
          .sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
      assertEquals(pet, select(f));
      assertEquals(2, baal(f).lastTargetCount());
    }
  }

  @Test
  void sourceOnlyPointFromNearbyCachedTargetIsClearedByWrapper() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      terrain(f, true);
      cache(f).allocated().targetCoordinates(13, 10);
      int wall = wall(f, 14, 10);
      f.targetPosition().set(65, 10);
      f.world.process();
      assertEquals(wall, select(f));
      assertTrue(cache(f).coordinatesKnown);
      assertFalse(cache(f).hasPoints);
    }
  }

  @Test
  void freshKnownEmptyCacheEnablesMissingPrimaryFallbackForBodyAndClone() {
    for (String ai : new String[] {"BaalCrab", "BaalCrabClone"}) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(ai)) {
        NativeAiPathCache cache = cache(f).allocated();
        int wall = wall(f, 14, 10);
        f.targetPosition().set(65, 10); // head culled: zero primary nodes
        f.world.process();
        assertEquals(wall, select(f), "zero cached coordinates prove zero points without terrain");
        assertEquals(0, baal(f).lastTargetCount());
        assertEquals(baal(f).targetScore(wall), baal(f).lastMaxTargetScore());
        assertTrue(cache.coordinatesKnown);
        assertTrue(cache.pointsKnown);
        assertFalse(cache.hasPoints);
        verifyNoInteractions(f.paths);
      }
    }
  }

  @Test
  void missingPrimaryUsesCachedCoordinatesWithoutFollowingDeadOrRemovedUnit() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      Map map = terrain(f, true);
      int wall = wall(f, 6, 10); // cache points east, node9 candidate is west
      f.world.process();
      assertEquals(wall, select(f));
      assertEquals(20, cache(f).targetX);
      assertEquals(10, cache(f).targetY);
      f.world.delete(f.target);
      f.world.process();
      clearInvocations(map);
      assertEquals(wall, select(f));
      assertEquals(0, baal(f).lastTargetCount());
      verify(map, atLeastOnce()).isBlockedForPath(any(), eq(11), eq(10), anyInt(), anyInt(), anyBoolean());
      verify(map, never()).isBlockedForPath(any(), eq(9), anyInt(), anyInt(), anyInt(), anyBoolean());
      assertEquals(20, cache(f).targetX);
      assertFalse(cache(f).hasPoints);
    }
  }

  @Test
  void successfulUnitlessProbeDoesNotReuseUnknownFinalPathEndpoint() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      Map map = terrain(f, false);
      wall(f, 14, 10);
      f.world.process();
      assertEquals(f.target, select(f));
      assertTrue(cache(f).coordinatesKnown);
      attributes(f, f.target).base().put(Stat.hitpoints, 0f);
      attributes(f, f.target).reset();
      assertEquals(Engine.INVALID_ENTITY, select(f));
      assertTrue(cache(f).pointsKnown);
      assertTrue(cache(f).hasPoints);
      assertFalse(cache(f).coordinatesKnown, "native may replace coordinates with the last path point");
      when(map.isBlockedForPath(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenReturn(true);
      assertEquals(Engine.INVALID_ENTITY, select(f), "do not re-probe a stale requested endpoint");
      assertFalse(cache(f).pointsKnown, "unprojectable recomputation cannot retain a trusted old count");
      attributes(f, f.target).base().put(Stat.hitpoints, 100f);
      attributes(f, f.target).reset();
      f.targetPosition().set(0, 10);
      assertEquals(f.target, select(f), "zero-coordinate early return must also see the count as unknown");
    }
  }

  @Test
  void zeroTargetUnitCoordinateRetainsKnownPointCountAndDoesNotUpdateCache() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      NativeAiPathCache cache = cache(f).allocated();
      int wall = wall(f, 14, 10);
      f.targetPosition().set(0, 10);
      f.world.process();
      assertEquals(wall, select(f), "early return retains known zero points");
      assertEquals(0, cache.targetY, "early return does not read target coordinates into the cache");
      cache.pathPoints(true);
      assertEquals(f.target, select(f), "early return can also retain known nonzero points");
      assertEquals(0, cache.targetY);
    }
  }

  @Test
  void unknownCacheDoesNotBorrowEcsDestinationOrAssumeEmptyAfterPathRemoval() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      terrain(f, true);
      wall(f, 14, 10);
      f.targetPosition().set(65, 10);
      GraphPath path = new GraphPath();
      path.add(new Point2(10, 10));
      path.add(new Point2(10, 10));
      f.world.getMapper(Pathfind.class).create(f.actor).set(path);
      f.world.process();
      assertEquals(Engine.INVALID_ENTITY, select(f));
      f.world.getMapper(Pathfind.class).remove(f.actor);
      f.world.process();
      assertEquals(Engine.INVALID_ENTITY, select(f));
      assertFalse(cache(f).coordinatesKnown);
    }
  }

  @Test
  void missingPrimaryStillChoosesWholeBucketBeforeDistanceGate() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      cache(f).allocated();
      wall(f, 14, 10);
      int far = wall(f, 30, 10);
      attributes(f, far).base().put(Stat.maxdamage, 1000);
      attributes(f, far).reset();
      f.targetPosition().set(65, 10);
      f.world.process();
      assertEquals(Engine.INVALID_ENTITY, select(f));
      assertTrue(cache(f).pointsKnown);
      assertFalse(cache(f).hasPoints);
      assertEquals(0, baal(f).lastTargetCount());
    }
  }

  @Test
  void actionsMovementAndWarpInvalidateCacheButStoppingDoesNotInventCoordinates() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      NativeAiPathCache cache = cache(f).allocated();
      f.ai.stopMovement();
      assertTrue(cache.coordinatesKnown, "ECS stop is not a native coordinate command");
      for (int action : new int[] {1, 5, 11}) {
        cache.allocated();
        baal(f).execute(action, f.target);
        assertFalse(cache.coordinatesKnown, "unported mode writer: " + action);
        assertFalse(cache.pointsKnown);
        f.finishAction();
      }
      cache.allocated();
      f.ai.walkTo(new Vector2(30, 10), f.target);
      assertFalse(cache.coordinatesKnown);
      cache.allocated();
      f.ai.onOwnerWarp();
      assertFalse(cache.coordinatesKnown);
    }
  }

  @Test
  void replacingAiDoesNotTreatExistingEntityAsFreshAllocation() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      NativeAiPathCache cache = cache(f);
      cache.targetCoordinates(37, 41);
      cache.pathPoints(true);
      BaalCrab replacement = new BaalCrab(f.actor);
      f.world.getInjector().inject(replacement);
      replacement.initialize();
      assertSame(cache, cache(f));
      assertEquals(37, cache.targetX);
      assertEquals(41, cache.targetY);
      assertTrue(cache.hasPoints);
    }
  }

  @Test
  void explicitOverrideHasAuthorityAndDoesNotRewriteNativeCache() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      NativeAiPathCache cache = cache(f).allocated();
      wall(f, 14, 10);
      int forced = enemy(f, 30, 10);
      f.world.getMapper(NativeAiTargetOverride.class).create(f.actor).setAttract(forced, f.target, 86, 100);
      f.targetPosition().set(65, 10);
      f.world.process();
      assertEquals(forced, select(f));
      assertTrue(cache.coordinatesKnown);
      assertTrue(cache.pointsKnown);
      assertEquals(0, cache.targetX);
      assertFalse(cache.hasPoints);
    }
  }

  private static NativeAiPathCache cache(Act1NativeAiIntegrationTest.Fixture f) {
    return f.world.getMapper(NativeAiPathCache.class).create(f.actor);
  }

  @Test
  void real110fBoneWallRowBelongsToNode9ButScoresZeroDueToNativeThreat() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      terrain(f, true);
      int wall = wall(f, 14, 10);
      MonStats.Entry row = Riiablo.files.monstats.get("bonewall");
      assertNotNull(row);
      assertTrue(row.threat <= 1, "real bonewall is excluded by native Baal threat filtering");
      f.world.getMapper(Monster.class).get(wall).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      f.world.process();
      assertEquals(0, baal(f).targetScore(wall));
      assertEquals(f.target, select(f));
      assertEquals(1, baal(f).lastTargetCount(), "wall is not a primary owner-group node either");
    }
  }

  @Test
  void real110fHighThreatEvilRowWithAttractCanBecomeFallbackTarget() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      terrain(f, true);
      MonStats.Entry chosen = null;
      for (MonStats.Entry row : Riiablo.files.monstats) {
        if (row.threat > 1 && row.Align == 0 && row.killable && !row.boss && !row.npc && !row.inTown) {
          chosen = row;
          break;
        }
      }
      assertNotNull(chosen);
      int enemy = f.ally(chosen.Id, 100, 14, 10);
      f.world.getMapper(Size.class).create(enemy).size = 1;
      f.world.getMapper(UnitStates.class).create(enemy).init(enemy).stateList
          .addState(StateId.ATTRACT, 100, 1, f.target);
      f.world.process();
      assertTrue(baal(f).targetScore(enemy) > 0);
      assertEquals(enemy, select(f), chosen.Id);
      assertEquals(1, baal(f).lastTargetCount());
    }
  }

  @Test
  void dynamicOccupancyUsesSourceZoneAndKeepsAllBlockingUnitsInProbe() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      terrain(f, false);
      int wall = wall(f, 14, 10);
      f.world.process();
      DynamicUnitCollisionSystem dynamic = mock(DynamicUnitCollisionSystem.class);
      World engine = spy(f.world);
      doReturn(dynamic).when(engine).getSystem(DynamicUnitCollisionSystem.class);
      Riiablo.engine = engine;
      assertEquals(wall, select(f), "dynamic blockage alone can leave zero initial steps");
      Map.Zone zone = f.world.getMapper(MapWrapper.class).get(f.actor).zone;
      verify(dynamic, atLeastOnce()).isFreeForPath(zone, f.actor, Engine.INVALID_ENTITY, 11, 10, 1);
      when(dynamic.isFreeForPath(zone, f.actor, Engine.INVALID_ENTITY, 11, 9, 1)).thenReturn(true);
      assertEquals(f.target, select(f));
    }
  }

  @Test
  void blockedTowardPrimarySelectsNearbyWallEvenWithLowerScoreAndKeepsPrimaryCount() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      terrain(f, true);
      int wall = wall(f, 14, 10);
      attributes(f, f.target).base().put(Stat.maxdamage, 1000);
      attributes(f, f.target).reset();
      f.world.process();
      assertTrue(baal(f).targetScore(f.target) > baal(f).targetScore(wall));
      float[] distance = {0};
      assertEquals(wall, baal(f).findNearestTargetWithAidist(distance));
      assertEquals(4, distance[0]);
      assertEquals(1, baal(f).lastTargetCount());
      assertEquals(baal(f).targetScore(wall), baal(f).lastMaxTargetScore());
    }
  }

  @Test
  void anyInitialTowardStepRetainsPrimaryRatherThanRequiringCompleteReachability() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      Map map = terrain(f, true);
      wall(f, 14, 10);
      f.world.process();
      // Target is east and the ray is blocked immediately. The third native
      // candidate (north-east), with the default budget >1, yields points.
      when(map.isBlockedForPath(any(), eq(11), eq(9), anyInt(), anyInt(), anyBoolean())).thenReturn(false);
      assertEquals(f.target, select(f));
      verifyNoInteractions(f.paths);
    }
  }

  @Test
  void partialRayRetainsPrimaryAndStopsAtCollisionWithoutInstallingMovement() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      Map map = terrain(f, false);
      wall(f, 14, 10);
      when(map.isBlockedForPath(any(), eq(13), eq(10), anyInt(), anyInt(), anyBoolean()))
          .thenReturn(true);
      f.world.process();
      assertEquals(f.target, select(f), "two cells of ray progress already provide path points");
      verify(map).isBlockedForPath(any(), eq(12), eq(10), anyInt(), anyInt(), anyBoolean());
      verify(map).isBlockedForPath(any(), eq(13), eq(10), anyInt(), anyInt(), anyBoolean());
      verify(map, never()).isBlockedForPath(any(), eq(14), eq(10), anyInt(), anyInt(), anyBoolean());
      verifyNoInteractions(f.paths);
    }
  }

  @Test
  void dynamicOccupancyAlsoClipsRayAfterPartialProgress() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrabClone")) {
      terrain(f, false);
      wall(f, 14, 10);
      f.world.process();
      DynamicUnitCollisionSystem dynamic = mock(DynamicUnitCollisionSystem.class);
      World engine = spy(f.world);
      doReturn(dynamic).when(engine).getSystem(DynamicUnitCollisionSystem.class);
      Riiablo.engine = engine;
      Map.Zone zone = f.world.getMapper(MapWrapper.class).get(f.actor).zone;
      when(dynamic.isFreeForPath(zone, f.actor, Engine.INVALID_ENTITY, 11, 10, 1)).thenReturn(true);
      when(dynamic.isFreeForPath(zone, f.actor, Engine.INVALID_ENTITY, 12, 10, 1)).thenReturn(true);
      assertEquals(f.target, select(f));
      verify(dynamic).isFreeForPath(zone, f.actor, Engine.INVALID_ENTITY, 13, 10, 1);
      verifyNoInteractions(f.paths);
    }
  }

  @Test
  void wrapperRejectsOwnerGroupTargetBeyondStrictHundredCellPerAxisBound() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      Map map = terrain(f, false);
      int wall = wall(f, 14, 10);
      int child = enemy(f, 110, 10);
      Monster converted = f.world.getMapper(Monster.class).get(child);
      converted.converted = true;
      converted.conversionOwnerId = f.target;
      f.world.getMapper(UnitStates.class).create(child).init(child).stateList
          .addState(StateId.CONVERSION, 100, 1, f.target);
      attributes(f, child).base().put(Stat.maxdamage, 1000);
      attributes(f, child).reset();
      f.world.process();
      assertEquals(child, select(f), "100 is still inside the native coordinate bound");
      clearInvocations(map);
      f.world.getMapper(Position.class).get(child).position.set(111, 10);
      assertEquals(wall, select(f), "101 clears native points before tracing");
      assertEquals(2, baal(f).lastTargetCount());
      verify(map, never()).isBlockedForPath(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
    }
  }

  @Test
  void zeroTargetUnitCoordinateDoesNotGuessRetainedNativePointCount() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      Map map = terrain(f, true);
      wall(f, 14, 10);
      f.targetPosition().set(0, 10);
      f.world.process();
      assertEquals(f.target, select(f));
      verify(map, never()).isBlockedForPath(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
    }
  }

  @Test
  void probeDirectionsPointToPrimaryNotToWall() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      Map map = terrain(f, true);
      int wall = wall(f, 6, 10); // west, opposite the primary
      f.world.process();
      assertEquals(wall, select(f));
      verify(map, atLeastOnce()).isBlockedForPath(any(), eq(11), eq(10), eq(DT1.Tile.FLAG_BLOCK_WALK), eq(1), anyBoolean());
      verify(map, never()).isBlockedForPath(any(), eq(9), anyInt(), anyInt(), anyInt(), anyBoolean());
    }
  }

  @Test
  void highestNode9ScoreIsChosenBeforeStrictFiveGridGate() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      terrain(f, true);
      int near = wall(f, 14, 10);
      int far = wall(f, 30, 10);
      attributes(f, far).base().put(Stat.maxdamage, 1000);
      attributes(f, far).reset();
      f.world.process();
      assertEquals(f.target, select(f), "do not prefilter distant high scorer and choose nearby lower scorer");
      attributes(f, far).base().put(Stat.hitpoints, 0f);
      attributes(f, far).reset();
      assertEquals(near, select(f));
      f.world.getMapper(Size.class).get(near).size = 9;
      for (int[] point : new int[][] {{15, 10, 0}, {14, 10, 1}, {13, 13, 1}, {14, 13, 0}}) {
        f.world.getMapper(Position.class).get(near).position.set(point[0], point[1]);
        assertEquals(point[2] == 1 ? near : f.target, select(f));
      }
    }
  }

  @Test
  void meleePrimaryAndMissingTerrainDoNotTriggerFallback() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      wall(f, 14, 10);
      f.world.process();
      assertEquals(f.target, select(f), "no terrain is not proof of an empty path");
      Map map = terrain(f, true);
      f.targetPosition().set(11, 10);
      assertEquals(f.target, select(f));
      verify(map, never()).isBlockedForPath(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
    }
  }

  @Test
  void attractAndConfuseCandidatesExpireWithStatesAndOrdinaryEvilMonstersStayExcluded() {
    for (int state : new int[] {StateId.ATTRACT, StateId.CONFUSE}) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
        terrain(f, true);
        int enemy = enemy(f, 14, 10);
        f.world.process();
        assertEquals(f.target, select(f));
        f.world.getMapper(UnitStates.class).create(enemy).init(enemy).stateList.addState(state, 100, 1, f.target);
        assertFalse(f.ai.isValidEnemyTargetWithoutAiScope(enemy), "ordinary faction scan stays unchanged");
        assertEquals(enemy, select(f));
        assertEquals(1, baal(f).lastTargetCount());
        f.world.getMapper(UnitStates.class).get(enemy).stateList.removeState(state);
        assertEquals(f.target, select(f));
      }
    }
  }

  @Test
  void conversionJoinsOwnerGroupAndDoesNotBecomeNode9() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      terrain(f, true);
      int converted = enemy(f, 30, 10);
      Monster monster = f.world.getMapper(Monster.class).get(converted);
      monster.converted = true;
      monster.conversionOwnerId = f.target;
      attributes(f, converted).base().put(Stat.maxdamage, 1000);
      f.world.getMapper(UnitStates.class).create(converted).init(converted).stateList
          .addState(StateId.CONVERSION, 100, 1, f.target);
      attributes(f, converted).reset();
      f.world.process();
      assertEquals(converted, select(f));
      assertEquals(2, baal(f).lastTargetCount());
      monster.converted = false;
      monster.conversionOwnerId = -1;
      assertEquals(f.target, select(f));
      assertEquals(1, baal(f).lastTargetCount());
    }
  }

  @Test
  void ownerRecreationReleasesConversionUntilNewStateInsertionButNotNode9Wall() {
    for (String ai : new String[] {"BaalCrab", "BaalCrabClone"}) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(ai)) {
        terrain(f, true);
        int converted = enemy(f, 30, 10);
        Monster monster = f.world.getMapper(Monster.class).get(converted);
        monster.converted = true;
        monster.conversionOwnerId = f.target;
        UnitStates states = f.world.getMapper(UnitStates.class).create(converted).init(converted);
        states.stateList.addState(StateId.CONVERSION, 100, 1, f.target);
        attributes(f, converted).base().put(Stat.maxdamage, 1000);
        attributes(f, converted).reset();
        int wall = wall(f, 14, 10);
        f.world.process();
        f.world.getMapper(Player.class).remove(f.target);
        f.world.process();
        f.world.getMapper(Player.class).create(f.target);
        f.world.process();
        assertEquals(wall, select(f), "independent node9 survives releasing the player group");
        assertEquals(1, baal(f).lastTargetCount());
        states.stateList.addState(StateId.CONVERSION, 200, 1, f.target);
        assertEquals(wall, select(f), "refresh is not an insertion into the replacement group");
        assertEquals(1, baal(f).lastTargetCount());
        states.stateList.removeState(StateId.CONVERSION);
        states.stateList.addState(StateId.CONVERSION, 200, 1, f.target);
        f.world.getMapper(SummonedPet.class).get(wall).boneWall = false;
        assertEquals(converted, select(f));
        assertEquals(2, baal(f).lastTargetCount(), "wall's old player insertion remains released");
      }
    }
  }

  @Test
  void convertedUnitStillDependsOnOwnerHeadCullButWallDoesNot() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      terrain(f, true);
      int converted = enemy(f, 14, 10);
      Monster monster = f.world.getMapper(Monster.class).get(converted);
      monster.converted = true;
      monster.conversionOwnerId = f.target;
      f.targetPosition().set(65, 10);
      f.world.getMapper(UnitStates.class).create(converted).init(converted).stateList
          .addState(StateId.CONVERSION, 100, 1, f.target);
      f.world.process();
      assertEquals(Engine.INVALID_ENTITY, select(f));
      assertEquals(0, baal(f).lastTargetCount());
      f.targetPosition().set(20, 10);
      int wall = wall(f, 14, 10);
      f.world.getMapper(SummonedPet.class).get(wall).ownerId = -1;
      monster.converted = false;
      f.world.process();
      assertEquals(wall, select(f), "node9 membership does not require a live/in-range owner");
    }
  }

  @Test
  void node9StillHonorsLifeModeThreatFlagsMapActAndTownSafety() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      Map map = terrain(f, true);
      int wall = wall(f, 14, 10);
      f.world.process();
      assertEquals(wall, select(f));
      NativeUnitFlags flags = f.world.getMapper(NativeUnitFlags.class).create(wall);
      assertEquals(f.target, select(f));
      flags.set(NativeUnitFlags.MONSTER_TARGET);
      assertEquals(wall, select(f));
      attributes(f, wall).base().put(Stat.hitpoints, 0f);
      attributes(f, wall).reset();
      assertEquals(f.target, select(f));
      attributes(f, wall).base().put(Stat.hitpoints, 100f);
      attributes(f, wall).reset();
      f.world.getMapper(Sequence.class).create(wall).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
      assertEquals(f.target, select(f));
      f.world.getMapper(Sequence.class).remove(wall);
      f.world.getMapper(Monster.class).get(wall).monstats.threat = 1;
      assertEquals(f.target, select(f));
      f.world.getMapper(Monster.class).get(wall).monstats.threat = 2;
      Map.Zone zone = zone(4);
      MapWrapper wrapper = f.world.getMapper(MapWrapper.class).create(wall).set(map, zone);
      assertEquals(wall, select(f));
      wrapper.map = mock(Map.class);
      assertEquals(f.target, select(f));
      wrapper.map = map;
      zone.level.Act = 3;
      assertEquals(f.target, select(f));
      zone.level.Act = 4;
      when(zone.isTown()).thenReturn(true);
      assertEquals(f.target, select(f));
    }
  }

  @Test
  void explicitOverrideIsNotReplacedByNode9AndMissingPrimaryDoesNotInventProbeTarget() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      Map map = terrain(f, true);
      wall(f, 14, 10);
      int forced = enemy(f, 30, 10);
      f.world.getMapper(NativeAiTargetOverride.class).create(f.actor).setAttract(forced, f.target, 86, 100);
      f.world.process();
      assertEquals(forced, select(f));
      f.world.getMapper(NativeAiTargetOverride.class).remove(f.actor);
      attributes(f, f.target).base().put(Stat.hitpoints, 0f);
      attributes(f, f.target).reset();
      assertEquals(Engine.INVALID_ENTITY, select(f));
      verify(map, never()).isBlockedForPath(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
    }
  }

  @Test
  void selectionDoesNotInstallMovementChangeVelocityOrConsumeRandomDraws() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrab")) {
      terrain(f, true);
      int wall = wall(f, 14, 10);
      f.world.process();
      GraphPath path = new GraphPath();
      path.add(new Point2(10, 10));
      path.add(new Point2(20, 10));
      Pathfind active = f.world.getMapper(Pathfind.class).create(f.actor).set(path);
      active.targetEntityId = f.target;
      f.world.getMapper(Velocity.class).get(f.actor).velocity.set(3, 4);
      Riiablo.gameSeed = 123;
      D2Seed seed = new D2Seed(123 ^ (f.actor * 0x45D9F3B), 666);
      assertEquals(wall, select(f));
      assertSame(path, active.path);
      assertEquals(f.target, active.targetEntityId);
      assertEquals(new Vector2(3, 4), f.world.getMapper(Velocity.class).get(f.actor).velocity);
      assertEquals(Seed.rollRandomNumber(seed) & 0xFFFFFFFFL, f.ai.rollAiRandomNumber());
      verifyNoInteractions(f.paths);
    }
  }

  @Test
  void cloneInheritsNode9Fallback() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrabClone")) {
      terrain(f, true);
      int wall = wall(f, 14, 10);
      f.world.process();
      assertEquals(wall, select(f));
      assertEquals(1, baal(f).lastTargetCount());
    }
  }

  private static Act1NativeAiIntegrationTest.Fixture fixture(String ai) {
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (ai.equalsIgnoreCase(row.AI)) {
        Act1NativeAiIntegrationTest.Fixture f = new Act1NativeAiIntegrationTest.Fixture(row.Id, 0, 10);
        attributes(f, f.target);
        return f;
      }
    }
    throw new AssertionError("No 1.10f " + ai + " row");
  }
  private static BaalCrab baal(Act1NativeAiIntegrationTest.Fixture f) { return (BaalCrab) f.ai; }
  private static int select(Act1NativeAiIntegrationTest.Fixture f) {
    return baal(f).findNearestTargetWithAidist(new float[] {Float.MAX_VALUE});
  }
  private static Attributes attributes(Act1NativeAiIntegrationTest.Fixture f, int id) {
    AttributesWrapper wrapper = f.world.getMapper(AttributesWrapper.class).create(id);
    if (wrapper.attrs == null) {
      wrapper.attrs = Attributes.obtainStandard();
      wrapper.attrs.base().put(Stat.hitpoints, 100f);
      wrapper.attrs.base().put(Stat.maxhp, 100f);
      wrapper.attrs.reset();
    }
    return wrapper.attrs;
  }
  private static int enemy(Act1NativeAiIntegrationTest.Fixture f, int x, int y) {
    int id = f.ally("skeleton1", 100, x, y);
    MonStats.Entry row = new MonStats.Entry();
    row.threat = 2;
    row.killable = true;
    f.world.getMapper(Monster.class).get(id).monstats = row;
    f.world.getMapper(Size.class).create(id).size = 1;
    return id;
  }
  private static int wall(Act1NativeAiIntegrationTest.Fixture f, int x, int y) {
    int id = enemy(f, x, y);
    f.world.getMapper(SummonedPet.class).create(id).set(f.target, "bonewall", 78, 1, true, 100).boneWall = true;
    return id;
  }
  private static Map.Zone zone(int act) {
    Map.Zone zone = mock(Map.Zone.class);
    zone.level = new Levels.Entry();
    zone.level.Act = act;
    return zone;
  }
  private static Map terrain(Act1NativeAiIntegrationTest.Fixture f, boolean blocked) {
    Map map = mock(Map.class);
    when(map.getAct()).thenReturn(-1);
    f.world.getMapper(MapWrapper.class).get(f.actor).set(map, zone(4));
    when(map.isBlockedForPath(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenReturn(blocked);
    return map;
  }
}
