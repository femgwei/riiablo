package com.d2moo.common.drlg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DrlgMazeMergeTest {
  @Test void mergingAdjacentRoomsCreatesReciprocalRoomOrthsNotWholeLevelOrths() {
    D2DrlgLevel level = new D2DrlgLevel();
    level.setLevelType(17); // LvlTypes.txt / D2MOO LVLTYPE_ACT2_TOMB
    D2MazeRecord maze = new D2MazeRecord();
    maze.setDwMerge(1000);
    level.setPresetOrOutdoorsOrMaze(maze);
    D2DrlgRoom left = room(level, 0, 0);
    D2DrlgRoom right = room(level, 16, 0);
    left.setDrlgRoomNext(right);
    level.setFirstRoomEx(left);
    DrlgMaze.mergeMazeRooms(right);
    assertNotNull(left.getDrlgOrth());
    assertSame(right, left.getDrlgOrth().getPDrlgRoom());
    assertSame(right.getDrlgCoord(), left.getDrlgOrth().getPBox());
    assertEquals(2, left.getDrlgOrth().getNDirection());
    assertSame(left, right.getDrlgOrth().getPDrlgRoom());
    assertSame(left.getDrlgCoord(), right.getDrlgOrth().getPBox());
    assertEquals(0, right.getDrlgOrth().getNDirection());
    D2DrlgOrth forward = left.getDrlgOrth(), reverse = right.getDrlgOrth();
    DrlgMaze.mergeMazeRooms(right);
    assertSame(forward, left.getDrlgOrth());
    assertSame(reverse, right.getDrlgOrth());
    assertNull(forward.getPNext());
    assertNull(reverse.getPNext());
  }

  private static D2DrlgRoom room(D2DrlgLevel level, int x, int y) {
    D2DrlgRoom room = new D2DrlgRoom();
    room.setLevel(level);
    room.setNTileXPos(x);
    room.setNTileYPos(y);
    room.setNTileWidth(16);
    room.setNTileHeight(16);
    room.setMazeOrOutdoor(new D2DrlgPresetRoomStrc());
    return room;
  }
}
