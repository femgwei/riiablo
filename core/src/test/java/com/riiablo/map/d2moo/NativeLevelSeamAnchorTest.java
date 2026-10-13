package com.riiablo.map.d2moo;

import com.d2moo.common.drlg.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeLevelSeamAnchorTest {
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
