package com.d2moo.common.collision;

import com.d2moo.common.drlg.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D2CollisionAnyCrossTest {
  private static D2ActiveRoom room(int x,int y,int w,int h) {
    D2ActiveRoom r=new D2ActiveRoom(); D2DrlgCoords c=new D2DrlgCoords();
    c.setNSubtileX(x);c.setNSubtileY(y);c.setNSubtileWidth(w);c.setNSubtileHeight(h);
    r.setCoords(c);D2CommonCollision.allocRoomCollisionGrid(r);return r;
  }
  private static void near(D2ActiveRoom r,D2ActiveRoom... list) {r.setPpRoomList(list);r.setNNumRooms(list.length);}
  private static int mask(D2ActiveRoom r,int x,int y,int bits) {return D2CommonCollision.checkMaskWithPattern(r,x,y,1,bits);}
  private static int any(D2ActiveRoom r,int x,int y,int pattern,int bits) {return D2CommonCollision.checkAnyCollisionWithPattern(r,x,y,pattern,bits);}
  @Test void missingCenterUsesRequestedMaskRatherThanPointInvalid() {
    assertEquals(0x8000,mask(null,10,10,0x8000));assertEquals(0,mask(null,10,10,0));
    assertEquals(1,any(null,10,10,1,0));
  }
  @Test void missingCenterDoesNotVisitCoveredArms() {
    D2ActiveRoom r=room(0,0,3,3);r.getPCollisionGrid().setFlag(0,1,0x8000);
    assertEquals(0,mask(r,-1,1,0));assertEquals(1,any(r,-1,1,1,0));
  }
  @Test void resolvedCenterSuppliesHintsForArms() {
    D2ActiveRoom a=room(0,0,3,3),b=room(3,0,3,3),c=room(6,0,3,3);
    near(a,b);near(b,c);c.getPCollisionGrid().setFlag(0,1,0x8000);
    assertEquals(0x8000,mask(a,5,1,0x8000));assertEquals(1,any(a,5,1,1,0x8000));
  }
  @Test void armCannotUseRootHintAfterCenterSwitch() {
    D2ActiveRoom a=room(0,0,3,3),b=room(3,0,3,3),c=room(6,0,3,3);near(a,b,c);
    assertEquals(0x27,mask(a,5,1,0));assertEquals(1,any(a,5,1,1,0));
  }
  @Test void widthOrHeightOneUsesDefaultSwitchEvenIfNeighborsExist() {
    for(int[] size:new int[][]{{1,3},{3,1},{1,1}}) {
      D2ActiveRoom r=room(10,10,size[0],size[1]);
      assertEquals(0x8000,mask(r,10,10,0x8000));assertEquals(0,mask(r,10,10,0));
      assertEquals(1,any(r,10,10,1,0));
    }
  }
  @Test void allNineNormalEdgeCasesUseLocalAndBoundaryCells() {
    D2ActiveRoom r=room(0,0,3,3),cover=room(-1,-1,5,5);near(r,cover);
    for(int y=0;y<3;y++)for(int x=0;x<3;x++) {
      assertEquals(0,mask(r,x,y,0xffff));assertEquals(0,any(r,x,y,1,0xffff));
    }
    r.getPCollisionGrid().setFlag(1,1,0x8000);
    assertEquals(0x8000,mask(r,0,1,0x8000));assertEquals(1,any(r,0,1,1,0x8000));
  }
  @Test void anyShortCircuitsAtCenterAndMaskStillCombinesAllBits() {
    D2ActiveRoom r=room(0,0,5,5);List<String> trace=new ArrayList<>();
    installTrace(r,trace);r.getPCollisionGrid().setFlag(2,2,1);r.getPCollisionGrid().setFlag(1,2,2);
    assertEquals(1,any(r,2,2,1,3));assertEquals(Collections.singletonList("2,2"),trace);
    trace.clear();assertEquals(3,mask(r,2,2,3));assertEquals(5,trace.size());
  }
  @Test void anyInteriorOrderIsCenterLeftRightBottomTop() {
    D2ActiveRoom r=room(0,0,5,5);List<String> trace=new ArrayList<>();installTrace(r,trace);
    assertEquals(0,any(r,2,2,1,1));
    assertEquals(Arrays.asList("2,2","1,2","3,2","2,1","2,3"),trace);
  }
  @Test void anyBottomLeftQueriesBottomBeforeLeftAfterLocalChecks() {
    D2ActiveRoom r=room(0,0,3,3),cover=room(-1,-1,5,5);near(r,cover);
    List<String> trace=new ArrayList<>();installTrace(cover,trace);
    assertEquals(0,any(r,0,0,1,1));assertEquals(Arrays.asList("1,0","0,1"),trace);
  }
  private static void installTrace(D2ActiveRoom r,List<String> trace) {
    int w=r.getCoords().getNSubtileWidth(),h=r.getCoords().getNSubtileHeight();
    D2DrlgGridStrc g=new D2DrlgGridStrc(w,h) {
      @Override public int getFlag(int x,int y){trace.add(x+","+y);return super.getFlag(x,y);}
    };
    int[] rows=new int[h];for(int i=0;i<h;i++)rows[i]=i*w;
    g.setPCellsFlags(new int[w*h]);g.setPCellsRowOffsets(rows);r.setPCollisionGrid(g);
  }
  @Test void mutationCrossStillUsesOriginalHintForEachPoint() {
    D2ActiveRoom a=room(0,0,3,3),b=room(3,0,3,3),c=room(6,0,3,3);near(a,b);near(b,c);
    D2CommonCollision.setMaskWithSize(a,5,1,2,0x100);
    assertEquals(0x100,b.getPCollisionGrid().getFlag(2,1));assertEquals(0,c.getPCollisionGrid().getFlag(0,1));
    D2CommonCollision.resetMaskWithSize(a,5,1,2,0x100);assertEquals(0,b.getPCollisionGrid().getFlag(2,1));
  }
  @Test void noneAnyPreservesRawPointMaskAndUnknownPatternReturnsOne() {
    D2ActiveRoom r=room(0,0,3,3);r.getPCollisionGrid().setFlag(1,1,0x8000);
    assertEquals(0x8000,any(r,1,1,0,0x8000));assertEquals(0x27,any(null,1,1,0,0));
    assertEquals(1,any(r,1,1,6,0));
  }
  @Test void bigAnyScansWholeLocalRectangleThenSkipsRightSubtree() {
    D2ActiveRoom r=room(0,0,3,3),b=room(3,0,3,3);near(r,b);
    List<String> local=new ArrayList<>(),other=new ArrayList<>();installTrace(r,local);installTrace(b,other);
    r.getPCollisionGrid().setFlag(1,0,1);
    assertEquals(1,any(r,2,1,2,1));assertEquals(6,local.size());assertTrue(other.isEmpty());
    local.clear();assertEquals(1,D2CommonCollision.checkMaskWithPattern(r,2,1,2,1));assertEquals(3,other.size());
  }
  @Test void bigAnyMissingAnchorAndZeroMaskStillReturnTrue() {
    D2ActiveRoom r=room(0,0,3,3);assertEquals(1,any(r,0,1,2,0));
    assertEquals(0,any(r,1,1,2,0));assertEquals(1,any(r,2,1,2,0));
  }
  @Test void missingMaskPointerUsesNativePointAndCrossFallbacks() {
    D2ActiveRoom r=room(0,0,3,3);r.getPCollisionGrid().setPCellsFlags(null);
    assertEquals(0x27,D2CommonCollision.checkMask(r,1,1,0));assertEquals(0x8000,mask(r,1,1,0x8000));
    assertEquals(1,any(r,1,1,1,0));assertDoesNotThrow(()->D2CommonCollision.setMask(r,1,1,1));
  }
  @Test void randomTiledCrossAndAnyResultsMatchFlatCellOracle() {
    Random random=new Random(24);D2ActiveRoom[][] rooms=new D2ActiveRoom[3][3];int[][] flags=new int[9][9];
    for(int ry=0;ry<3;ry++)for(int rx=0;rx<3;rx++)rooms[ry][rx]=room(rx*3,ry*3,3,3);
    for(int ry=0;ry<3;ry++)for(int rx=0;rx<3;rx++) {
      List<D2ActiveRoom> list=new ArrayList<>();
      for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)
        if(ry+dy>=0&&ry+dy<3&&rx+dx>=0&&rx+dx<3)list.add(rooms[ry+dy][rx+dx]);
      near(rooms[ry][rx],list.toArray(new D2ActiveRoom[0]));
    }
    for(int sample=0;sample<200;sample++) {
      for(int y=0;y<9;y++)for(int x=0;x<9;x++) {
        flags[y][x]=random.nextInt(65536);rooms[y/3][x/3].getPCollisionGrid().setFlag(x%3,y%3,flags[y][x]);
      }
      int x=1+random.nextInt(7),y=1+random.nextInt(7),bits=random.nextBoolean()?0:random.nextInt(65536);
      for(int pattern=0;pattern<=5;pattern++) {
        int expected=0;
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
          boolean included=pattern==0?dx==0&&dy==0:pattern==2||pattern==4||dx==0||dy==0;
          if(included)expected|=flags[y+dy][x+dx]&bits;
        }
        D2ActiveRoom hint=rooms[y/3][x/3];
        assertEquals(expected,D2CommonCollision.checkMaskWithPattern(hint,x,y,pattern,bits));
        assertEquals(pattern==0?expected:expected==0?0:1,any(hint,x,y,pattern,bits));
      }
    }
  }
}
