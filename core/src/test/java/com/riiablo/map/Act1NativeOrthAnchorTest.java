package com.riiablo.map;

import com.d2moo.common.drlg.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Act1NativeOrthAnchorTest {
  @Test void bothEndpointsUseConnectorRoomEdgeNotWholeLevelCenter() {
    D2DrlgLevel barracks = level(100, 200), cloister = level(80, 200);
    D2DrlgRoom connector = new D2DrlgRoom();
    connector.setLevel(barracks);
    connector.setNTileXPos(100);
    connector.setNTileYPos(202);
    connector.setNTileWidth(8);
    connector.setNTileHeight(8);
    barracks.setFirstRoomEx(connector);
    D2DrlgOrth orth = new D2DrlgOrth();
    orth.setPLevel(cloister);
    orth.setNDirection((byte) 0);
    connector.setDrlgOrth(orth);
    assertArrayEquals(new int[] {0, 30}, Act1MapBuilderD2MOD.nativeOrthAnchor(barracks, cloister));
    assertArrayEquals(new int[] {100, 30}, Act1MapBuilderD2MOD.nativeOrthAnchor(cloister, barracks));
    orth.setNDirection((byte) 1);
    assertArrayEquals(new int[] {20, 10}, Act1MapBuilderD2MOD.nativeOrthAnchor(barracks, cloister));
    orth.setNDirection((byte) 2);
    assertArrayEquals(new int[] {40, 30}, Act1MapBuilderD2MOD.nativeOrthAnchor(barracks, cloister));
    orth.setNDirection((byte) 3);
    assertArrayEquals(new int[] {20, 50}, Act1MapBuilderD2MOD.nativeOrthAnchor(barracks, cloister));
  }

  @Test void noOrthDoesNotInventAnEntranceRoom() {
    assertNull(Act1MapBuilderD2MOD.nativeOrthAnchor(level(0, 0), level(10, 0)));
  }

  private static D2DrlgLevel level(int x, int y) {
    D2DrlgLevel level = new D2DrlgLevel();
    D2DrlgCoord coordinates = new D2DrlgCoord();
    coordinates.setNPosX(x);
    coordinates.setNPosY(y);
    level.setLevelCoords(coordinates);
    return level;
  }
}
