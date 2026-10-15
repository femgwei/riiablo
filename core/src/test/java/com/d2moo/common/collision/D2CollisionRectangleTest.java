package com.d2moo.common.collision;

import com.d2moo.common.drlg.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D2CollisionRectangleTest {
  private static final int INVALID = D2Collision.COLLIDE_MASK_INVALID;
  private static D2ActiveRoom room(int x,int y,int w,int h) {
    D2ActiveRoom r=new D2ActiveRoom(); D2DrlgCoords c=new D2DrlgCoords();
    c.setNSubtileX(x); c.setNSubtileY(y); c.setNSubtileWidth(w); c.setNSubtileHeight(h);
    r.setCoords(c); D2CommonCollision.allocRoomCollisionGrid(r); return r;
  }
  private static void near(D2ActiveRoom r,D2ActiveRoom... rooms) {
    r.setPpRoomList(rooms); r.setNNumRooms(rooms.length);
  }
  private static int read(D2ActiveRoom r,int x,int y,int w,int h,int mask) {
    return D2CommonCollision.checkMaskWithSizeXY(r,x,y,w,h,mask);
  }
  private static void write(D2ActiveRoom r,int x,int y,int w,int h,int mask) {
    D2CommonCollision.setMaskWithSizeXY(r,x,y,w,h,mask);
  }
  @Test void followsRoomChainBeyondInitialNearList() {
    D2ActiveRoom a=room(0,0,2,2), b=room(2,0,2,2), c=room(4,0,2,2);
    near(a,b); near(b,c); c.getPCollisionGrid().setFlag(1,0,0x8000);
    assertEquals(0x8000,read(a,3,0,6,1,0xffff));
    write(a,3,0,6,1,0x100);
    assertEquals(0x8100,c.getPCollisionGrid().getFlag(1,0));
    D2CommonCollision.resetMaskWithSizeXY(a,3,0,6,1,0x100);
    assertEquals(0x8000,c.getPCollisionGrid().getFlag(1,0));
  }
  @Test void missingLowerLeftAbandonsEntireRectangle() {
    D2ActiveRoom a=room(0,0,3,3);
    assertEquals(INVALID,read(a,0,1,3,1,0));
    write(a,0,1,3,1,0x100);
    assertEquals(0,a.getPCollisionGrid().getFlag(0,1));
  }
  @Test void resetAlsoSkipsCoveredCellsWhenAnchorIsMissing() {
    D2ActiveRoom a=room(0,0,3,3); a.getPCollisionGrid().setFlag(0,1,0x100);
    D2CommonCollision.resetMaskWithSizeXY(a,0,1,3,1,0x100);
    assertEquals(0x100,a.getPCollisionGrid().getFlag(0,1));
  }
  @Test void missingRightSubtreeDoesNotHideValidLocalBits() {
    D2ActiveRoom a=room(0,0,2,2); a.getPCollisionGrid().setFlag(0,0,0x8000);
    assertEquals(INVALID|0x8000,read(a,2,0,4,1,0x8000));
    write(a,2,0,4,1,0x100);
    assertEquals(0x8100,a.getPCollisionGrid().getFlag(0,0));
  }
  @Test void missingGridDoesNotSearchAlternateOverlappingRoom() {
    D2ActiveRoom a=room(0,0,2,2), duplicate=room(0,0,2,2);
    near(a,duplicate); a.setPCollisionGrid(null);
    assertEquals(INVALID,read(a,1,0,2,1,0));
    write(a,1,0,2,1,1); assertEquals(0,duplicate.getPCollisionGrid().getFlag(0,0));
  }
  @Test void newAnchorRoomBecomesHintForItsUpperChild() {
    D2ActiveRoom a=room(0,0,2,2), b=room(2,0,2,2), upper=room(2,2,2,2);
    near(a,b); near(b,upper); upper.getPCollisionGrid().setFlag(0,0,0x2000);
    assertEquals(0x2000,read(a,3,2,2,4,0xffff));
  }
  @Test void followsNativeRightThenUpperDepthFirstOrder() {
    List<String> trace=new ArrayList<>();
    D2ActiveRoom a=traced(0,0,"a",trace), b=traced(2,0,"b",trace),
        c=traced(0,2,"c",trace), d=traced(2,2,"d",trace);
    near(a,b,c); near(b,d); near(c,d);
    assertEquals(0,read(a,2,2,4,4,0xffff));
    assertEquals(Arrays.asList("a","b","d","c"),trace);
  }
  private static D2ActiveRoom traced(int x,int y,String name,List<String> trace) {
    D2ActiveRoom r=room(x,y,2,2);
    D2DrlgGridStrc g=new D2DrlgGridStrc(2,2) {
      @Override public int getFlag(int lx,int ly) { if(lx==0&&ly==0) trace.add(name); return super.getFlag(lx,ly); }
    };
    g.setPCellsFlags(new int[4]); g.setPCellsRowOffsets(new int[]{0,2}); r.setPCollisionGrid(g); return r;
  }
  @Test void branchCannotUseOriginalHintsUnrelatedNeighbors() {
    D2ActiveRoom a=room(0,0,2,2), b=room(2,0,2,2), c=room(4,0,2,2);
    near(a,b,c); c.getPCollisionGrid().setFlag(0,0,0x8000);
    assertEquals(INVALID,read(a,3,0,6,1,0x8000));
    write(a,3,0,6,1,0x100); assertEquals(0x8000,c.getPCollisionGrid().getFlag(0,0));
  }
  @Test void oddEvenBiasAndInclusiveUpperBounds() {
    D2ActiveRoom a=room(0,0,6,6); write(a,3,3,2,4,0x400);
    for(int y=0;y<6;y++) for(int x=0;x<6;x++)
      assertEquals(x>=2&&x<=3&&y>=1&&y<=4?0x400:0,a.getPCollisionGrid().getFlag(x,y));
  }
  @Test void bigPatternsAndBigSizeUseSameRecursiveRouting() {
    D2ActiveRoom a=room(0,0,2,4), b=room(2,0,2,4), c=room(4,0,2,4);
    near(a,b); near(b,c); c.getPCollisionGrid().setFlag(0,1,0x8000);
    assertEquals(0x8000,D2CommonCollision.checkMaskWithPattern(a,3,1,2,0x8000));
    assertEquals(0x8000,D2CommonCollision.checkMaskWithSize(a,3,1,3,0x8000));
    D2CommonCollision.setMaskWithSize(a,3,1,3,0x100);
    assertEquals(0x8100,c.getPCollisionGrid().getFlag(0,1));
  }
  @Test void zeroMaskStillDetectsMissingSubtreeAndDoesNotMutate() {
    D2ActiveRoom a=room(0,0,2,2); a.getPCollisionGrid().setFlag(1,0,0xabcd);
    assertEquals(INVALID,read(a,2,0,4,1,0)); write(a,2,0,4,1,0);
    assertEquals(0xabcd,a.getPCollisionGrid().getFlag(1,0));
  }
  @Test void nullHintReadAndMutationRetainNativeMissingRoomPolicy() {
    assertEquals(INVALID,read(null,2,2,3,3,0));
    assertDoesNotThrow(()->write(null,2,2,3,3,0x100));
  }
  @Test void safeDomainRejectsWrappingBoxesAndRoomEnds() {
    assertThrows(IllegalArgumentException.class,()->write(null,Integer.MIN_VALUE,0,3,1,1));
    assertThrows(IllegalArgumentException.class,()->write(null,Integer.MAX_VALUE,0,3,1,1));
    D2ActiveRoom a=room(Integer.MAX_VALUE-1,0,3,3);
    assertThrows(IllegalArgumentException.class,()->read(a,Integer.MAX_VALUE,1,2,1,1));
  }
  @Test void longRoomChainUsesExplicitStack() {
    D2ActiveRoom[] rooms=new D2ActiveRoom[2000];
    for(int i=0;i<rooms.length;i++) rooms[i]=room(i,0,1,2);
    for(int i=0;i<rooms.length-1;i++) near(rooms[i],rooms[i+1]);
    rooms[1999].getPCollisionGrid().setFlag(0,0,0x8000);
    assertEquals(0x8000,read(rooms[0],1000,0,2000,1,0xffff));
  }
  @Test void randomTiledGridMatchesFlatOracleForReadsSetsAndResets() {
    Random random=new Random(23);
    for(int sample=0;sample<150;sample++) {
      D2ActiveRoom[][] rooms=new D2ActiveRoom[3][3]; int[][] expected=new int[15][15];
      for(int ry=0;ry<3;ry++) for(int rx=0;rx<3;rx++) rooms[ry][rx]=room(rx*5,ry*5,5,5);
      for(int ry=0;ry<3;ry++) for(int rx=0;rx<3;rx++) {
        List<D2ActiveRoom> adjacent=new ArrayList<>();
        for(int dy=-1;dy<=1;dy++) for(int dx=-1;dx<=1;dx++)
          if(rx+dx>=0&&rx+dx<3&&ry+dy>=0&&ry+dy<3) adjacent.add(rooms[ry+dy][rx+dx]);
        near(rooms[ry][rx],adjacent.toArray(new D2ActiveRoom[0]));
      }
      for(int y=0;y<15;y++) for(int x=0;x<15;x++) {
        expected[y][x]=random.nextInt(65536); rooms[y/5][x/5].getPCollisionGrid().setFlag(x%5,y%5,expected[y][x]);
      }
      int w=2+random.nextInt(12),h=1+random.nextInt(13);
      int left=random.nextInt(16-w),bottom=random.nextInt(16-h),cx=left+w/2,cy=bottom+h/2;
      int mask=random.nextInt(65536),bits=0;
      for(int y=bottom;y<bottom+h;y++) for(int x=left;x<left+w;x++) bits|=expected[y][x]&mask;
      D2ActiveRoom hint=rooms[bottom/5][left/5];
      assertEquals(bits,read(hint,cx,cy,w,h,mask));
      write(hint,cx,cy,w,h,mask);
      for(int y=0;y<15;y++) for(int x=0;x<15;x++) {
        if(x>=left&&x<left+w&&y>=bottom&&y<bottom+h) expected[y][x]|=mask;
        assertEquals(expected[y][x],rooms[y/5][x/5].getPCollisionGrid().getFlag(x%5,y%5));
      }
      D2CommonCollision.resetMaskWithSizeXY(hint,cx,cy,w,h,mask);
      for(int y=0;y<15;y++) for(int x=0;x<15;x++) {
        if(x>=left&&x<left+w&&y>=bottom&&y<bottom+h) expected[y][x]&=~mask;
        assertEquals(expected[y][x],rooms[y/5][x/5].getPCollisionGrid().getFlag(x%5,y%5));
      }
    }
  }
}
