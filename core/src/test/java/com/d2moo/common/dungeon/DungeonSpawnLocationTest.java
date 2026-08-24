package com.d2moo.common.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgCoord;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2DrlgTypes;
import com.d2moo.common.drlg.D2DrlgTileGrid;
import org.junit.jupiter.api.Test;

class DungeonSpawnLocationTest {
  @Test
  void invalidActOrLevelDoesNotInventSpawnCoordinates() {
    int[] x = {123};
    int[] y = {456};
    assertNull(Dungeon.findActSpawnLocation(null, 1, 0, x, y));
    assertEquals(123, x[0]);
    assertEquals(456, y[0]);

    D2DrlgAct act = new D2DrlgAct();
    assertNull(Dungeon.findActSpawnLocation(act, 999, 0, x, y));
    assertEquals(123, x[0]);
    assertEquals(456, y[0]);
  }

  @Test
  void streamInitializesOnceAndSpawnExUsesSubtileCenterOffset() {
    D2DrlgAct act = new D2DrlgAct();
    D2DrlgStrc drlg = new D2DrlgStrc();
    drlg.setAct(act);
    act.setDrlg(drlg);
    D2DrlgLevel level = new D2DrlgLevel();
    level.setLevelId(2);
    level.setDrlgType(D2DrlgTypes.DRLGTYPE_PRESET);
    level.getLevelCoords().setNPosX(0);
    level.getLevelCoords().setNPosY(0);
    level.getLevelCoords().setNWidth(10);
    level.getLevelCoords().setNHeight(10);
    level.setDrlg(drlg);
    drlg.setLevel(level);
    D2DrlgRoom room = room(0, 0, 10, 10);
    room.setTileGrid(new D2DrlgTileGrid());
    room.setLevel(level);
    level.setFirstRoomEx(room);
    level.setRooms(1);

    D2ActiveRoom first = Dungeon.streamRoomAtCoords(act, 1, 1);
    D2ActiveRoom second = Dungeon.streamRoomAtCoords(act, 1, 1);
    assertSame(first, second);
    assertSame(room, first.getPDrlgRoom());
    assertEquals(1, act.getRoom() == first ? 1 : 0);

    int[] x = {1};
    int[] y = {1};
    D2ActiveRoom spawn = Dungeon.findActSpawnLocationEx(act, 2, 0, x, y, 1);
    assertSame(first, spawn);
    assertEquals(28, x[0]);
    assertEquals(28, y[0]);
  }

  private static D2DrlgRoom room(int x, int y, int width, int height) {
    D2DrlgCoord coord = new D2DrlgCoord();
    coord.setNTileXPos(x); coord.setNTileYPos(y);
    coord.setNTileWidth(width); coord.setNTileHeight(height);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setDrlgCoord(coord);
    return room;
  }
}
