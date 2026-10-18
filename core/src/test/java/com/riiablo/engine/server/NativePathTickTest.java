package com.riiablo.engine.server;

import com.d2moo.common.path.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePathTickTest {
  static class Effects implements D2PathTick.Effects {
    boolean blocked;
    int searches, refreshes, recaches;
    public boolean tryMove(int x,int y,int nx,int ny,boolean unchecked) { return unchecked || !blocked; }
    public int search(D2PathContinuation.State path) { searches++; return 0; }
    public void refresh(D2PathContinuation.State path) { refreshes++; }
    public void recache(D2PathResult.State state) { recaches++; }
  }
  static D2PathTick.State state(int type) {
    D2PathContinuation.State p=new D2PathContinuation.State(1);
    p.currentPoint=new D2Path.Point(20,20);
    p.targetPoint=p.finalTargetPoint=new D2Path.Point(25,20);
    p.setNewDistance(14); p.setDistance(20);
    D2PathResult.State r=new D2PathResult.State(p);
    D2PathTypes.setType(r,type);
    r.velocity=4096;
    r.storePoints(java.util.Collections.singletonList(p.targetPoint));
    D2PathResult.finishSearch(r);
    return new D2PathTick.State(r);
  }
  @Test void distanceCountsAttemptedTicksNotCrossedCellsAndIncludesRejectedMove() {
    D2PathTick.State t=state(2); Effects e=new Effects();
    assertTrue(D2PathTick.advance(t,1024,e)); // one subtile, not one counter per substep
    assertEquals(13,t.result.path.nDist());
    e.blocked=true;
    assertFalse(D2PathTick.advance(t,1024,e));
    assertEquals(12,t.result.path.nDist());
    assertTrue(t.blocked);
    assertEquals(0,t.result.path.pathPointCount);
    assertEquals(D2PathStep.toFp16Center(21),t.result.precisionX);
  }
  @Test void knockbackNeverConsumesMovementAttemptDistance() {
    D2PathTick.State t=state(8); Effects e=new Effects();
    assertTrue(D2PathTick.advance(t,1024,e));
    assertEquals(14,t.result.path.nDist());
  }
  @Test void exhaustedMonsterBudgetStopsBeforeAnyNewSearch() {
    D2PathTick.State t=state(2); Effects e=new Effects();
    t.result.path.currentPointIndex=t.result.path.pathPointCount;
    t.result.path.setDistance(0);
    assertFalse(D2PathTick.advance(t,1024,e));
    assertEquals(0,e.searches);
    assertEquals(0,e.refreshes);
  }
  @Test void exhaustedCircleDoesNotResume() {
    D2PathTick.State t=state(5); Effects e=new Effects();
    t.result.path.currentPointIndex=t.result.path.pathPointCount;
    assertFalse(D2PathTick.advance(t,1024,e));
    assertEquals(0,e.searches);
  }
  @Test void accelerationChangesVelocityOnlyEveryFifthEligibleTick() {
    D2PathTick.State t=state(2); Effects e=new Effects();
    t.result.velocity=256;
    t.acceleration=32; t.maximumVelocity=300;
    for(int i=0;i<4;i++) assertTrue(D2PathTick.advance(t,1024,e));
    assertEquals(256,t.result.velocity);
    assertTrue(D2PathTick.advance(t,1024,e));
    assertEquals(288,t.result.velocity);
    for(int i=0;i<5;i++) assertTrue(D2PathTick.advance(t,1024,e));
    assertEquals(300,t.result.velocity);
    assertEquals(0,t.acceleration);
  }

  @Test void uncheckedPathFlagBypassesCollisionWithoutChangingItsType() {
    D2PathTick.State t=state(2); Effects e=new Effects(); e.blocked=true;
    t.result.path.pathFlags |= 4;
    assertTrue(D2PathTick.advance(t,1024,e));
    assertFalse(t.blocked);
    assertEquals(2,t.result.path.pathType);
    assertEquals(8,t.result.path.pathFlags & 8);
  }

  @Test void partialCrossingThenRejectionDoesNotPublishSuccessfulStepFlag() {
    D2PathTick.State t=state(2);
    t.result.velocity=8192;
    Effects e=new Effects() {
      public boolean tryMove(int x,int y,int nx,int ny,boolean unchecked) { return nx<22; }
    };
    assertFalse(D2PathTick.advance(t,1024,e));
    assertEquals(1,t.crossedCells);
    assertTrue(t.blocked);
    assertEquals(0,t.result.path.pathFlags & 8);
    assertEquals(13,t.result.path.nDist());
  }
}
