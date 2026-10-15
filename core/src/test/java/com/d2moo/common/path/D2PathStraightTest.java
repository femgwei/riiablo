package com.d2moo.common.path;

import com.d2moo.common.collision.D2CommonCollision;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgCoords;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D2PathStraightTest {
  private static D2Path.Point p(int x, int y) { return new D2Path.Point(x,y); }
  private static D2PathResult.State state() {
    D2PathContinuation.State path = new D2PathContinuation.State(1);
    path.currentPoint = p(10,10); path.targetPoint = p(20,10); path.pathType = 7;
    path.setNewDistance(77); path.pathPointCount = 3; path.currentPointIndex = 2;
    D2PathResult.State s = new D2PathResult.State(path); s.velocity = 256;
    Arrays.fill(s.points,p(99,99));
    s.directionVectorX = 123; s.velocityVectorY = 456; s.newDirection = 13;
    return s;
  }
  private static void unitTarget(D2PathResult.State s) {
    s.path.targetUnit = new D2PathContinuation.Target() {
      public int unitType(){return 1;} public int distanceToOwner(){return 10;}
      public D2Path.Point samplePoint(){return p(20,10);}
    };
  }
  private static int compute(D2PathResult.State s, int type, D2Path.Point target,
      int maximum, int tolerance, D2Toward.Collision collision) {
    return D2PathDispatch.compute(s,s.path.currentPoint,target,type,maximum,tolerance,collision);
  }
  private static List<D2Path.Point> prefix(D2PathResult.State s, int count) {
    return Arrays.asList(Arrays.copyOf(s.points,count));
  }
  private static List<D2Path.Point> approaches() {
    return Arrays.asList(p(18,8),p(18,12),p(22,8),p(22,12),p(18,10),p(20,8),p(22,10),p(20,12));
  }

  @Test void typeOneOnlyWritesAcceptedPrefixAndIgnoresTowardDistanceToleranceAndOffset() {
    D2PathResult.State s = state(); s.path.directionOffset = -2;
    assertEquals(1,compute(s,1,p(20,10),0,255,(x,y)->false));
    assertEquals(p(20,10),s.points[0]); assertEquals(p(99,99),s.points[1]);
    assertEquals(3,s.path.pathPointCount); assertEquals(2,s.path.currentPointIndex);
    assertEquals(123,s.directionVectorX); assertEquals(456,s.velocityVectorY); assertEquals(13,s.newDirection);
    assertEquals(7,s.path.pathType);
  }

  @Test void failedTypeOneLeavesAllBufferSlotsAndLiveStateUntouched() {
    D2PathResult.State s = state();
    assertEquals(0,compute(s,1,p(20,10),77,0,(x,y)->true));
    for (D2Path.Point point : s.points) assertEquals(p(99,99),point);
    assertEquals(3,s.path.pathPointCount); assertEquals(2,s.path.currentPointIndex);
    assertEquals(123,s.directionVectorX); assertEquals(456,s.velocityVectorY);
  }

  @Test void unitApproachGateUsesLiveIdentityAndNativeShortCircuitOrder() {
    D2PathResult.State s = state(); unitTarget(s); List<D2Path.Point> queries = new ArrayList<>();
    assertEquals(0,compute(s,1,p(20,10),77,0,(x,y)->{queries.add(p(x,y));return true;}));
    assertEquals(approaches(),queries); assertEquals(p(99,99),s.points[0]);
    queries.clear();
    assertEquals(0,compute(s,1,p(20,10),77,0,(x,y)->{
      queries.add(p(x,y)); return !p(x,y).equals(p(22,8));
    }));
    assertEquals(approaches().subList(0,3),queries.subList(0,3));
    assertEquals(11,queries.size(),"three approach queries then eight blocked source neighbors");
  }

  @Test void coordinateTargetDoesNotRunUnitApproachGate() {
    D2PathResult.State s = state(); List<D2Path.Point> queries = new ArrayList<>();
    compute(s,1,p(20,10),77,0,(x,y)->{queries.add(p(x,y));return true;});
    assertEquals(8,queries.size()); assertEquals(p(9,9),queries.get(0));
  }

  @Test void approachOffsetsRemainInt32EvenThoughNodeCoordinatesAreWords() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2AStar.findNativePath(p(10,10),p(0,65535),(x,y)->{queries.add(p(x,y));return true;},true);
    assertEquals(Arrays.asList(p(-2,65533),p(-2,65537),p(2,65533),p(2,65537),
        p(-2,65535),p(0,65533),p(2,65535),p(0,65537)),queries);
  }

  @Test void nativeNeighborWritesWrapToUnsignedWordsWhileGeneralApiDoesNot() {
    List<D2Path.Point> nativeQueries = new ArrayList<>(), generalQueries = new ArrayList<>();
    D2AStar.findNativePath(p(0,0),p(1,1),(x,y)->{nativeQueries.add(p(x,y));return true;},false);
    D2AStar.findPath(p(0,0),p(1,1),(x,y)->{generalQueries.add(p(x,y));return true;});
    assertEquals(Arrays.asList(p(65535,65535),p(65535,1),p(1,65535),p(1,1),
        p(65535,0),p(0,65535),p(1,0),p(0,1)),nativeQueries);
    assertEquals(p(-1,-1),generalQueries.get(0));
  }

  @Test void nativeSignedWordHeuristicAndFScoreCanPreferTheOppositeBranch() {
    D2AStar.Collision corridor = (x,y)-> y != 10 || x < 9 || x > 11;
    // h(root)=32766; h(left) truncates to -32768 and f(left) to -32766.
    D2AStar.Result nativeResult = D2AStar.findNativePath(p(10,10),p(16393,10),corridor,false);
    D2AStar.Result generalResult = D2AStar.findPath(p(10,10),p(16393,10),corridor);
    assertEquals(Collections.singletonList(p(9,10)),nativeResult.path());
    assertEquals(Collections.singletonList(p(11,10)),generalResult.path());
    assertFalse(nativeResult.reachedTarget()); assertFalse(generalResult.reachedTarget());
  }

  @Test void nativeEntryRejectsNonWordCoordinatesButGeneralApiStillAcceptsThem() {
    assertThrows(IllegalArgumentException.class,()->D2AStar.findNativePath(p(-1,10),p(2,10),(x,y)->false,false));
    assertThrows(IllegalArgumentException.class,()->D2AStar.findNativePath(p(1,10),p(65536,10),(x,y)->false,false));
    assertTrue(D2AStar.findPath(p(-1,10),p(2,10),(x,y)->false).reachedTarget());
  }

  @Test void ordinaryInputsKeepExactQueriesPathsAndAllocationOfTheGeneralReference() {
    Random random = new Random(0x110f18);
    for (int i=0;i<1000;i++) {
      D2Path.Point start = p(200+random.nextInt(40),200+random.nextInt(40));
      D2Path.Point target = p(200+random.nextInt(40),200+random.nextInt(40));
      int seed = random.nextInt(); boolean unit = (i & 1) != 0;
      List<D2Path.Point> nativeQueries = new ArrayList<>(), generalQueries = new ArrayList<>();
      D2AStar.Result nativeResult = D2AStar.findNativePath(start,target,(x,y)->{
        nativeQueries.add(p(x,y)); return ((x*73856093 ^ y*19349663 ^ seed) & 7) == 0;
      },unit);
      D2AStar.Result generalResult = D2AStar.findPath(start,target,(x,y)->{
        generalQueries.add(p(x,y)); return ((x*73856093 ^ y*19349663 ^ seed) & 7) == 0;
      },unit);
      assertEquals(generalQueries,nativeQueries,"queries case "+i);
      assertEquals(generalResult.path(),nativeResult.path(),"path case "+i);
      assertEquals(generalResult.allocatedNodes(),nativeResult.allocatedNodes());
      assertEquals(generalResult.bestPoint(),nativeResult.bestPoint());
      assertEquals(generalResult.reachedTarget(),nativeResult.reachedTarget());
    }
  }

  @Test void straightAcceptsSuccessfulTowardWithoutAStarQueries() {
    D2PathResult.State s = state(); List<D2Path.Point> queries = new ArrayList<>(), expected = new ArrayList<>();
    D2PathResult.State toward = state();
    int count = compute(s,7,p(20,10),77,0,(x,y)->{queries.add(p(x,y));return false;});
    int towardCount = compute(toward,2,p(20,10),77,0,(x,y)->{expected.add(p(x,y));return false;});
    assertEquals(1,count); assertEquals(prefix(toward,towardCount),prefix(s,count)); assertEquals(expected,queries);
    assertEquals(1,s.path.pathPointCount); assertEquals(4096,s.velocityVectorX);
  }

  @Test void straightAcceptsNonExactTowardEndpointWithinTolerance() {
    D2PathResult.State s = state(); List<D2Path.Point> queries = new ArrayList<>();
    assertEquals(1,compute(s,7,p(20,10),77,2,(x,y)->{queries.add(p(x,y));return x == 19 && y == 10;}));
    assertEquals(p(18,10),s.points[0]); assertEquals(9,queries.size());
    assertEquals(4096,s.directionVectorX);
  }

  @Test void sourceEndpointWithinToleranceDoesNotBypassAStar() {
    D2PathResult.State s = state(); s.velocity = 0;
    assertEquals(1,compute(s,7,p(20,10),77,10,(x,y)->false));
    assertEquals(p(20,10),s.points[0],"Toward returned source, so Straight must try A*");
    assertEquals(1,s.path.pathPointCount); assertEquals(123,s.directionVectorX);
  }

  @Test void straightRadiusIncludesEighteenButNotNineteen() {
    for (int distance : new int[] {18,19}) {
      D2PathResult.State s = state(); s.velocity = 0; List<D2Path.Point> queries = new ArrayList<>();
      int count = compute(s,7,p(10+distance,10),0,0,(x,y)->{queries.add(p(x,y));return false;});
      assertEquals(distance == 18 ? 1 : 0,count);
      assertEquals(distance == 18,!queries.isEmpty());
      assertEquals(0,s.path.pathPointCount,"A* return count is not assigned by the entry");
    }
  }

  @Test void radiusIsEuclideanNotAnAxisAlignedOrApproximateDistanceBox() {
    D2PathResult.State s = state(); s.velocity = 0;
    assertEquals(0,compute(s,7,p(23,23),0,0,(x,y)->{fail("13 squared plus 13 squared exceeds 324");return true;}));
  }

  @Test void outsideRadiusReturnsTheOriginalTowardPrefixWithoutRetry() {
    D2PathResult.State s = state(); s.velocity = 0; List<D2Path.Point> queries = new ArrayList<>();
    assertEquals(1,compute(s,7,p(29,10),2,0,(x,y)->{queries.add(p(x,y));return false;}));
    assertEquals(Arrays.asList(p(11,10),p(12,10)),queries);
    assertEquals(p(12,10),s.points[0]); assertEquals(1,s.path.pathPointCount);
  }

  @Test void straightUsesTheLiveOffsetBranchWithoutInventingProbeVectors() {
    D2PathResult.State s = state(); s.path.directionOffset = 2;
    assertEquals(1,compute(s,7,p(20,10),1,0,(x,y)->false));
    assertEquals(p(20,10),s.points[0]); assertEquals(1,s.path.pathPointCount);
    assertEquals(123,s.directionVectorX); assertEquals(456,s.velocityVectorY);
  }

  @Test void continuationDoesNotTurnFailedAStarOrStraightIntoWallFollow() {
    for (int type : new int[] {1,7}) {
      D2PathResult.State s = state(); s.path.pathType = type; s.path.setDistance(20);
      List<Integer> types = new ArrayList<>();
      assertEquals(0,D2PathContinuation.resume(s.path,false,new D2PathContinuation.Effects() {
        public void refresh(D2PathContinuation.State path) {}
        public int search(D2PathContinuation.State path) {
          types.add(path.pathType); return compute(s,path.pathType,p(20,10),77,0,(x,y)->true);
        }
      }));
      assertEquals(Collections.singletonList(type),types); assertEquals(type,s.path.pathType);
    }
  }

  @Test void failedAStarRerunsTowardOnlyWhenItOriginallyReturnedPoints() {
    for (int maximum : new int[] {0,2}) {
      D2PathResult.State s = state(); s.velocity = 0; unitTarget(s);
      List<D2Path.Point> queries = new ArrayList<>();
      assertEquals(maximum == 0 ? 0 : 1,compute(s,7,p(20,10),maximum,0,(x,y)->{
        queries.add(p(x,y)); return approaches().contains(p(x,y));
      }));
      List<D2Path.Point> expected = new ArrayList<>();
      if (maximum != 0) expected.addAll(Arrays.asList(p(11,10),p(12,10)));
      expected.addAll(approaches());
      if (maximum != 0) expected.addAll(Arrays.asList(p(11,10),p(12,10)));
      assertEquals(expected,queries); assertEquals(maximum == 0 ? 0 : 1,s.path.pathPointCount);
      assertEquals(maximum == 0 ? p(20,10) : p(12,10),s.points[0]);
    }
  }

  @Test void failedAStarRecomputesRatherThanRestoringTheOldTowardBuffer() {
    D2PathResult.State s = state(); s.velocity = 0; unitTarget(s); int[] firstSteps = {0};
    assertEquals(0,compute(s,7,p(20,10),2,0,(x,y)-> {
      if (x == 11 && y == 10) return ++firstSteps[0] > 1;
      if (x == 12 && y == 10) return false;
      return true;
    }));
    assertEquals(2,firstSteps[0]); assertEquals(0,s.path.pathPointCount);
    assertEquals(p(20,10),s.points[0],"second failed Toward leaves its temporary target");
  }

  @Test void aStarSuccessMayReturnDifferentCountButLeavesTowardCountAndEarlyVectors() {
    D2PathResult.State s = state();
    D2Toward.Collision wall = (x,y)->x == 12 && y >= 9 && y <= 11;
    int count = compute(s,7,p(20,10),1,0,wall);
    assertTrue(count > 1); assertEquals(p(20,10),s.points[count-1]);
    assertEquals(1,s.path.pathPointCount); assertEquals(0,s.path.currentPointIndex);
    assertEquals(4096,s.directionVectorX); assertEquals(0,s.directionVectorY);
    assertEquals(4096,s.velocityVectorX); assertEquals(0,s.velocityVectorY);
    assertNotEquals(10,s.points[0].y,"outer Request, not A*, will renormalize to the detour");
  }

  @Test void radiusUsesNativeSignedInt32OverflowNotLongMath() {
    D2PathResult.State s = state(); s.velocity = 0; unitTarget(s); List<D2Path.Point> queries = new ArrayList<>();
    // 50000 squared wraps negative, taking the A* branch despite exceeding 18 mathematically.
    assertEquals(0,compute(s,7,p(50010,10),0,0,(x,y)->{queries.add(p(x,y));return true;}));
    assertEquals(8,queries.size()); assertEquals(p(50008,8),queries.get(0));
  }

  @Test void aStarPartialResultIsNotRequiredToReachTheStraightTarget() {
    D2PathResult.State s = state(); s.velocity = 0;
    int count = compute(s,7,p(20,10),0,0,(x,y)->y != 10 || x > 15 || x < 10);
    assertEquals(1,count); assertEquals(p(15,10),s.points[0]);
    assertEquals(0,s.path.pathPointCount);
  }

  @Test void concreteRequestsInstallReturnedCountAfterRestoringFootprintsAndNormalizeTheDetour() {
    for (int type : new int[] {1,7}) {
      D2ActiveRoom grid = new D2ActiveRoom(); D2DrlgCoords coords = new D2DrlgCoords();
      coords.setNSubtileWidth(50); coords.setNSubtileHeight(50); grid.setCoords(coords);
      D2CommonCollision.allocRoomCollisionGrid(grid);
      D2PathFootprint.Unit owner = D2PathFootprint.Unit.dynamic(D2PathUnits.Unit.dynamic(1,2,
          new D2PathUnits.DynamicPath(D2PathStep.toFp16Center(10),D2PathStep.toFp16Center(10),0)),grid,1,1,0x100);
      D2PathFootprint.Unit target = D2PathFootprint.Unit.dynamic(D2PathUnits.Unit.dynamic(1,2,
          new D2PathUnits.DynamicPath(D2PathStep.toFp16Center(20),D2PathStep.toFp16Center(10),0)),grid,1,1,0x100);
      D2PathFootprint.add(owner); D2PathFootprint.add(target);
      for (int y=9;y<=11;y++) D2CommonCollision.setMask(grid,12,y,1);
      D2PathResult.State s = state(); s.path.pathType = type; unitTarget(s);
      s.path.pathFlags = 0x800; s.path.setNewDistance(1);
      s.room = new D2PathResult.Room(0,0,50,50);
      D2PathRequest.State request = new D2PathRequest.State(s,target.coordinates,
          new D2PathRequest.Room(s.room,false),false,0,2,1,0x501,70);
      int count = D2PathRequest.compute(request,false,new D2PathDispatch.Effects(request,owner,target));
      assertTrue(count > 1); assertEquals(count,s.path.pathPointCount);
      assertEquals(p(20,10),s.points[count-1]); assertNotEquals(0,s.velocityVectorY);
      assertEquals(0x1100,D2CommonCollision.checkMask(grid,10,10,0xffff));
      assertEquals(0x1100,D2CommonCollision.checkMask(grid,20,10,0xffff));
      assertEquals(type,s.path.pathType,"neither entry silently switches to WallFollow");
    }
  }
}
