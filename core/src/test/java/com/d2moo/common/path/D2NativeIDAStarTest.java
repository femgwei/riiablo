package com.d2moo.common.path;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D2NativeIDAStarTest {
  private static D2Path.Point p(int x,int y) { return new D2Path.Point(x,y); }
  private static D2IDAStar.Bounds box() { return new D2IDAStar.Bounds(10,10,30,30); }
  private static D2PathRequest.Room room(int x,int y,int width,int height) {
    return new D2PathRequest.Room(new D2PathResult.Room(x,y,width,height),false);
  }
  private static void bounds(D2IDAStar.Bounds b,int x,int y,int right,int bottom) {
    assertEquals(x,b.minX); assertEquals(y,b.minY); assertEquals(right,b.maxX); assertEquals(bottom,b.maxY);
  }

  @Test void straightTerminalStillRejectsSingleCompressedPoint() {
    D2IDAStar.Result r = D2IDAStar.findNativePath(p(15,15),p(20,15),box(),(x,y)->false,70,0);
    assertTrue(r.reachedTarget()); assertTrue(r.nativePathFound()); assertTrue(r.path().isEmpty());
    assertEquals(1,r.cutoffIterations()); assertEquals(10,r.finalCutoff());
  }

  @Test void largerContourAcceptsEqualCachedGAndNeverRequeriesEvenBlockedCells() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2IDAStar.Result r = D2IDAStar.findNativePath(p(15,15),p(19,15),box(),(x,y)->{
      queries.add(p(x,y)); return x == 16 && y >= 14 && y <= 16;
    },70,0);
    assertTrue(r.reachedTarget()); assertTrue(r.path().size() > 1); assertTrue(r.cutoffIterations() > 1);
    assertEquals(new HashSet<>(queries).size(),queries.size(),"cache survives all cutoff contours");
    assertEquals(0,(r.finalCutoff()-8)%5);
  }

  @Test void typeZeroUsesMaxOfHeuristicAndByteCeilingAndTriesInitialContourUnconditionally() {
    D2AStar.Collision wall = (x,y)->x == 16 && y >= 14 && y <= 16;
    D2IDAStar.Result small = D2IDAStar.findNativePath(p(15,15),p(19,15),box(),wall,0,0);
    D2IDAStar.Result ceiling = D2IDAStar.findNativePath(p(15,15),p(19,15),box(),wall,13,0);
    assertEquals(1,small.cutoffIterations()); assertFalse(small.nativePathFound());
    assertEquals(1,ceiling.cutoffIterations(),"next cutoff equals exclusive ceiling");
    assertTrue(D2IDAStar.findNativePath(p(15,15),p(19,15),box(),wall,14,0).reachedTarget());
  }

  @Test void nativeThresholdIsStrictAndCheckedAfterEnteringAChild() {
    D2IDAStar.Result r = D2IDAStar.findNativePath(p(15,15),p(20,15),box(),(x,y)->false,70,4);
    assertEquals(p(19,15),r.bestPoint()); assertFalse(r.reachedTarget());
    assertEquals(p(20,15),D2IDAStar.findNativePath(p(15,15),p(20,15),box(),(x,y)->false,70,2).bestPoint());
  }

  @Test void inclusiveBoundaryAllowsEnteringOneOutsideCellBeforeTerminalCheck() {
    D2IDAStar.Result r = D2IDAStar.findNativePath(p(15,15),p(20,15),new D2IDAStar.Bounds(14,14,17,17),
        (x,y)->y != 15,70,0);
    assertEquals(p(18,15),r.bestPoint()); assertTrue(r.nativePathFound()); assertFalse(r.reachedTarget());
  }

  @Test void startOutsideBoundsIsANativeTerminalNotAGeneralApiValidationError() {
    D2IDAStar.Result r = D2IDAStar.findNativePath(p(9,15),p(20,15),box(),(x,y)->{
      fail("outside root is returned without neighbors"); return true;
    },70,0);
    assertTrue(r.nativePathFound()); assertTrue(r.path().isEmpty());
    assertThrows(IllegalArgumentException.class,()->D2IDAStar.findPath(p(9,15),p(20,15),box(),(x,y)->true));
  }

  @Test void nodeStorageAndSignedWordScoresAreNotGeneralSaturatedArithmetic() {
    D2IDAStar.Result longLine = D2IDAStar.findNativePath(p(100,100),p(2100,100),
        new D2IDAStar.Bounds(100,99,3100,100),(x,y)->y != 100,0,0);
    assertEquals(900,longLine.allocatedNodes()); assertFalse(longLine.nativePathFound());
    D2IDAStar.Result overflow = D2IDAStar.findNativePath(p(15,15),p(20015,15),box(),(x,y)->true,0,0);
    assertEquals((short)40000,overflow.finalCutoff()-5*overflow.cutoffIterations());
    assertTrue(overflow.cutoffIterations() > 1);
  }

  @Test void signedChildHeuristicCanTriggerThresholdBeforeReachingDistantTarget() {
    D2IDAStar.Result r=D2IDAStar.findNativePath(p(15,15),p(20015,15),box(),(x,y)->false,70,0);
    assertEquals(p(16,15),r.bestPoint()); assertTrue(r.nativePathFound()); assertFalse(r.reachedTarget());
    assertTrue(r.path().isEmpty(),"one compressed point is still rejected");
  }

  @Test void rejectsUnsafePaddedCacheAreaEvenWhenNativeUnpaddedAssertionWouldPass() {
    D2IDAStar.Bounds b = new D2IDAStar.Bounds(100,100,350,300); // unpadded exactly 50000
    assertThrows(IllegalArgumentException.class,()->D2IDAStar.findNativePath(p(110,110),p(120,110),b,(x,y)->true,70,0));
    assertThrows(IllegalArgumentException.class,()->D2IDAStar.findNativePath(p(-1,15),p(20,15),box(),(x,y)->true,70,0));
    assertThrows(IllegalArgumentException.class,()->D2IDAStar.findNativePath(p(15,15),p(20,15),box(),(x,y)->true,256,0));
    assertThrows(IllegalArgumentException.class,()->D2IDAStar.findNativePath(p(15,15),p(20,15),box(),(x,y)->true,70,256));
  }

  @Test void wordWrappedNeighborOutsideCacheMemoryIsExplicitlyRejected() {
    assertThrows(IllegalArgumentException.class,()->D2IDAStar.findNativePath(p(0,15),p(0,20),
        new D2IDAStar.Bounds(0,10,10,30),(x,y)->x == 0,70,0));
  }

  @Test void roomWrapperExpandsUnchangedAxesByTenIncludingNullAndSameRoomTargets() {
    D2PathRequest.Room a = room(100,200,40,30);
    bounds(D2IDAStar.nativeRoomBounds(a,null),90,190,150,240);
    bounds(D2IDAStar.nativeRoomBounds(a,a),90,190,150,240);
    bounds(D2IDAStar.nativeRoomBounds(a,room(140,200,40,30)),100,190,180,240);
    bounds(D2IDAStar.nativeRoomBounds(a,room(100,230,40,30)),90,200,150,260);
  }

  @Test void roomWrapperUsesDirectionalEndRatherThanMaxUnionForContainedRooms() {
    D2PathRequest.Room a = room(100,200,40,30);
    bounds(D2IDAStar.nativeRoomBounds(a,room(110,205,10,10)),100,200,120,215);
    bounds(D2IDAStar.nativeRoomBounds(a,room(80,180,10,10)),80,180,140,230);
    // Different native room identity, same origin but different dimensions: do not use same-room shortcut.
    bounds(D2IDAStar.nativeRoomBounds(a,room(100,200,10,10)),100,200,110,210);
  }

  @Test void invalidRoomProjectionDoesNotPretendNativeWrapOrMemoryCorruptionIsSafe() {
    assertThrows(IllegalArgumentException.class,()->D2IDAStar.nativeRoomBounds(null,null));
    assertThrows(IllegalArgumentException.class,()->D2IDAStar.nativeRoomBounds(room(0,0,40,30),null));
    assertThrows(IllegalArgumentException.class,()->D2IDAStar.nativeRoomBounds(room(65500,200,40,30),null));
    assertThrows(IllegalArgumentException.class,()->D2IDAStar.nativeRoomBounds(room(100,100,230,180),null));
  }

  @Test void installedDllNeighborTablesMatchTheActualJavaData() throws Exception {
    ByteBuffer dll = D2PathUnitsTest.originalDll("D2Common.dll",
        "B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB");
    for (String name : new String[] {"COORD_OFFSETS","NEIGHBOR_SEQUENCES"}) {
      Field field = D2IDAStar.class.getDeclaredField(name); field.setAccessible(true);
      int[][] table = (int[][])field.get(null);
      int offset = D2PathUnitsTest.fileOffset(dll,name.equals("COORD_OFFSETS") ? 0x6fdd1d60L : 0x6fdd1be0L);
      for (int[] row : table) for (int value : row) { assertEquals(dll.getInt(offset),value); offset += 4; }
    }
  }

  /** Independent recursive contour oracle for the safe small domain. Not a DLL execution oracle.
   * Its loop shape deliberately does not reuse production's mutable node/backtrack implementation. */
  private static final class Oracle {
    static final int[][] OFFSET = {{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
    static final int[][] SEQ = {{0,1,6,3,4,5,2,7},{0,1,6,3,1,3,6,1},{0,1,1,1,1,1,2,7},
        {1,1,1,1,1,3,6,1},{6,4,3,6,1,3,2,7},{7,7,7,7,7,5,2,7},{0,7,7,7,7,5,2,7},{0,7,2,5,7,5,2,7}};
    final Map<D2Path.Point,Integer> cache = new HashMap<>();
    final List<D2Path.Point> queries = new ArrayList<>();
    final D2IDAStar.Bounds bounds; final D2Path.Point target; final D2AStar.Collision collision;
    int visits,cutoff,threshold,iterations,nodes,maxNodes; boolean exhausted;
    Oracle(D2IDAStar.Bounds bounds,D2Path.Point target,D2AStar.Collision collision,int threshold) {
      this.bounds=bounds;this.target=target;this.collision=collision;this.threshold=threshold;
    }
    List<D2Path.Point> run(D2Path.Point start,int ceiling) {
      cutoff=(short)D2Path.aStarHeuristic(start.x,start.y,target.x,target.y);
      ceiling=Math.max(cutoff,ceiling);
      do {
        visits=0;nodes=1;exhausted=false;iterations++;
        List<D2Path.Point> chain = visit(start,0,0,0,true);
        maxNodes=Math.max(maxNodes,nodes);
        if (chain != null) return chain;
        if (nodes == 900) break;
        cutoff+=5;
      } while(cutoff<ceiling);
      return null;
    }
    List<D2Path.Point> visit(D2Path.Point at,int g,int parentDirection,int depth,boolean root) {
      if(at.equals(target)) return new ArrayList<>(Arrays.asList(at));
      int primary=D2PathMisc.primaryDirection(at,target);
      int row=root?0:(primary-parentDirection)&7;
      int dir=root?primary:(parentDirection+SEQ[row][0])&7;
      for(int i=0;i<(root?8:5);i++) {
        if(i>0) dir=(dir+SEQ[row][i])&7;
        if(++visits>10000) { exhausted=true; return null; }
        if(!bounds.contains(at.x,at.y)) return new ArrayList<>(Arrays.asList(at));
        D2Path.Point next=p((at.x+OFFSET[dir][0])&65535,(at.y+OFFSET[dir][1])&65535);
        int old=cache.getOrDefault(next,0);
        if(old==0) {
          queries.add(next);
          if(collision.isBlocked(next.x,next.y)) { cache.put(next,1); continue; }
        }
        int ng=(short)(g+(at.x==next.x || at.y==next.y?2:3));
        if(old!=0 && Integer.compareUnsigned(old,ng)<0) continue;
        cache.put(next,ng);
        int h=(short)D2Path.aStarHeuristic(next.x,next.y,target.x,target.y);
        if((short)(ng+h)>cutoff) continue;
        if(depth+1>=nodes) {
          if(nodes==900) { exhausted=true; return null; }
          nodes++;
        }
        List<D2Path.Point> tail=h<threshold ? new ArrayList<>(Arrays.asList(next)) : visit(next,ng,dir,depth+1,false);
        if(tail!=null) { tail.add(0,at); return tail; }
        if(exhausted) return null;
      }
      return null;
    }
  }

  @Test void independentRecursiveOracleMatchesTwoHundredContoursIncludingCacheAndChildReuse() {
    Random random=new Random(0x110f19);
    for(int i=0;i<200;i++) {
      D2Path.Point start=p(15,15), target=p(12+random.nextInt(12),12+random.nextInt(12));
      int salt=random.nextInt(), ceiling=10+random.nextInt(80), threshold=random.nextInt(5);
      D2AStar.Collision collision=(x,y)->((x*73856093 ^ y*19349663 ^ salt)&7)<2;
      Oracle oracle=new Oracle(box(),target,collision,threshold);
      List<D2Path.Point> chain=oracle.run(start,ceiling), queries=new ArrayList<>();
      D2IDAStar.Result r=D2IDAStar.findNativePath(start,target,box(),(x,y)->{queries.add(p(x,y));return collision.isBlocked(x,y);},ceiling,threshold);
      assertEquals(oracle.queries,queries,"queries case "+i);
      assertEquals(chain!=null,r.nativePathFound(),"terminal case "+i);
      assertEquals(oracle.iterations,r.cutoffIterations()); assertEquals(oracle.cutoff,r.finalCutoff());
      assertEquals(oracle.nodes,r.allocatedNodes()); assertEquals(oracle.maxNodes,r.maximumAllocatedNodes());
      List<D2Path.Point> expected=chain==null?new ArrayList<>():D2Path.compressAStarPath(chain);
      if(expected.size()==1) expected.clear();
      assertEquals(expected,r.path(),"flush case "+i);
      assertEquals(chain==null?start:chain.get(chain.size()-1),r.bestPoint());
    }
  }
}
