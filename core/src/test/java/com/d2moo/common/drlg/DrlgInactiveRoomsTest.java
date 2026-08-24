package com.d2moo.common.drlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.d2moo.common.dungeon.Dungeon;

class DrlgInactiveRoomsTest {
  @Test
  void countsDownBeforeFreeingAnUnreferencedLevel() {
    D2DrlgStrc drlg = new D2DrlgStrc();
    D2DrlgLevel level = level(drlg, 1);
    D2DrlgRoom room = room(level, D2DrlgRoomStatus.COUNT, 0);
    room.setRoomTiles(new D2RoomTile());
    level.setFirstRoomEx(room);
    level.setRooms(1);
    level.setInactiveFrames(2);
    drlg.setLevel(level);
    D2DrlgAct act = new D2DrlgAct();
    act.setDrlg(drlg);

    Dungeon.updateAndFreeInactiveRooms(act);
    assertEquals(1, level.getInactiveFrames());
    assertSame(room, level.getFirstRoomEx());
    Dungeon.updateAndFreeInactiveRooms(act);
    assertEquals(0, level.getInactiveFrames());
    assertSame(room, level.getFirstRoomEx());

    Dungeon.updateAndFreeInactiveRooms(act);

    assertNull(level.getFirstRoomEx());
    assertEquals(0, level.getRooms());
    assertNotNull(level.getPresetMaps(), "native alloc=true preserves per-room regeneration state");
    assertEquals(1, level.getPresetMaps().length);
  }

  @Test
  void retainedRoomRestartsNativeTenFrameDelay() {
    D2DrlgStrc drlg = new D2DrlgStrc();
    D2DrlgLevel level = level(drlg, 1);
    D2DrlgRoom room = room(level, D2DrlgRoomStatus.UNTILE, 0);
    level.setFirstRoomEx(room);
    level.setRooms(1);
    drlg.setLevel(level);

    DrlgDrlg.updateAndFreeInactiveRooms(drlg);

    assertSame(room, level.getFirstRoomEx());
    assertEquals(10, level.getInactiveFrames());
  }

  @Test
  void visibleRetainedLinkKeepsInactiveDestinationGenerated() {
    Graph graph = visibleGraph(D2DrlgRoomStatus.UNTILE);

    DrlgDrlg.updateAndFreeInactiveRooms(graph.drlg);

    assertSame(graph.sourceRoom, graph.sourceLevel.getFirstRoomEx());
    assertEquals(10, graph.sourceLevel.getInactiveFrames());
    assertNotNull(graph.linkRoom.getRoomTiles());
    assertNotNull(graph.linkRoom.getPpRoomsNear());
  }

  @Test
  void freesStaleVisibleLinkTilesBeforeEvictingDestination() {
    Graph graph = visibleGraph(D2DrlgRoomStatus.COUNT);

    DrlgDrlg.updateAndFreeInactiveRooms(graph.drlg);

    assertNull(graph.sourceLevel.getFirstRoomEx());
    assertNull(graph.linkRoom.getRoomTiles());
    assertNull(graph.linkRoom.getPpRoomsNear());
    assertEquals(0, graph.linkRoom.getNRoomsNear());
    assertSame(graph.linkRoom, graph.visibleLevel.getFirstRoomEx());
  }

  @Test
  void levelActivityUsesReferenceCountsForLevelAndVisibleNeighbors() {
    Graph graph = visibleGraph(D2DrlgRoomStatus.COUNT);
    graph.sourceLevel.setActive(0);
    graph.visibleLevel.setActive(0);
    D2ActiveRoom source = new D2ActiveRoom();
    source.setPDrlgRoom(graph.sourceRoom);
    D2ActiveRoom visible = new D2ActiveRoom();
    visible.setPDrlgRoom(graph.linkRoom);

    Dungeon.updateRoomLevelActivity(null, source);
    Dungeon.updateRoomLevelActivity(null, source);
    assertEquals(2, graph.sourceLevel.getActive());
    assertEquals(2, graph.visibleLevel.getActive());

    Dungeon.updateRoomLevelActivity(source, null);
    assertEquals(1, graph.sourceLevel.getActive());
    assertEquals(1, graph.visibleLevel.getActive());
    Dungeon.updateRoomLevelActivity(source, visible);
    assertEquals(1, graph.sourceLevel.getActive());
    assertEquals(1, graph.visibleLevel.getActive());
    Dungeon.updateRoomLevelActivity(visible, null);

    assertEquals(0, graph.sourceLevel.getActive());
    assertEquals(0, graph.visibleLevel.getActive());
    assertEquals(10, graph.sourceLevel.getInactiveFrames());
    assertEquals(10, graph.visibleLevel.getInactiveFrames());
  }

  private static Graph visibleGraph(D2DrlgRoomStatus linkStatus) {
    Graph graph = new Graph();
    graph.drlg = new D2DrlgStrc();
    graph.sourceLevel = level(graph.drlg, 1);
    graph.visibleLevel = level(graph.drlg, 2);
    graph.sourceLevel.setPNextLevel(graph.visibleLevel);
    graph.drlg.setLevel(graph.sourceLevel);

    graph.sourceRoom = room(graph.sourceLevel, D2DrlgRoomStatus.COUNT, 0);
    graph.sourceLevel.setFirstRoomEx(graph.sourceRoom);
    graph.sourceLevel.setRooms(1);

    graph.linkRoom = room(graph.visibleLevel, linkStatus, D2DrlgRoomFlags.HAS_WARP_0);
    graph.linkRoom.setRoomTiles(new D2RoomTile());
    graph.linkRoom.setPpRoomsNear(new D2DrlgRoom[] {graph.sourceRoom});
    graph.linkRoom.setNRoomsNear(1);
    graph.visibleLevel.setFirstRoomEx(graph.linkRoom);
    graph.visibleLevel.setRooms(1);
    graph.visibleLevel.setActive(1);

    D2DrlgWarp sourceWarp = warp(1, 2);
    D2DrlgWarp visibleWarp = warp(2, 1);
    sourceWarp.setPNext(visibleWarp);
    graph.drlg.setWarp(sourceWarp);
    return graph;
  }

  private static D2DrlgLevel level(D2DrlgStrc drlg, int id) {
    D2DrlgLevel level = new D2DrlgLevel();
    level.setDrlg(drlg);
    level.setLevelId(id);
    level.setDrlgType(-1);
    return level;
  }

  private static D2DrlgRoom room(
      D2DrlgLevel level, D2DrlgRoomStatus status, int flags) {
    D2DrlgRoom room = new D2DrlgRoom();
    room.setLevel(level);
    room.setRoomStatus(status);
    room.setFlags(flags);
    return room;
  }

  private static D2DrlgWarp warp(int levelId, int visibleLevelId) {
    D2DrlgWarp warp = new D2DrlgWarp();
    warp.setNLevel(levelId);
    warp.getNVis()[0] = visibleLevelId;
    return warp;
  }

  private static final class Graph {
    D2DrlgStrc drlg;
    D2DrlgLevel sourceLevel;
    D2DrlgLevel visibleLevel;
    D2DrlgRoom sourceRoom;
    D2DrlgRoom linkRoom;
  }
}
