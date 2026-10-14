package com.riiablo.map.d2moo;

import com.d2moo.common.drlg.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeLevelSeamAnchorTest {
  @Test void expectedManifestKeepsUninstalledNativeLinksAndRejectsRealWarpsAndCornerContacts() {
    com.riiablo.map.Map map = new com.riiablo.map.Map(1, 0);
    com.riiablo.map.Map.Zone source = new com.riiablo.map.Map.Zone();
    source.level = new com.riiablo.codec.excel.Levels.Entry(); source.level.Id = 2;
    map.getZones().add(source);
    D2DrlgStrc drlg = new D2DrlgStrc();
    D2DrlgLevel a = level(100,100,20,20), b = level(120,100,20,20), corner = level(120,120,20,20);
    a.setLevelId(2); b.setLevelId(3); corner.setLevelId(4);
    a.setPNextLevel(b); b.setPNextLevel(corner); drlg.setLevel(a);
    D2DrlgWarp links = new D2DrlgWarp(); links.setNLevel(2);
    links.setNVis(new int[]{3,3,4,0,0,0,0,0});
    links.setNWarp(new int[]{-1,5,-1,-1,-1,-1,-1,-1}); drlg.setWarp(links);
    NativeLevelSeamAnchor.captureWalkEdges(map, drlg);
    NativeLevelSeamAnchor.captureWalkEdges(map, drlg);
    assertEquals(1, map.nativeWalkEdges.size, "duplicate captures, real Warps and corner contacts are excluded");
    assertEquals(2, map.nativeWalkEdges.first().from);
    assertEquals(3, map.nativeWalkEdges.first().to);
    assertEquals(0, map.nativeWalkEdges.first().slot);
    assertNull(source.nativeSeamAnchor(0), "manifest must retain the missing installation for failure detection");
  }
  @Test void lavaBridgeUsesTheNativeNorthOrthAndReverseUsesTheSameWorldPoint() {
    D2DrlgLevel river = level(100, 200, 120, 216), chaos = level(120, 80, 120, 120);
    D2DrlgRoom bridge = new D2DrlgRoom();
    bridge.setLevel(river); bridge.setNTileXPos(160); bridge.setNTileYPos(200);
    bridge.setNTileWidth(24); bridge.setNTileHeight(24);
    D2DrlgOrth orth = new D2DrlgOrth();
    orth.setPLevel(chaos); orth.setNDirection((byte) 1); bridge.setDrlgOrth(orth);
    river.setFirstRoomEx(bridge);
    assertArrayEquals(new int[] {360, 0}, NativeLevelSeamAnchor.resolve(river, chaos));
    assertArrayEquals(new int[] {260, 600}, NativeLevelSeamAnchor.resolve(chaos, river));
  }

  @Test void outdoorSeamUsesTheSharedLevelEdgeNotTheRectangularCentre() {
    D2DrlgLevel first = level(100, 200, 80, 80), next = level(180, 216, 80, 80);
    D2DrlgOutdoorInfoStrc outdoor = new D2DrlgOutdoorInfoStrc();
    D2DrlgOrth orth = new D2DrlgOrth(); orth.setPLevel(next); outdoor.setPRoomData(orth);
    first.setPresetOrOutdoorsOrMaze(outdoor);
    assertArrayEquals(new int[] {400, 240}, NativeLevelSeamAnchor.resolve(first, next));
    assertArrayEquals(new int[] {0, 160}, NativeLevelSeamAnchor.resolve(next, first));
    assertNull(NativeLevelSeamAnchor.resolve(first, level(800, 800, 80, 80)));
  }

  private static D2DrlgLevel level(int x, int y, int w, int h) {
    D2DrlgLevel level = new D2DrlgLevel();
    level.getLevelCoords().setNPosX(x); level.getLevelCoords().setNPosY(y);
    level.getLevelCoords().setNWidth(w); level.getLevelCoords().setNHeight(h);
    return level;
  }
}
