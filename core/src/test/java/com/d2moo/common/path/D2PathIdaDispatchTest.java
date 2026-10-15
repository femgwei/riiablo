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

class D2PathIdaDispatchTest {
  private static D2Path.Point p(int x,int y) {return new D2Path.Point(x,y);}
  private static D2PathResult.State state() {
    D2PathContinuation.State path=new D2PathContinuation.State(1);
    path.currentPoint=p(60,60); path.targetPoint=p(70,60); path.pathType=0; path.setNewDistance(77);
    D2PathResult.State s=new D2PathResult.State(path); s.room=new D2PathResult.Room(50,50,50,50);
    s.velocity=256; s.directionVectorX=123; s.velocityVectorY=456;
    Arrays.fill(s.points,p(99,99)); return s;
  }
  private static D2PathRequest.State request(D2PathResult.State s,D2PathUnits.Unit target,int ceiling) {
    return request(s,target,ceiling,null);
  }
  private static D2PathRequest.State request(D2PathResult.State s,D2PathUnits.Unit target,int ceiling,D2Seed seed) {
    return new D2PathRequest.State(s,target,new D2PathRequest.Room(s.room,false),false,0,2,1,0x501,ceiling,seed);
  }
  private static boolean wall(int x,int y) { return x==62 && y>=59 && y<=61; }
  private static class Effects implements D2PathRequest.Effects {
    final D2Toward.Collision collision;
    final List<String> events=new ArrayList<>();
    Effects(D2Toward.Collision collision){this.collision=collision;}
    public boolean removeOwner(D2PathRequest.State s){events.add("removeOwner");return true;}
    public boolean removeTarget(D2PathRequest.State s){events.add("removeTarget");return true;}
    public void addTarget(D2PathRequest.State s){events.add("addTarget");}
    public void addOwner(D2PathRequest.State s){events.add("addOwner");}
    public boolean blocked(D2PathRequest.State s,int x,int y){return collision.blocked(x,y);}
    public int search(D2PathRequest.Search search){events.add("search");return D2PathDispatch.compute(search,collision);}
  }

  @Test void roomAwareEntryOnlyWritesPrefixAndCallerOwnsCountIndexAndVectors() {
    D2PathResult.State s=state(); s.path.setNewDistance(0);
    D2PathRequest.State request=request(s,null,70);
    Effects effects=new Effects(D2PathIdaDispatchTest::wall) {
      @Override public int search(D2PathRequest.Search search) {
        assertEquals(0,search.type); assertEquals(0,search.maximumDistance);
        s.path.currentPointIndex=2; s.path.pathPointCount=3;
        int count=super.search(search);
        assertTrue(count>1); assertEquals(2,s.path.currentPointIndex); assertEquals(3,s.path.pathPointCount);
        assertEquals(123,s.directionVectorX); assertEquals(456,s.velocityVectorY);
        assertEquals(p(99,99),s.points[count]);
        s.path.currentPointIndex=0; return count;
      }
    };
    int count=D2PathRequest.compute(request,false,effects);
    assertTrue(count>1); assertEquals(count,s.path.pathPointCount); assertEquals(p(70,60),s.points[count-1]);
    assertNotEquals(0,s.velocityVectorY); assertEquals(Arrays.asList("removeOwner","search","addOwner"),effects.events);
  }

  @Test void straightIdaFlushFailureKeepsStaleSlotsAndNeverFallsBackToAStarOrToward() {
    D2PathResult.State s=state(); Effects effects=new Effects((x,y)->false);
    assertEquals(0,D2PathRequest.compute(request(s,null,70),false,effects));
    assertEquals(0,s.path.pathPointCount); assertEquals(p(99,99),s.points[0]); assertEquals(0,s.path.pathType);
    assertEquals(Arrays.asList("removeOwner","search","addOwner"),effects.events);
  }

  @Test void continuationKeepsTypeZeroWhenItsSinglePointFlushFails() {
    D2PathResult.State s=state(); s.path.setDistance(20);
    D2PathRequest.State request=request(s,null,70); Effects effects=new Effects((x,y)->false);
    List<Integer> types=new ArrayList<>();
    assertEquals(0,D2PathContinuation.resume(s.path,false,new D2PathContinuation.Effects() {
      public void refresh(D2PathContinuation.State path) {}
      public int search(D2PathContinuation.State path) {
        types.add(path.pathType);return D2PathRequest.compute(request,false,effects);
      }
    }));
    assertEquals(Collections.singletonList(0),types); assertEquals(0,s.path.pathType);
  }

