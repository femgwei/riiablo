package com.d2moo.common.path;

import com.d2moo.common.drlg.D2Seed;
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

class D2NativeIDAStreamTest {
  private static D2Path.Point p(int x,int y) { return new D2Path.Point(x,y); }
  private static D2IDAStar.Bounds box() { return new D2IDAStar.Bounds(10,10,30,30); }
  private static D2IDAStar.Result run(D2Path.Point target,D2AStar.Collision collision,int ceiling,int threshold,D2Seed seed) {
    return D2IDAStar.findNativeStreamPath(p(15,15),target,box(),collision,ceiling,threshold,seed);
  }

  @Test void blockedRootGoldenQueriesAndSevenRollsIncludeCachedRejections() {
    D2Seed seed=new D2Seed(1,0); List<D2Path.Point> queries=new ArrayList<>();
    D2IDAStar.Result r=run(p(20,15),(x,y)->{queries.add(p(x,y));return true;},0,0,seed);
    // Directions: 0,7,3,0,3,2,4,5. Duplicates hit the persistent blocked cache.
    assertEquals(Arrays.asList(p(16,15),p(16,14),p(14,16),p(15,16),p(14,15),p(14,14)),queries);
    assertEquals(0x5a0cc847eea0f793L,seed.getLSeed());
    assertFalse(r.nativePathFound()); assertEquals(1,r.allocatedNodes());
    assertEquals(1,r.cutoffIterations()); assertEquals(20,r.finalCutoff());
  }

  @Test void rootSetupAndRootTerminalNeverConsumeSeed() {
    D2Seed seed=new D2Seed(-1,-2); long initial=seed.getLSeed();
    D2AStar.Collision never=(x,y)->{fail("root terminal has no collision query");return false;};
    assertTrue(run(p(15,15),never,70,0,seed).nativePathFound());
    assertTrue(D2IDAStar.findNativeStreamPath(p(9,15),p(20,15),box(),never,70,0,seed).nativePathFound());
    assertEquals(initial,seed.getLSeed());
  }

  @Test void childDirectionRollPrecedesThresholdAndRejectedSinglePointFlush() {
    D2Seed seed=new D2Seed(1,0);
    D2IDAStar.Result r=run(p(20,15),(x,y)->false,0,255,seed);
    assertEquals(p(16,15),r.bestPoint()); assertTrue(r.nativePathFound());
    assertFalse(r.reachedTarget()); assertTrue(r.path().isEmpty());
    assertEquals(0x6ac690c5L,seed.getLSeed()); assertEquals(15,r.finalCutoff());
  }

  @Test void exactChildTargetAlsoConsumesItsUnusedDirectionRoll() {
    D2Seed seed=new D2Seed(1,0);
    D2IDAStar.Result r=run(p(16,16),(x,y)->false,0,0,seed);
    assertTrue(r.reachedTarget()); assertTrue(r.path().isEmpty());
    assertEquals(0x6ac690c5L,seed.getLSeed()); assertEquals(4,r.finalCutoff());
  }

  @Test void typeSpecificCutoffUsesSignedWordAndDivisionTowardZero() {
    D2IDAStar.Result r=run(p(20015,16),(x,y)->true,0,0,new D2Seed(1,0));
    // root h=(short)40001=-25535; h+h/2=-38302, narrowed to 27234.
    assertEquals(1,r.cutoffIterations()); assertEquals(27239,r.finalCutoff());
  }

  @Test void contourIncrementDoesNotNarrowBackToShort() {
    D2IDAStar.Result r=run(p(10937,15),(x,y)->true,0,0,new D2Seed(1,0));
    assertEquals(1,r.cutoffIterations()); assertEquals(32771,r.finalCutoff());
  }

  @Test void invalidInputsAndPaddedBufferDomainRejectBeforeAnyRoll() {
    D2Seed seed=new D2Seed(1,666); long initial=seed.getLSeed();
    assertThrows(IllegalArgumentException.class,()->run(p(20,15),(x,y)->false,0,0,null));
    assertThrows(IllegalArgumentException.class,()->run(p(-1,15),(x,y)->false,0,0,seed));
    assertThrows(IllegalArgumentException.class,()->run(p(20,15),(x,y)->false,256,0,seed));
    assertThrows(IllegalArgumentException.class,()->run(p(20,15),(x,y)->false,0,256,seed));
    assertThrows(IllegalArgumentException.class,()->D2IDAStar.findNativeStreamPath(p(110,110),p(120,110),
        new D2IDAStar.Bounds(100,100,350,300),(x,y)->false,70,0,seed));
    assertEquals(initial,seed.getLSeed());
  }

