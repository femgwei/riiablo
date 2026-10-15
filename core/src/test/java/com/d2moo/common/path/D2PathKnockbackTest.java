package com.d2moo.common.path;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D2PathKnockbackTest {
  private static D2Path.Point p(int x,int y) { return new D2Path.Point(x,y); }
  private static D2PathResult.State state() {
    D2PathContinuation.State path = new D2PathContinuation.State(1);
    path.currentPoint=p(10,10); path.targetPoint=p(40,40); path.pathType=8;
    path.pathPointCount=0; path.currentPointIndex=0; path.setDist(9);
    D2PathResult.State s=new D2PathResult.State(path); s.velocity=256;
    s.directionVectorX=123; s.points[1]=p(99,99); return s;
  }
  private static int run(D2PathResult.State s,int type,D2Path.Point target,D2Toward.Collision c) {
    return D2PathDispatch.compute(s,s.path.currentPoint,target,type,1,0,c);
  }
  private static D2PathContinuation.Target target(int x,int y) {
    return D2PathUnits.observation(D2PathUnits.Unit.dynamic(1,1,null),
        D2PathUnits.Unit.stationary(2,1,p(x,y),0),7);
  }
  @Test void serverUsesRawCoordinatesLiveDistAndTruncatingDivision() {
    D2PathResult.State s=state(); s.path.targetUnit=target(7,12);
    s.path.currentPointIndex=2; s.velocity=0;
    assertEquals(1,run(s,8,p(50,50),(x,y)->false));
    assertEquals(p(15,7),s.points[0]); assertEquals(p(15,7),s.path.targetPoint);
    assertNull(s.path.targetUnit); assertEquals(2,s.path.currentPointIndex);
    assertEquals(1,s.path.pathPointCount); assertEquals(123,s.directionVectorX);
    assertEquals(p(99,99),s.points[1]);
  }
  @Test void serverClipsBeforeClearingTargetEvenWithoutVelocity() {
    D2PathResult.State s=state(); s.velocity=0; s.path.targetUnit=target(5,10);
    assertEquals(1,run(s,8,p(50,50),(x,y)->x==13));
    assertEquals(p(12,10),s.points[0]); assertEquals(p(12,10),s.path.targetPoint);
    assertNull(s.path.targetUnit);
  }
  @Test void serverBlockedAtFirstStepRetainsCandidateAndCountButClearsIdentity() {
    D2PathResult.State s=state(); s.path.targetUnit=target(5,10); s.path.pathPointCount=3;
    assertEquals(0,run(s,8,p(20,10),(x,y)->true));
    assertEquals(p(15,10),s.points[0]); assertEquals(p(10,10),s.path.targetPoint);
    assertEquals(3,s.path.pathPointCount); assertNull(s.path.targetUnit);
  }
  @Test void serverCoincidentUnitDoesNotUseSearchTarget() {
    D2PathResult.State s=state(); s.path.targetUnit=target(10,10);
    assertEquals(0,run(s,8,p(20,10),(x,y)->{fail();return false;}));
    assertEquals(p(10,10),s.points[0]); assertNull(s.path.targetUnit);
  }
  @Test void serverCoordinateTargetBypassesClientZeroAxisAssertion() {
    D2PathResult.State s=state();
    assertEquals(1,run(s,8,p(0,10),(x,y)->false));
    assertEquals(p(0,10),s.path.targetPoint);
  }
  @Test void clientNormalizesThenClipsWithoutWritingCountOrTarget() {
    D2PathResult.State s=state(); s.path.pathType=11; s.path.currentPointIndex=2;
    D2PathContinuation.Target t=target(5,10); s.path.targetUnit=t;
    assertEquals(1,run(s,11,p(20,10),(x,y)->{
      assertEquals(0,s.path.currentPointIndex); assertEquals(4096,s.directionVectorX); return x==13;
    }));
    assertEquals(p(12,10),s.points[0]); assertEquals(0,s.path.pathPointCount);
    assertSame(t,s.path.targetUnit); assertEquals(p(40,40),s.path.targetPoint);
  }
  @Test void clientZeroVelocitySkipsQueriesAndStillReturnsOne() {
    D2PathResult.State s=state(); s.velocity=0;
    assertEquals(1,run(s,11,p(20,10),(x,y)->{fail();return false;}));
    assertEquals(p(10,10),s.points[0]); assertEquals(123,s.directionVectorX);
  }
  @Test void clientExactCenterZerosVelocityAndReturnsOne() {
    D2PathResult.State s=state();
    assertEquals(1,run(s,11,p(10,10),(x,y)->{fail();return false;}));
    assertEquals(0,s.velocity); assertEquals(0,s.directionVectorX);
  }
  @Test void clientAssertionRetainsSlotAndIndexWrites() {
    D2PathResult.State s=state(); s.path.currentPointIndex=2;
    assertThrows(IllegalStateException.class,()->run(s,11,p(0,10),(x,y)->false));
    assertEquals(0,s.path.currentPointIndex); assertEquals(p(0,10),s.points[0]);
  }
  @Test void serverExceptionRetainsCandidateAndUnit() {
    D2PathResult.State s=state(); D2PathContinuation.Target t=target(5,10); s.path.targetUnit=t;
    assertThrows(IllegalStateException.class,()->run(s,8,p(20,10),(x,y)->{throw new IllegalStateException();}));
    assertSame(t,s.path.targetUnit); assertEquals(p(15,10),s.points[0]);
  }
  @Test void serverUsesUnsignedDistAndNarrowsEndpointBeforeRay() {
    D2PathResult.State s=state(); s.path.setDist(255); s.path.targetUnit=target(11,10);
    assertEquals(0,run(s,8,p(20,10),(x,y)->x==11));
    // 10-128 narrows to 65418, hence ray travels in positive X and immediately blocks.
    assertEquals(p(65418,10),s.points[0]);
    assertEquals(p(10,10),s.path.targetPoint);
  }
  @Test void missingRawObservationIsRejectedInsteadOfUsingLeadSample() {
    D2PathResult.State s=state(); s.path.targetUnit=new D2PathContinuation.Target() {
      public int unitType(){return 1;} public int distanceToOwner(){return 1;}
      public D2Path.Point samplePoint(){fail("must not sample lead");return p(1,1);}
    };
    assertThrows(UnsupportedOperationException.class,()->run(s,8,p(20,10),(x,y)->false));
  }
}