  @Test void searchUsesSnapshotTypeRatherThanLiveTypeAndDoesNotApplyTypeTables() {
    D2PathResult.State s=state(); s.path.directionOffset=2;
    Effects effects=new Effects(D2PathIdaDispatchTest::wall) {
      @Override public int search(D2PathRequest.Search search) {
        s.path.pathType=16; return super.search(search);
      }
    };
    assertTrue(D2PathRequest.compute(request(s,null,70),false,effects)>1);
    assertEquals(16,s.path.pathType); assertEquals(2,s.path.directionOffset);
  }

  @Test void originalTargetRoomLookupGoesToIdaWrapperAndNotOnlyTheStartBounds() {
    D2PathResult.State s=state(); s.path.targetPoint=p(110,60);
    D2PathRequest.State request=request(s,null,150);
    D2PathRequest.Room target=new D2PathRequest.Room(new D2PathResult.Room(100,50,50,50),false);
    request.startRoom.nearby=Collections.singletonList(target);
    Effects effects=new Effects((x,y)->x==62 && y>=59 && y<=61) {
      @Override public int search(D2PathRequest.Search search) {
        assertSame(target,search.targetRoom);
        D2IDAStar.Bounds b=D2IDAStar.nativeRoomBounds(search.state.startRoom,search.targetRoom);
        assertEquals(50,b.minX); assertEquals(150,b.maxX); assertEquals(40,b.minY); assertEquals(110,b.maxY);
        return super.search(search);
      }
    };
    int count=D2PathRequest.compute(request,false,effects);
    assertTrue(count>1); assertEquals(p(110,60),s.points[count-1]);
  }

  @Test void rawCoordinateOverloadsRequireRoomsAndTypeSixteenRequiresOwnerSeed() {
    D2PathResult.State s=state();
    assertThrows(UnsupportedOperationException.class,()->D2PathDispatch.compute(s,p(60,60),p(70,60),0,77,0,(x,y)->false));
    assertThrows(UnsupportedOperationException.class,()->D2PathDispatch.compute(s,p(60,60),p(70,60),16,77,0,(x,y)->false));
    s.path.pathType=16; Effects effects=new Effects((x,y)->false);
    assertThrows(IllegalArgumentException.class,()->D2PathRequest.compute(request(s,null,70),false,effects));
    assertEquals(Arrays.asList("removeOwner","search","addOwner"),effects.events);
  }

  @Test void unsafeRoomProjectionRestoresOwnerThroughRequestFinallyWithoutInventingFailure() {
    D2PathResult.State s=state(); s.room=new D2PathResult.Room(50,50,230,180);
    Effects effects=new Effects((x,y)->false);
    assertThrows(IllegalArgumentException.class,()->D2PathRequest.compute(request(s,null,70),false,effects));
    assertEquals(Arrays.asList("removeOwner","search","addOwner"),effects.events);
    assertEquals(p(99,99),s.points[0]);
  }

  @Test void concreteGridEntryRestoresBothFootprintsAndNormalizesOnlyAfterRestoration() {
    concreteGrid(0,null);
  }

  @Test void concreteTypeSixteenGridRestoresBothFootprintsAndConsumesLiveOwnerSeed() {
    D2Seed seed=new D2Seed(1,0); concreteGrid(16,seed);
    assertNotEquals(1L,seed.getLSeed());
  }

