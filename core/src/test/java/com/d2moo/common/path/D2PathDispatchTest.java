package com.d2moo.common.path;

import com.d2moo.common.collision.D2CommonCollision;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgCoords;
import com.d2moo.common.drlg.D2Seed;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D2PathDispatchTest {
  @Test void coordinateSearchRejectsFractionalMovementBeforeQueriesWritesOrStreamSeed() {
    for (int type : new int[] {0, 2, 16}) {
      for (boolean xAxis : new boolean[] {false, true}) {
        D2Seed seed = new D2Seed(123, 456);
        D2PathRequest.Search search = savedSearch(type, () -> {}, seed, false);
        D2PathResult.State s = search.state.result;
        if (xAxis) s.precisionX ^= 1; else s.precisionY ^= 1;
        D2Path.Point[] before = s.points.clone(); long seedBefore = seed.getLSeed();
        int count = s.path.pathPointCount, index = s.path.currentPointIndex;
        int directionX = s.directionVectorX, velocityY = s.velocityVectorY;
        assertThrows(IllegalArgumentException.class, () -> D2PathDispatch.compute(search,
            (x,y) -> { fail("fractional movement must precede queries"); return false; }));
        assertArrayEquals(before, s.points); assertEquals(seedBefore, seed.getLSeed());
        assertEquals(count, s.path.pathPointCount); assertEquals(index, s.path.currentPointIndex);
        assertEquals(directionX, s.directionVectorX); assertEquals(velocityY, s.velocityVectorY);
      }
    }
  }

  @Test void unchangedCoordinateSearchStillDispatchesAcrossBranches() {
    for (int type : new int[] {0, 2, 16}) {
      D2PathRequest.Search search = savedSearch(type, () -> {}, new D2Seed(123, 456), false);
      assertNull(search.state.result.path.targetUnit);
      int count = assertDoesNotThrow(() -> D2PathDispatch.compute(search, (x,y) -> false));
      assertTrue(count >= 0 && count <= search.state.result.points.length);
    }
  }

  @Test void newCoordinateSearchCanCaptureMovedPrecision() {
    for (int type : new int[] {0, 2, 16}) {
      D2PathRequest.Search old = savedSearch(type, () -> {}, new D2Seed(123, 456), false);
      old.state.result.precisionX ^= 1;
      D2PathRequest.Search fresh = captureSearch(old.state);
      assertNotSame(old, fresh);
      int count = assertDoesNotThrow(() -> D2PathDispatch.compute(fresh, (x,y) -> false));
      assertTrue(count >= 0 && count <= fresh.state.result.points.length);
    }
  }

  @Test void savedSearchRejectsReplacedResultRoomBeforeQueriesWritesOrStreamSeed() {
    for (int type : new int[] {0, 2, 16}) {
      D2Seed seed = new D2Seed(123, 456);
      D2PathRequest.Search search = savedSearch(type, () -> {}, seed);
      D2PathResult.State s = search.state.result;
      // Equal geometry is still a different projection from the saved start room.
      s.room = new D2PathResult.Room(10, 10, 30, 30);
      D2Path.Point[] before = s.points.clone(); long seedBefore = seed.getLSeed();
      int count = s.path.pathPointCount, index = s.path.currentPointIndex;
      int directionX = s.directionVectorX, velocityY = s.velocityVectorY;
      assertThrows(IllegalArgumentException.class, () -> D2PathDispatch.compute(search,
          (x,y) -> { fail("room mismatch must precede collision queries"); return false; }));
      assertArrayEquals(before, s.points); assertEquals(seedBefore, seed.getLSeed());
      assertEquals(count, s.path.pathPointCount); assertEquals(index, s.path.currentPointIndex);
      assertEquals(directionX, s.directionVectorX); assertEquals(velocityY, s.velocityVectorY);
    }
  }

  private static D2PathRequest.Search savedSearch(int type, Runnable guard, D2Seed seed) {
    return savedSearch(type, guard, seed, true);
  }

  private static D2PathRequest.Search savedSearch(int type, Runnable guard, D2Seed seed, boolean observed) {
    D2PathResult.State s = state(); s.path.pathType = type;
    s.precisionX = 10 << 16; s.precisionY = 10 << 16;
    D2PathUnits.Unit owner = D2PathUnits.Unit.dynamic(1, 2,
        new D2PathUnits.DynamicPath(10 << 16, 10 << 16, 0));
    D2PathUnits.Unit target = observed ? D2PathUnits.Unit.stationary(4, 1, p(20, 10)) : null;
    if (observed) s.path.targetUnit = D2PathUnits.observation(owner, target, 0, guard);
    s.room = new D2PathResult.Room(10, 10, 30, 30);
    D2PathRequest.State request = new D2PathRequest.State(s, target,
        new D2PathRequest.Room(s.room, false), false, 0, 2, 1, 0x501, 70, seed);
    return captureSearch(request);
  }

  private static D2PathRequest.Search captureSearch(D2PathRequest.State request) {
    D2PathRequest.Search[] saved = {null};
    D2PathRequest.compute(request, false, new D2PathRequest.Effects() {
      public boolean removeOwner(D2PathRequest.State state) { return false; }
      public boolean removeTarget(D2PathRequest.State state) { return false; }
      public void addOwner(D2PathRequest.State state) {}
      public void addTarget(D2PathRequest.State state) {}
      public boolean blocked(D2PathRequest.State state, int x, int y) { return false; }
      public int search(D2PathRequest.Search search) { saved[0] = search; return 0; }
    });
    return java.util.Objects.requireNonNull(saved[0]);
  }

  @Test void savedSearchRejectsExpiredLifetimeBeforePointsQueriesOrStreamSeed() {
    for (int type : new int[] {0, 2, 16}) {
      boolean[] live = {true}; D2Seed seed = new D2Seed(123, 456);
      D2PathRequest.Search search = savedSearch(type, () -> {
        if (!live[0]) throw new IllegalStateException("expired lifetime");
      }, seed);
      D2PathResult.State s = search.state.result;
      D2Path.Point[] before = s.points.clone(); long seedBefore = seed.getLSeed();
      int count = s.path.pathPointCount, index = s.path.currentPointIndex;
      live[0] = false;
      assertThrows(IllegalStateException.class, () -> D2PathDispatch.compute(search,
          (x,y) -> { fail("stale search must not query"); return false; }));
      assertArrayEquals(before, s.points); assertEquals(seedBefore, seed.getLSeed());
      assertEquals(count, s.path.pathPointCount); assertEquals(index, s.path.currentPointIndex);
    }
  }

  @Test void savedSearchRejectsDifferentCurrentObservationEvenWhenBothGuardsAreValid() {
    for (int type : new int[] {0, 2, 16}) {
      D2Seed seed = new D2Seed(123, 456);
      D2PathRequest.Search search = savedSearch(type, () -> {}, seed);
      D2PathResult.State s = search.state.result;
      D2PathUnits.Observation old = (D2PathUnits.Observation) s.path.targetUnit;
      s.path.targetUnit = D2PathUnits.observation(old.owner, old.target, old.speed);
      D2Path.Point[] before = s.points.clone(); long seedBefore = seed.getLSeed();
      assertThrows(IllegalArgumentException.class, () -> D2PathDispatch.compute(search,
          (x,y) -> { fail("replacement must precede queries"); return false; }));
      assertArrayEquals(before, s.points); assertEquals(seedBefore, seed.getLSeed());
    }
  }

  @Test void savedCurrentSearchStillDispatchesAcrossRoomAwareAndLocalBranches() {
    for (int type : new int[] {0, 2, 16}) {
      D2PathRequest.Search search = savedSearch(type, () -> {}, new D2Seed(123, 456));
      int count = assertDoesNotThrow(() -> D2PathDispatch.compute(search, (x,y) -> false));
      assertTrue(count >= 0 && count <= search.state.result.points.length);
    }
  }
  @Test void directDispatchRejectsStaleObservationBeforeWritesEvenWithoutCollisionQueries() {
    for (int type : new int[] {2, 12, 15}) {
      D2PathResult.State s = state();
      D2PathUnits.Unit owner = D2PathUnits.Unit.dynamic(1, 2, null);
      s.path.targetUnit = D2PathUnits.observation(owner, owner, 0, () -> {
        throw new IllegalStateException("stale lifetime");
      });
      D2Path.Point[] before = s.points.clone();
      assertThrows(IllegalStateException.class, () -> compute(s, p(20,10), type, 10, 0,
          (x,y) -> { fail("stale observation must precede queries"); return false; }));
      assertArrayEquals(before, s.points);
      assertEquals(3, s.path.pathPointCount); assertEquals(2, s.path.currentPointIndex);
      assertEquals(123, s.directionVectorX); assertEquals(456, s.velocityVectorY);
    }
  }

  @Test void directDispatchAcceptsCurrentStandardObservation() {
    D2PathResult.State s = state();
    D2PathUnits.Unit owner = D2PathUnits.Unit.dynamic(1, 2, null);
    int[] checks = {0};
    s.path.targetUnit = D2PathUnits.observation(owner, owner, 0, () -> checks[0]++);
    assertEquals(1, compute(s, p(20,10), 2, 77, 0, (x,y) -> false));
    assertTrue(checks[0] > 0);
  }
  private static D2Path.Point p(int x, int y) { return new D2Path.Point(x,y); }
  private static D2PathResult.State state() {
    D2PathContinuation.State path = new D2PathContinuation.State(1);
    path.currentPoint = p(10,10); path.targetPoint = p(20,10); path.pathType = 2;
    path.setNewDistance(77); path.pathPointCount = 3; path.currentPointIndex = 2;
    D2PathResult.State s = new D2PathResult.State(path); s.velocity = 256;
    Arrays.fill(s.points, p(99,99));
    s.directionVectorX = 123; s.velocityVectorY = 456; s.newDirection = 13;
    return s;
  }
  private static int compute(D2PathResult.State s, D2Path.Point target, int type, int distance, int tolerance,
      D2Toward.Collision collision) {
    return D2PathDispatch.compute(s, s.path.currentPoint, target, type, distance, tolerance, collision);
  }
  private static List<D2Path.Point> points(D2PathResult.State s, int count) {
    return Arrays.asList(Arrays.copyOf(s.points, count));
  }

  @Test void allTowardAliasesResetIndexCountAndNormalizeTheTemporaryTargetBeforeQueries() {
    for (int type : new int[] {2,3,5,6,13}) {
      D2PathResult.State s = state();
      int count = compute(s, p(20,10), type, 77, 0, (x,y) -> {
        assertEquals(0, s.path.pathPointCount); assertEquals(0, s.path.currentPointIndex);
        assertEquals(p(20,10), s.points[0]); assertEquals(4096, s.directionVectorX);
        assertEquals(4096, s.velocityVectorX); assertEquals(0, s.velocityVectorY);
        return false;
      });
      assertEquals(1, count); assertEquals(Collections.singletonList(p(20,10)), points(s,count));
      assertEquals(0, s.path.currentPointIndex); assertEquals(p(99,99), s.points[1]);
      assertEquals(2, s.path.pathType, "snapshot dispatch does not assign live type");
    }
  }

  @Test void nonzeroLiveOffsetBypassesRayAndNormalizationWithoutReapplyingTheTypeTable() {
    D2PathResult.State s = state(); s.path.directionOffset = -2; s.velocity = 0;
    List<D2Path.Point> expected = D2Toward.compute(p(10,10),p(20,10),4,0,-2,(x,y)->false).points();
    int count = compute(s,p(20,10),13,4,0,(x,y)->false);
    assertEquals(expected, points(s,count)); assertEquals(-2,s.path.directionOffset);
    assertEquals(123,s.directionVectorX); assertEquals(456,s.velocityVectorY); assertEquals(13,s.newDirection);
  }

  @Test void initialRayIsQueriedExactlyOnceAndLocalTailMatchesThePureKernel() {
    D2PathResult.State s = state(); List<D2Path.Point> actualQueries = new ArrayList<>(), expectedQueries = new ArrayList<>();
    D2Toward.Collision expectedCollision = (x,y) -> { expectedQueries.add(p(x,y)); return x == 14 && y == 10; };
    List<D2Path.Point> expected = D2Toward.compute(p(10,10),p(20,10),10,0,0,expectedCollision).points();
    int count = compute(s,p(20,10),2,10,0,(x,y) -> { actualQueries.add(p(x,y)); return x == 14 && y == 10; });
    assertEquals(expected,points(s,count)); assertEquals(expectedQueries,actualQueries);
  }

  @Test void zeroVelocitySkipsRayButCanStillGenerateLocalMovementPoints() {
    D2PathResult.State s = state(); s.velocity = 0;
    List<D2Path.Point> queries = new ArrayList<>();
    int count = compute(s,p(20,10),2,3,0,(x,y) -> { queries.add(p(x,y)); return false; });
    assertEquals(Collections.singletonList(p(13,10)),points(s,count));
    assertEquals(Arrays.asList(p(11,10),p(12,10),p(13,10)),queries);
    assertEquals(0,s.velocity); assertEquals(123,s.directionVectorX); assertEquals(456,s.velocityVectorY);
  }

  @Test void zeroVelocityWithinToleranceEmitsSourceWithoutAnyCollisionQuery() {
    D2PathResult.State s = state(); s.velocity = 0;
    assertEquals(1,compute(s,p(11,10),2,77,2,(x,y)->{ fail("no ray or local query"); return true; }));
    assertEquals(p(10,10),s.points[0]); assertEquals(123,s.directionVectorX);
  }

  @Test void exactCenterProbeWithZeroLiveCountZeroesVelocityAndVectorsWithoutInventingAnIndex() {
    D2PathResult.State s = state();
    assertEquals(1,compute(s,p(10,10),2,77,0,(x,y)->{ fail("exhausted probe has no ray"); return true; }));
    assertEquals(0,s.path.currentPointIndex); assertEquals(0,s.velocity);
    assertEquals(0,s.directionVectorX); assertEquals(0,s.directionVectorY);
    assertEquals(0,s.velocityVectorX); assertEquals(0,s.velocityVectorY); assertEquals(13,s.newDirection);
    assertEquals(p(10,10),s.points[0]);
  }

  @Test void sameIntegerTileButOffCenterProbeStillNormalizesAndRunsTheRay() {
    D2PathResult.State s = state(); s.precisionX -= 1;
    assertEquals(1,compute(s,p(10,10),2,77,0,(x,y)->{ fail("zero-length integer ray is clear"); return true; }));
    assertEquals(256,s.velocity); assertEquals(4096,s.directionVectorX); assertEquals(4096,s.velocityVectorX);
  }

  @Test void failedZeroDistanceSearchRetainsTheTemporaryTargetSlotAndEarlyVectors() {
    D2PathResult.State s = state();
    assertEquals(0,compute(s,p(20,10),2,0,0,(x,y)->true));
    assertEquals(p(20,10),s.points[0]); assertEquals(p(99,99),s.points[1]);
    assertEquals(0,s.path.pathPointCount); assertEquals(4096,s.velocityVectorX);
  }

  @Test void rayAxisAssertionsOccurAfterTemporaryTargetAndIndexWrites() {
    D2PathResult.State s = state();
    assertThrows(IllegalStateException.class,()->compute(s,p(0,10),2,10,0,(x,y)->false));
    assertEquals(p(0,10),s.points[0]); assertEquals(0,s.path.pathPointCount); assertEquals(0,s.path.currentPointIndex);
    s.path.directionOffset = 2;
    assertDoesNotThrow(()->compute(s,p(0,10),5,2,0,(x,y)->false),"offset branch never enters #ABF0 axis assertions");
  }

  @Test void wallFollowUsesLiveDistMaxAndDoesNotAssignCountIndexOrNormalizeVectors() {
    D2PathResult.State s = state(); s.path.setNewDistance(4);
    List<D2Path.Point> expected = D2WallFollow.compute(p(10,10),p(13,10),4,false,(x,y)->false).points();
    int count = compute(s,p(13,10),15,0,255,(x,y)->false);
    assertEquals(expected,points(s,count)); assertEquals(1,count);
    assertEquals(3,s.path.pathPointCount); assertEquals(2,s.path.currentPointIndex);
    assertEquals(123,s.directionVectorX); assertEquals(456,s.velocityVectorY); assertEquals(p(99,99),s.points[1]);
  }

  @Test void wallFollowTargetMinimumFortyUsesLiveIdentityAndShortFailurePreservesOldBuffer() {
    D2PathResult.State s = state(); s.path.setNewDistance(3);
    s.path.targetUnit = new D2PathContinuation.Target() {
      public int unitType(){return 2;} public int distanceToOwner(){return 99;}
      public D2Path.Point samplePoint(){return p(20,10);}
    };
    assertEquals(1,compute(s,p(20,10),15,0,0,(x,y)->false)); assertEquals(p(20,10),s.points[0]);
    s.path.targetUnit = null; s.points[0] = p(99,99);
    assertEquals(0,compute(s,p(12,10),15,77,0,(x,y)->{fail("<=2 line not queried");return true;}));
    assertEquals(p(99,99),s.points[0]); assertEquals(3,s.path.pathPointCount);
  }

  @Test void unsupportedAndNullEntriesAreExplicitAndNeverFallBackToCoordinateAStar() {
    D2PathResult.State s = state();
    for (int type : new int[] {0,16})
      assertThrows(UnsupportedOperationException.class,()->compute(s,p(20,10),type,77,0,(x,y)->false));
    for (int type : new int[] {4,10,14,17})
      assertThrows(IllegalStateException.class,()->compute(s,p(20,10),type,77,0,(x,y)->false));
    assertEquals(p(99,99),s.points[0]); assertEquals(3,s.path.pathPointCount); assertEquals(2,s.path.currentPointIndex);
  }

  @Test void invalidInputAndUnsynchronizedPrecisionAreRejected() {
    D2PathResult.State s = state(); s.precisionX = D2PathStep.toFp16Center(11);
    assertThrows(IllegalArgumentException.class,()->compute(s,p(20,10),2,77,0,(x,y)->false));
    s.precisionX = D2PathStep.toFp16Center(10); s.path.pathFlags = 0x40000;
    assertThrows(IllegalArgumentException.class,()->compute(s,p(20,10),2,77,0,(x,y)->false));
    s.path.pathFlags = 0;
    assertThrows(IllegalArgumentException.class,()->compute(s,p(20,10),2,78,0,(x,y)->false));
    assertThrows(IllegalArgumentException.class,()->compute(s,p(20,10),2,77,256,(x,y)->false));
    assertThrows(IllegalArgumentException.class,()->compute(s,p(-1,10),2,77,0,(x,y)->false));
  }

  @Test void liveMissileTypeRayAssertionIsIndependentOfTheSnapshotTableEntryAndVelocityGate() {
    D2PathResult.State s = state(); s.path.pathType = 4; s.velocity = 0;
    assertThrows(IllegalStateException.class,()->compute(s,p(20,10),2,77,0,(x,y)->false));
    assertEquals(p(20,10),s.points[0]); assertEquals(0,s.path.pathPointCount);
    assertEquals(123,s.directionVectorX);
  }

  @Test void zeroBudgetOffsetKeepsStaleSlotZeroAndDoesNotRunTheRayProbe() {
    D2PathResult.State s = state(); s.path.directionOffset = 2;
    assertEquals(0,compute(s,p(20,10),5,0,255,(x,y)->{fail("zero offset budget has no query");return true;}));
    assertEquals(p(99,99),s.points[0]); assertEquals(0,s.path.currentPointIndex);
    assertEquals(123,s.directionVectorX); assertEquals(456,s.velocityVectorY);
  }

  @Test void reversedFacingFlagAffectsEarlyProbeButNotTheRayEndpoint() {
    D2PathResult.State s = state(); s.path.pathFlags = 0x200; s.direction = 0;
    D2PathStep.DirectionVector expected = D2PathStep.directionVector(s.precisionX,s.precisionY,
        D2PathStep.toFp16Center(20),D2PathStep.toFp16Center(10));
    assertEquals(1,compute(s,p(20,10),2,77,0,(x,y)->{
      assertEquals((expected.direction-32)&63,s.newDirection); return false;
    }));
    assertEquals(p(20,10),s.points[0]); assertEquals(4096,s.velocityVectorX);
  }

  private static D2ActiveRoom grid() {
    D2ActiveRoom room = new D2ActiveRoom(); D2DrlgCoords c = new D2DrlgCoords();
    c.setNSubtileWidth(50);c.setNSubtileHeight(50);room.setCoords(c);
    D2CommonCollision.allocRoomCollisionGrid(room); return room;
  }
  private static D2PathFootprint.Unit footprint(D2ActiveRoom grid) {
    return D2PathFootprint.Unit.dynamic(D2PathUnits.Unit.dynamic(1,2,new D2PathUnits.DynamicPath(
        D2PathStep.toFp16Center(10),D2PathStep.toFp16Center(10),0)),grid,1,1,0x100);
  }
  private static D2PathRequest.State request(D2PathResult.State s) {
    s.room = new D2PathResult.Room(0,0,50,50);
    return new D2PathRequest.State(s,null,new D2PathRequest.Room(s.room,false),false,0,2,1,0x501,70);
  }

  @Test void concreteRequestEffectsPerformRealFootprintsSearchAndFinalNormalization() {
    D2ActiveRoom grid = grid(); D2PathFootprint.Unit owner = footprint(grid);
    D2PathFootprint.add(owner); D2PathResult.State s = state();
    D2PathRequest.State request = request(s);
    assertEquals(1,D2PathRequest.compute(request,false,new D2PathDispatch.Effects(request,owner,null)));
    assertEquals(p(20,10),s.points[0]); assertEquals(0x1100,D2CommonCollision.checkMask(grid,10,10,0xffff));
    assertEquals(4096,s.velocityVectorX); assertTrue((s.path.pathFlags & 0x20)!=0);
  }

  @Test void skillMovementEntriesComposeWithRequestFootprintRestorationAndNormalization() {
    for (int type : new int[] {8,9,11,12}) {
      D2ActiveRoom grid=grid(); D2PathFootprint.Unit owner=footprint(grid);
      D2PathFootprint.add(owner); D2PathResult.State s=state(); D2PathTypes.setType(s,type);
      D2PathRequest.State r=request(s);
      assertTrue(D2PathRequest.compute(r,false,new D2PathDispatch.Effects(r,owner,null)) > 0);
      if (type != 12) assertEquals(p(20,10),s.points[0]);
      assertEquals(0x1100,D2CommonCollision.checkMask(grid,10,10,0xffff));
      assertEquals(type == 12 ? -4096 : 4096,s.velocityVectorX);
    }
  }

  @Test void concreteDispatcherComposesWithContinuationTowardToWallFollowRetry() {
    D2ActiveRoom grid = grid(); D2PathFootprint.Unit owner = footprint(grid); D2PathFootprint.add(owner);
    // Each Toward first step is blocked. WallFollow sees the same surrounding grid and also fails.
    for (D2Path.Point point : Arrays.asList(p(11,10),p(11,9),p(11,11),p(10,9),p(10,11),p(9,9),p(9,10),p(9,11)))
      D2CommonCollision.setMask(grid,point.x,point.y,1);
    D2PathResult.State s = state(); s.path.currentPointIndex = 0; s.path.setDistance(20);
    s.path.finalTargetPoint = p(20,10);
    D2PathRequest.State request = request(s); List<Integer> types = new ArrayList<>();
    D2PathDispatch.Effects effects = new D2PathDispatch.Effects(request,owner,null);
    assertEquals(0,D2PathContinuation.resume(s.path,false,new D2PathContinuation.Effects() {
      public void refresh(D2PathContinuation.State path){}
      public int search(D2PathContinuation.State path) {
        types.add(path.pathType); return D2PathRequest.compute(request,false,effects);
      }
    }));
    assertEquals(Arrays.asList(2,15),types); assertEquals(0x1100,D2CommonCollision.checkMask(grid,10,10,0xffff));
  }

  @Test void nullSearchEntryStillRestoresTheOwnerFootprintThroughTheRequestFinally() {
    D2ActiveRoom grid = grid(); D2PathFootprint.Unit owner = footprint(grid); D2PathFootprint.add(owner);
    D2PathResult.State s = state(); s.path.pathType = 17;
    D2PathRequest.State request = request(s);
    assertThrows(IllegalStateException.class,()->D2PathRequest.compute(request,false,
        new D2PathDispatch.Effects(request,owner,null)));
    assertEquals(0x1100,D2CommonCollision.checkMask(grid,10,10,0xffff));
    assertEquals(0,s.path.pathPointCount);
  }
}