  @Test void callbackExceptionDoesNotRollBackEarlierDirectionConsumption() {
    D2Seed seed=new D2Seed(1,0); int[] queries={0};
    assertThrows(IllegalStateException.class,()->run(p(20,15),(x,y)->{
      if(++queries[0]==2)throw new IllegalStateException("collision view failed");return true;
    },0,0,seed));
    assertEquals(0x6ac690c5L,seed.getLSeed());
  }

  @Test void repeatedRequestsContinueLiveSeedRatherThanResettingOrCloningIt() {
    D2Seed seed=new D2Seed(1,0); run(p(20,15),(x,y)->true,0,0,seed);
    Oracle expected=new Oracle(box(),p(20,15),(x,y)->true,0,seed.getLSeed());
    expected.run(p(15,15),0); run(p(20,15),(x,y)->true,0,0,seed);
    assertEquals(expected.seed,seed.getLSeed()); assertEquals(7,expected.rolls);
  }

  @Test void exclusiveCeilingAndCrossContourCacheKeepConsumingTheSameSeed() {
    D2Seed one=new D2Seed(1,0),two=new D2Seed(1,0);List<D2Path.Point> queries=new ArrayList<>();
    assertEquals(1,run(p(20,15),(x,y)->true,20,0,one).cutoffIterations());
    D2IDAStar.Result r=run(p(20,15),(x,y)->{queries.add(p(x,y));return true;},21,0,two);
    assertEquals(2,r.cutoffIterations());assertEquals(25,r.finalCutoff());
    assertEquals(0x5a0cc847eea0f793L,one.getLSeed());assertEquals(0x2aed0d8050155d6dL,two.getLSeed());
    assertEquals(new HashSet<>(queries).size(),queries.size());
  }

  @Test void installedDllRandomOffsetsMatchAllThirtyTwoEntries() throws Exception {
    ByteBuffer dll=D2PathUnitsTest.originalDll("D2Common.dll",
        "B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB");
    Field field=D2IDAStar.class.getDeclaredField("RANDOM_OFFSETS"); field.setAccessible(true);
    int[] table=(int[])field.get(null); assertEquals(32,table.length);
    int offset=D2PathUnitsTest.fileOffset(dll,0x6fdd1ce0L);
    for(int value:table) { assertEquals(dll.getInt(offset),value);offset+=4; }
  }