  private static void concreteGrid(int type,D2Seed seed) {
    D2PathResult.State s=state(); s.path.pathFlags=0x800;s.path.pathType=type;
    D2ActiveRoom grid=new D2ActiveRoom(); D2DrlgCoords coords=new D2DrlgCoords();
    coords.setNSubtileX(50);coords.setNSubtileY(50);coords.setNSubtileWidth(50);coords.setNSubtileHeight(50);
    grid.setCoords(coords);D2CommonCollision.allocRoomCollisionGrid(grid);
    D2PathFootprint.Unit owner=D2PathFootprint.Unit.dynamic(D2PathUnits.Unit.dynamic(1,2,
        new D2PathUnits.DynamicPath(D2PathStep.toFp16Center(60),D2PathStep.toFp16Center(60),0)),grid,1,1,0x100);
    D2PathFootprint.Unit target=D2PathFootprint.Unit.dynamic(D2PathUnits.Unit.dynamic(1,2,
        new D2PathUnits.DynamicPath(D2PathStep.toFp16Center(70),D2PathStep.toFp16Center(60),0)),grid,1,1,0x100);
    s.path.targetUnit=D2PathUnits.observation(owner.coordinates,target.coordinates,0);
    D2PathFootprint.add(owner);D2PathFootprint.add(target);
    for(int y=59;y<=61;y++)D2CommonCollision.setMask(grid,62,y,1);
    D2PathRequest.State request=request(s,target.coordinates,70,seed);
    int count=D2PathRequest.compute(request,false,new D2PathDispatch.Effects(request,owner,target));
    assertTrue(count>1); assertEquals(p(70,60),s.points[count-1]);
    // A randomized route may start horizontally before its detour; require normalization,
    // not a nonzero Y component that is specific to the deterministic type0 first leg.
    assertNotEquals(456,s.velocityVectorY);assertNotEquals(123,s.directionVectorX);
    if(type==0)assertNotEquals(0,s.velocityVectorY);
    assertEquals(0x1100,D2CommonCollision.checkMask(grid,60,60,0xffff));
    assertEquals(0x1100,D2CommonCollision.checkMask(grid,70,60,0xffff));
  }

  @Test void dispatcherDoublesSearchToleranceWithoutChangingPersistentStepDistance() {
    D2PathResult.State s=state();
    D2PathUnits.Unit item=D2PathUnits.Unit.stationary(4,2,p(70,60));
    s.path.targetUnit=new D2PathContinuation.Target() {
      public int unitType(){return 4;} public int distanceToOwner(){return 100;}
      public D2Path.Point samplePoint(){return p(70,60);}
    };
    int steps=s.path.nStepNum();
    Effects effects=new Effects(D2PathIdaDispatchTest::wall) {
      @Override public int search(D2PathRequest.Search search) {
        assertEquals(2,search.tolerance);
        List<D2Path.Point> expected=D2IDAStar.findNativePath(search.start,search.target,
            D2IDAStar.nativeRoomBounds(search.state.startRoom,search.targetRoom),this.collision::blocked,70,4).path();
        int count=super.search(search);
        assertEquals(expected,Arrays.asList(Arrays.copyOf(s.points,count)));
        return count;
      }
    };
    assertTrue(D2PathRequest.compute(request(s,item,70),false,effects)>1);
    assertEquals(steps,s.path.nStepNum()); assertNotEquals(p(70,60),s.points[s.path.pathPointCount-1]);
  }

  @Test void typeSixteenSnapshotUsesLiveSeedAndOnlyWritesAcceptedPrefix() {
    D2PathResult.State s=state();s.path.pathType=16;s.path.directionOffset=2;
    D2Seed seed=new D2Seed(1,666);D2PathRequest.State request=request(s,null,70,seed);
    assertSame(seed,request.ownerSeed);
    Effects effects=new Effects(D2PathIdaDispatchTest::wall) {
      @Override public int search(D2PathRequest.Search search) {
        assertEquals(16,search.type);s.path.pathType=0;
        D2Seed copy=new D2Seed(seed.getNLowSeed(),seed.getNHighSeed());
        List<D2Path.Point> expected=D2IDAStar.findNativeStreamPath(search.start,search.target,
            D2IDAStar.nativeRoomBounds(search.state.startRoom,search.targetRoom),this.collision::blocked,70,0,copy).path();
        s.path.currentPointIndex=2;s.path.pathPointCount=3;
        int count=super.search(search);
        assertTrue(count>1);assertEquals(expected,Arrays.asList(Arrays.copyOf(s.points,count)));
        assertEquals(copy.getLSeed(),seed.getLSeed());assertEquals(p(99,99),s.points[count]);
        assertEquals(2,s.path.currentPointIndex);assertEquals(3,s.path.pathPointCount);
        assertEquals(123,s.directionVectorX);assertEquals(456,s.velocityVectorY);
        s.path.currentPointIndex=0;return count;
      }
    };
    assertTrue(D2PathRequest.compute(request,false,effects)>1);
    assertEquals(0,s.path.pathType);assertEquals(2,s.path.directionOffset);
    assertEquals(Arrays.asList("removeOwner","search","addOwner"),effects.events);
  }

