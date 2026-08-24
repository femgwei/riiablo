package com.d2moo.common.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgCoord;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgRoomFlags;
import com.d2moo.common.drlg.D2DrlgRoomStatus;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.DrlgActivate;

import org.junit.jupiter.api.Test;

class DungeonRoomSightTest {
  @Test
  void setsQueriesAndUnsetsClientSightThroughDungeonFacade() {
    RoomGraph graph = roomGraph();

    Dungeon.setClientIsInSight(graph.act, 2, 4, 4, graph.active);

    assertEquals(1,
        graph.room.getRoomsInList()[D2DrlgRoomStatus.CLIENT_IN_SIGHT.getValue()]);
    assertEquals(D2DrlgRoomStatus.CLIENT_IN_SIGHT, graph.room.getRoomStatus());
    assertSame(graph.active, Dungeon.getARoomInClientSight(graph.act));

    Dungeon.unsetClientIsInSight(graph.act, 2, 4, 4, graph.active);

    assertEquals(0,
        graph.room.getRoomsInList()[D2DrlgRoomStatus.CLIENT_IN_SIGHT.getValue()]);
    assertEquals(D2DrlgRoomStatus.COUNT, graph.room.getRoomStatus());
    assertNull(Dungeon.getARoomInClientSight(graph.act));
  }

  @Test
  void changesClientRoomAndPreservesNativeNullSemantics() {
    RoomGraph graph = roomGraph();

    Dungeon.changeClientRoom(null, graph.active);
    assertEquals(1,
        graph.room.getRoomsInList()[D2DrlgRoomStatus.CLIENT_IN_ROOM.getValue()]);
    assertEquals(D2DrlgRoomStatus.CLIENT_IN_ROOM, graph.room.getRoomStatus());
    assertSame(graph.active, Dungeon.getARoomInClientSight(graph.act));
    assertNull(Dungeon.getARoomInSightButWithoutClient(graph.act, graph.active));

    Dungeon.changeClientRoom(graph.active, null);
    assertEquals(0,
        graph.room.getRoomsInList()[D2DrlgRoomStatus.CLIENT_IN_ROOM.getValue()]);
    assertEquals(D2DrlgRoomStatus.COUNT, graph.room.getRoomStatus());
    Dungeon.changeClientRoom(null, null);
    assertNull(Dungeon.getARoomInClientSight(null));
    assertNull(Dungeon.getARoomInSightButWithoutClient(null, null));
  }

  @Test
  void streamsAnAlreadyInitializedRoomAtTileCoordinates() {
    RoomGraph graph = roomGraph();
    graph.room.setFlags(D2DrlgRoomFlags.TILELIB_LOADED
        | D2DrlgRoomFlags.PRESET_UNITS_ADDED | D2DrlgRoomFlags.HAS_ROOM);

    assertSame(graph.active, Dungeon.streamRoomAtCoords(graph.act, 4, 4));
    assertNull(Dungeon.streamRoomAtCoords(graph.act, 40, 40));
    assertNull(Dungeon.streamRoomAtCoords(null, 4, 4));
  }

  private static RoomGraph roomGraph() {
    D2DrlgAct act = new D2DrlgAct();
    D2DrlgStrc drlg = new D2DrlgStrc();
    act.setDrlg(drlg);
    drlg.setAct(act);
    DrlgActivate.initializeRoomExStatusLists(drlg);

    D2DrlgLevel level = new D2DrlgLevel();
    level.setDrlg(drlg);
    level.setLevelId(2);
    level.setLevelCoords(coord(0, 0, 10, 10));
    drlg.setLevel(level);

    D2DrlgRoom room = new D2DrlgRoom();
    room.setLevel(level);
    room.setDrlgCoord(coord(0, 0, 10, 10));
    room.setRoomStatus(D2DrlgRoomStatus.COUNT);
    room.setPpRoomsNear(new D2DrlgRoom[] {room});
    room.setNRoomsNear(1);
    level.setFirstRoomEx(room);
    level.setRooms(1);

    D2ActiveRoom active = new D2ActiveRoom();
    active.setPDrlgRoom(room);
    active.setAct(act);
    active.setCoords(toActiveCoords(room.getDrlgCoord()));
    act.setRoom(active);
    return new RoomGraph(act, room, active);
  }

  private static D2DrlgCoord coord(int x, int y, int width, int height) {
    D2DrlgCoord coord = new D2DrlgCoord();
    coord.setNTileXPos(x);
    coord.setNTileYPos(y);
    coord.setNTileWidth(width);
    coord.setNTileHeight(height);
    return coord;
  }

  private static com.d2moo.common.drlg.D2DrlgCoords toActiveCoords(D2DrlgCoord room) {
    com.d2moo.common.drlg.D2DrlgCoords coords =
        new com.d2moo.common.drlg.D2DrlgCoords();
    coords.setNTileXPos(room.getNTileXPos());
    coords.setNTileYPos(room.getNTileYPos());
    coords.setNTileWidth(room.getNTileWidth());
    coords.setNTileHeight(room.getNTileHeight());
    coords.setNSubtileX(room.getNTileXPos() * 5);
    coords.setNSubtileY(room.getNTileYPos() * 5);
    coords.setNSubtileWidth(room.getNTileWidth() * 5);
    coords.setNSubtileHeight(room.getNTileHeight() * 5);
    return coords;
  }

  private static final class RoomGraph {
    final D2DrlgAct act;
    final D2DrlgRoom room;
    final D2ActiveRoom active;

    RoomGraph(D2DrlgAct act, D2DrlgRoom room, D2ActiveRoom active) {
      this.act = act;
      this.room = room;
      this.active = active;
    }
  }
}
