package com.d2moo.common.path;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D2PathLeapBackupTest {
  private static D2Path.Point p(int x,int y) { return new D2Path.Point(x,y); }
  private static D2PathResult.State state() {
    D2PathContinuation.State path=new D2PathContinuation.State(1);
    path.currentPoint=p(10,10); path.targetPoint=p(20,10); path.pathType=9;
    D2PathResult.State s=new D2PathResult.State(path); s.velocity=256;
    Arrays.fill(s.points,p(99,99)); return s;
  }
  private static int run(D2PathResult.State s,int type,int budget,D2Toward.Collision c) {
    return D2PathDispatch.compute(s,s.path.currentPoint,p(20,10),type,budget,0,c);
  }
  @Test void leapClipsAndWritesOneWithoutChangingTarget() {
    D2PathResult.State s=state(); s.path.currentPointIndex=2;
    assertEquals(1,run(s,9,0,(x,y)->x==14));
    assertEquals(p(13,10),s.points[0]); assertEquals(1,s.path.pathPointCount);
    assertEquals(0,s.path.currentPointIndex); assertEquals(p(20,10),s.path.targetPoint);
    assertEquals(p(99,99),s.points[1]);
  }
  @Test void leapZeroSpeedRetainsOriginalSlotAndCount() {
    D2PathResult.State s=state(); s.velocity=0; s.path.pathPointCount=3;
    assertEquals(0,run(s,9,3,(x,y)->{fail();return false;}));
    assertEquals(p(20,10),s.points[0]); assertEquals(3,s.path.pathPointCount);
  }
  @Test void leapImmediateBlockRetainsCandidate() {
    D2PathResult.State s=state(); assertEquals(0,run(s,9,3,(x,y)->true));
    assertEquals(p(20,10),s.points[0]); assertEquals(4096,s.directionVectorX);
  }
  @Test void leapAssertionOccursAfterSlotAndIndexWrite() {
    D2PathResult.State s=state(); s.path.pathType=4; s.path.currentPointIndex=4;
    assertThrows(IllegalStateException.class,()->run(s,9,0,(x,y)->false));
    assertEquals(0,s.path.currentPointIndex); assertEquals(p(20,10),s.points[0]);
  }
  @Test void backupOpenPassesRestartAtStartAndAppendSourcePoints() {
    D2PathResult.State s=state();
    assertEquals(4,run(s,12,3,(x,y)->false));
    assertEquals(Arrays.asList(p(10,10),p(7,10),p(10,10),p(10,7)),Arrays.asList(Arrays.copyOf(s.points,4)));
    assertEquals(-4,s.path.directionOffset); assertEquals(4,s.path.pathPointCount);
    assertEquals(256,s.velocity); assertEquals(0,s.directionVectorX);
  }
  @Test void backupFallsBackToPositiveTwoOnlyWhenSecondAddsNothing() {
    D2PathResult.State s=state(); List<Integer> offsets=new ArrayList<>();
    assertEquals(4,run(s,12,3,(x,y)->{
      int offset=s.path.directionOffset; if (!offsets.contains(offset)) offsets.add(offset);
      return offset==-2;
    }));
    assertEquals(Arrays.asList(-4,-2,2),offsets);
    assertEquals(p(10,13),s.points[3]); assertEquals(-4,s.path.directionOffset);
  }
  @Test void backupFirstFailureDoesNotTrySidePasses() {
    D2PathResult.State s=state();
    assertEquals(0,run(s,12,3,(x,y)->{assertEquals(-4,s.path.directionOffset);return true;}));
    assertEquals(p(99,99),s.points[0]); assertEquals(-4,s.path.directionOffset);
  }
  @Test void backupDoesNotClearExistingCountEvenWithZeroBudget() {
    D2PathResult.State s=state(); s.path.pathPointCount=2; s.path.currentPointIndex=1;
    assertEquals(2,run(s,12,0,(x,y)->{fail();return false;}));
    assertEquals(2,s.path.pathPointCount); assertEquals(0,s.path.currentPointIndex);
    assertEquals(p(99,99),s.points[0]); assertEquals(-4,s.path.directionOffset);
  }
  @Test void backupOneStepOmitsFinalNewlyTurnedPoint() {
    D2PathResult.State s=state(); assertEquals(2,run(s,12,1,(x,y)->false));
    assertEquals(p(10,10),s.points[0]); assertEquals(p(10,10),s.points[1]);
  }
  @Test void backupExceptionRetainsPrefixButDoesNotCommitLocalCountOrResetOffset() {
    D2PathResult.State s=state();
    assertThrows(IllegalStateException.class,()->run(s,12,3,(x,y)->{
      if(s.path.directionOffset==-2 && y==8) throw new IllegalStateException(); return false;
    }));
    assertEquals(2,s.path.pathPointCount); assertEquals(p(10,10),s.points[2]);
    assertEquals(-2,s.path.directionOffset);
  }
  @Test void backupCapacityRefusesNativeBufferOverflow() {
    D2PathResult.State s=state(); s.path.pathPointCount=78;
    assertThrows(IllegalArgumentException.class,()->run(s,12,3,(x,y)->false));
    assertEquals(78,s.path.pathPointCount);
  }
  @Test void backupNarrowsBoundaryCollisionCandidatesLikeTheDll() {
    D2PathResult.State s=state(); s.path.currentPoint=p(0,10);
    s.precisionX=D2PathStep.toFp16Center(0);
    List<D2Path.Point> q=new ArrayList<>();
    run(s,12,1,(x,y)->{q.add(p(x,y));return true;});
    assertEquals(65535,q.get(0).x);
  }
  @Test void leapExactCenterZerosVectorsWithoutInventingCount() {
    D2PathResult.State s=state();
    assertEquals(0,D2PathDispatch.compute(s,p(10,10),p(10,10),9,0,0,(x,y)->{fail();return false;}));
    assertEquals(0,s.velocity); assertEquals(0,s.path.pathPointCount);
  }
  @Test void backupAppendsAfterExistingPrefixInsteadOfReplacingIt() {
    D2PathResult.State s=state(); s.path.pathPointCount=1;
    assertEquals(5,run(s,12,3,(x,y)->false));
    assertEquals(p(99,99),s.points[0]); assertEquals(p(10,10),s.points[1]);
    assertEquals(p(10,10),s.points[3]);
  }
  @Test void backupPassesMatchOffsetKernelAcrossFixedRandomCollisionMaps() {
    Random random=new Random(22);
    for(int sample=0;sample<200;sample++) {
      Set<D2Path.Point> blocked=new HashSet<>();
      for(int x=3;x<24;x++) for(int y=3;y<18;y++) if(random.nextInt(4)==0) blocked.add(p(x,y));
      int budget=1+random.nextInt(6);
      List<D2Path.Point> expected=new ArrayList<>(); List<D2Path.Point> queries=new ArrayList<>();
      D2Toward.Collision c=(x,y)->{queries.add(p(x,y));return blocked.contains(p(x,y));};
      expected.addAll(D2Toward.compute(p(10,10),p(20,10),budget,0,-4,c).points());
      if(!expected.isEmpty()) {
        List<D2Path.Point> second=D2Toward.compute(p(10,10),p(20,10),budget,0,-2,c).points();
        expected.addAll(second);
        if(second.isEmpty()) expected.addAll(D2Toward.compute(p(10,10),p(20,10),budget,0,2,c).points());
      }
      D2PathResult.State s=state(); List<D2Path.Point> actualQueries=new ArrayList<>();
      int count=run(s,12,budget,(x,y)->{actualQueries.add(p(x,y));return blocked.contains(p(x,y));});
      assertEquals(expected,Arrays.asList(Arrays.copyOf(s.points,count)));
      assertEquals(queries,actualQueries); assertEquals(-4,s.path.directionOffset);
    }
  }
}