  @Test void typeZeroSnapshotNeverConsumesSeedEvenWhenLiveTypeChangesToSixteen() {
    D2PathResult.State s=state();D2Seed seed=new D2Seed(1,666);long initial=seed.getLSeed();
    Effects effects=new Effects(D2PathIdaDispatchTest::wall) {
      @Override public int search(D2PathRequest.Search search){s.path.pathType=16;return super.search(search);}
    };
    assertTrue(D2PathRequest.compute(request(s,null,70,seed),false,effects)>1);
    assertEquals(initial,seed.getLSeed());
  }

  @Test void randomFailureRestoresOwnerAndDoesNotFallBackOrRewindSeed() {
    D2PathResult.State s=state();s.path.pathType=16;D2Seed seed=new D2Seed(1,0);
    Effects effects=new Effects((x,y)->true);
    assertEquals(0,D2PathRequest.compute(request(s,null,0,seed),false,effects));
    assertEquals(0x5a0cc847eea0f793L,seed.getLSeed());assertEquals(16,s.path.pathType);
    assertEquals(p(99,99),s.points[0]);assertEquals(0,s.path.pathPointCount);
    assertEquals(Arrays.asList("removeOwner","search","addOwner"),effects.events);
  }

  @Test void randomCallbackExceptionRestoresBothUnitsButNotEarlierRngConsumption() {
    D2PathResult.State s=state();s.path.pathType=16;s.path.pathFlags=0x800;
    D2PathUnits.Unit target=D2PathUnits.Unit.stationary(4,2,p(70,60));
    s.path.targetUnit=new D2PathContinuation.Target() {
      public int unitType(){return 4;}public int distanceToOwner(){return 100;}
      public D2Path.Point samplePoint(){return p(70,60);}
    };
    D2Seed seed=new D2Seed(1,0);int[] queries={0};
    Effects effects=new Effects((x,y)->{
      if(++queries[0]==2)throw new IllegalStateException("collision failed");return true;
    });
    assertThrows(IllegalStateException.class,()->D2PathRequest.compute(request(s,target,0,seed),false,effects));
    assertEquals(0x6ac690c5L,seed.getLSeed());
    assertEquals(Arrays.asList("removeOwner","removeTarget","search","addTarget","addOwner"),effects.events);
  }

  @Test void typeSixteenRetainsMissileGateAndRejectsUnsafeRoomsBeforeRng() {
    D2PathResult.State s=state();s.path.pathType=16;s.path.pathFlags=0x40000;
    D2Seed seed=new D2Seed(1,0);Effects effects=new Effects((x,y)->false);
    assertThrows(IllegalArgumentException.class,()->D2PathRequest.compute(request(s,null,70,seed),false,effects));
    assertTrue(effects.events.isEmpty());
    s.path.pathFlags=0;s.room=new D2PathResult.Room(50,50,230,180);
    assertThrows(IllegalArgumentException.class,()->D2PathRequest.compute(request(s,null,70,seed),false,effects));
    assertEquals(1L,seed.getLSeed());assertEquals(Arrays.asList("removeOwner","search","addOwner"),effects.events);
  }

  @Test void typeSixteenDoublesTemporaryToleranceWithoutChangingPersistentStep() {
    D2PathResult.State s=state();s.path.pathType=16;int step=s.path.nStepNum();
    D2PathUnits.Unit target=D2PathUnits.Unit.stationary(4,2,p(70,60));
    s.path.targetUnit=new D2PathContinuation.Target() {
      public int unitType(){return 4;}public int distanceToOwner(){return 100;}
      public D2Path.Point samplePoint(){return p(70,60);}
    };
    D2Seed seed=new D2Seed(1,0);
    Effects effects=new Effects(D2PathIdaDispatchTest::wall) {
      @Override public int search(D2PathRequest.Search search) {
        assertEquals(2,search.tolerance);D2Seed copy=new D2Seed(seed.getNLowSeed(),seed.getNHighSeed());
        List<D2Path.Point> expected=D2IDAStar.findNativeStreamPath(search.start,search.target,
            D2IDAStar.nativeRoomBounds(search.state.startRoom,search.targetRoom),this.collision::blocked,70,4,copy).path();
        int count=super.search(search);assertEquals(copy.getLSeed(),seed.getLSeed());
        assertEquals(expected,Arrays.asList(Arrays.copyOf(s.points,count)));return count;
      }
    };
    assertTrue(D2PathRequest.compute(request(s,target,70,seed),false,effects)>1);
    assertEquals(step,s.path.nStepNum());assertNotEquals(p(70,60),s.points[s.path.pathPointCount-1]);
  }
}