  /** Recursive, chain-valued oracle. No production node/backtrack/RNG calls.
   * Explicitly accounts for an unused parent-unwind advance after its last child.
   * Safe small coordinates only; this is NOT dynamic DLL execution. */
  private static final class Oracle {
    static final int[][] OFF={{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
    static final int[][] SEQ={{0,1,6,3,4,5,2,7},{0,1,6,3,1,3,6,1},{0,1,1,1,1,1,2,7},
        {1,1,1,1,1,3,6,1},{6,4,3,6,1,3,2,7},{7,7,7,7,7,5,2,7},{0,7,7,7,7,5,2,7},{0,7,2,5,7,5,2,7}};
    static final int[] RAND={-2,-1,0,1,2,-2,-1,0,1,2,-2,-1,0,1,2,-2,-1,0,1,2,-2,-1,0,1,2,-2,-1,0,1,2,-1,1};
    final Map<D2Path.Point,Integer> cache=new HashMap<>();
    final List<D2Path.Point> queries=new ArrayList<>();
    final D2IDAStar.Bounds bounds; final D2Path.Point target; final D2AStar.Collision collision;
    final int threshold;
    long seed; int rolls,unwinds,visits,cutoff,iterations,nodes,maxNodes; boolean exhausted;
    Oracle(D2IDAStar.Bounds bounds,D2Path.Point target,D2AStar.Collision collision,int threshold,long seed) {
      this.bounds=bounds;this.target=target;this.collision=collision;this.threshold=threshold;this.seed=seed;
    }
    int advance(int dir,int row,int i) {
      seed=(seed>>>32)+0x6ac690c5L*(seed&0xffffffffL);rolls++;
      return (dir+SEQ[row+i/8][i%8]+RAND[(int)seed&31])&7;
    }
    List<D2Path.Point> run(D2Path.Point start,int ceiling) {
      int h=(short)D2Path.aStarHeuristic(start.x,start.y,target.x,target.y);
      cutoff=(short)(h+h/2);ceiling=Math.max(cutoff,ceiling);
      do {
        visits=0;nodes=1;exhausted=false;iterations++;
        List<D2Path.Point> chain=visit(start,0,D2PathMisc.primaryDirection(start,target),0,true);
        maxNodes=Math.max(maxNodes,nodes);
        if(chain!=null)return chain;
        if(nodes==900)break;
        cutoff+=5;
      }while(cutoff<ceiling);
      return null;
    }
    List<D2Path.Point> visit(D2Path.Point at,int g,int dir,int depth,boolean root) {
      int row=root?0:(D2PathMisc.primaryDirection(at,target)-dir)&7;
      if(!root)dir=advance(dir,row,0); // even exact target/threshold terminals consume this
      int h=(short)D2Path.aStarHeuristic(at.x,at.y,target.x,target.y);
      if((!root && h<threshold) || at.equals(target))return new ArrayList<>(Arrays.asList(at));
      for(int i=0;i<(root?8:5);i++) {
        if(++visits>10000){exhausted=true;return null;}
        if(!bounds.contains(at.x,at.y))return new ArrayList<>(Arrays.asList(at));
        D2Path.Point next=p(at.x+OFF[dir][0],at.y+OFF[dir][1]);
        int old=cache.getOrDefault(next,0);boolean enteredChild=false;
        if(old==0) {
          queries.add(next);
          if(collision.isBlocked(next.x,next.y)){cache.put(next,1);old=1;}
        }
        int ng=g+(at.x==next.x || at.y==next.y?2:3);
        if(old!=1 && (old==0 || old>=ng)) {
          cache.put(next,ng);
          int nh=(short)D2Path.aStarHeuristic(next.x,next.y,target.x,target.y);
          if((short)(ng+nh)<=cutoff) {
            if(depth+1>=nodes){if(nodes==900){exhausted=true;return null;}nodes++;}
            enteredChild=true;
            List<D2Path.Point> tail=visit(next,ng,dir,depth+1,false);
            if(tail!=null){tail.add(0,at);return tail;}
            if(exhausted)return null;
          }
        }
        if(i<(root?7:4) || enteredChild) {
          dir=advance(dir,row,i+1);
          if(i==(root?7:4)){unwinds++;}
        }
      }
      return null;
    }
  }

  @Test void independentOracleMatchesThreeHundredRandomSearchesAndFinalSeeds() {
    Random random=new Random(0x110f20);int unwinds=0,multicontours=0,successes=0;
    for(int i=0;i<300;i++) {
      D2Path.Point start=p(15,15),target=p(12+random.nextInt(12),12+random.nextInt(12));
      int salt=random.nextInt(),ceiling=10+random.nextInt(80),threshold=random.nextInt(5);
      D2AStar.Collision collision=(x,y)->((x*73856093 ^ y*19349663 ^ salt)&7)<3;
      D2Seed seed=new D2Seed(random.nextInt(),random.nextInt());
      Oracle oracle=new Oracle(box(),target,collision,threshold,seed.getLSeed());
      List<D2Path.Point> chain=oracle.run(start,ceiling),queries=new ArrayList<>();
      D2IDAStar.Result r=run(target,(x,y)->{queries.add(p(x,y));return collision.isBlocked(x,y);},ceiling,threshold,seed);
      assertEquals(oracle.seed,seed.getLSeed(),"seed case "+i);
      assertEquals(oracle.queries,queries,"queries case "+i);
      assertEquals(new HashSet<>(queries).size(),queries.size());
      assertEquals(chain!=null,r.nativePathFound(),"terminal case "+i);
      assertEquals(oracle.iterations,r.cutoffIterations());assertEquals(oracle.cutoff,r.finalCutoff());
      assertEquals(oracle.nodes,r.allocatedNodes());assertEquals(oracle.maxNodes,r.maximumAllocatedNodes());
      List<D2Path.Point> expected=chain==null?new ArrayList<>():D2Path.compressAStarPath(chain);
      if(expected.size()==1)expected.clear();
      assertEquals(expected,r.path(),"flush case "+i);
      assertEquals(chain==null?start:chain.get(chain.size()-1),r.bestPoint());
      unwinds+=oracle.unwinds;if(oracle.iterations>1)multicontours++;if(chain!=null)successes++;
    }
    assertTrue(unwinds>0,"must exercise unused parent-unwind rolls");
    assertTrue(multicontours>0,"must exercise persistent RNG/cache across contours");
    assertTrue(successes>0 && successes<300,"must exercise both outcomes");
  }
}
