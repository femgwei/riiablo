package com.d2moo.common.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgFlags;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgRoomFlags;
import com.d2moo.common.drlg.D2DrlgRoomStatus;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2LevelIds;
import org.junit.jupiter.api.Test;

class DungeonRoomStateTest {
  @Test
  void portalAndRoomStatusControlServerUntileEligibility() {
    RoomGraph graph = roomGraph(2, D2DrlgRoomStatus.CLIENT_OUT_OF_SIGHT);

    assertTrue(Dungeon.testRoomCanUnTile(graph.act, graph.active));
    assertFalse(Dungeon.getRoomStatusFlags(graph.active));

    Dungeon.toggleHasPortalFlag(graph.active, false);
    assertTrue((graph.room.getFlags() & D2DrlgRoomFlags.HASPORTAL) != 0);
    assertFalse(Dungeon.testRoomCanUnTile(graph.act, graph.active));

    Dungeon.toggleHasPortalFlag(graph.active, true);
    assertFalse((graph.room.getFlags() & D2DrlgRoomFlags.HASPORTAL) != 0);
    assertTrue(Dungeon.testRoomCanUnTile(graph.act, graph.active));

    graph.room.setRoomStatus(D2DrlgRoomStatus.CLIENT_IN_SIGHT);
    assertFalse(Dungeon.testRoomCanUnTile(graph.act, graph.active));
    graph.room.setRoomStatus(D2DrlgRoomStatus.UNTILE);
    assertTrue(Dungeon.getRoomStatusFlags(graph.active));
    graph.room.setRoomStatus(D2DrlgRoomStatus.COUNT);
    assertTrue(Dungeon.getRoomStatusFlags(graph.active));

    graph.act.setClient(true);
    assertFalse(Dungeon.testRoomCanUnTile(graph.act, graph.active));
    graph.act.setClient(false);
    graph.drlg.setFlags(D2DrlgFlags.ONCLIENT);
    assertFalse(Dungeon.testRoomCanUnTile(graph.act, graph.active));
  }

  @Test
  void townRoomWaitsUntilEveryRoomLeavesClientSight() {
    RoomGraph graph = roomGraph(
        D2LevelIds.LEVEL_ROGUEENCAMPMENT, D2DrlgRoomStatus.CLIENT_OUT_OF_SIGHT);
    D2DrlgRoom sibling = new D2DrlgRoom();
    sibling.setLevel(graph.level);
    sibling.setRoomStatus(D2DrlgRoomStatus.CLIENT_IN_SIGHT);
    graph.room.setDrlgRoomNext(sibling);

    assertFalse(Dungeon.testRoomCanUnTile(graph.act, graph.active));
    sibling.setRoomStatus(D2DrlgRoomStatus.CLIENT_OUT_OF_SIGHT);
    assertTrue(Dungeon.testRoomCanUnTile(graph.act, graph.active));
  }

  @Test
  void exposesNativeActiveRoomFlagBitsWithoutDisturbingOtherFlags() {
    D2ActiveRoom room = new D2ActiveRoom();
    room.setDwFlags(0x40 | 0x4);

    assertFalse(Dungeon.getActiveRoomFlag2(room));
    assertTrue(Dungeon.getActiveRoomFlag4(room));
    Dungeon.setActiveRoomFlag2(room, true);
    assertTrue(Dungeon.getActiveRoomFlag2(room));
    assertEquals(0x46, room.getDwFlags());
    Dungeon.setActiveRoomFlag2(room, false);
    assertFalse(Dungeon.getActiveRoomFlag2(room));
    assertEquals(0x44, room.getDwFlags());
  }

  @Test
  void requiresEveryGeneratedNearRoomToCarryNativeFlagOne() {
    D2ActiveRoom first = new D2ActiveRoom();
    first.setDwFlags(0x1);
    D2ActiveRoom second = new D2ActiveRoom();
    second.setDwFlags(0x1);
    D2DrlgRoom ownerDrlg = new D2DrlgRoom();
    ownerDrlg.setNRoomsNear(2);
    D2ActiveRoom owner = new D2ActiveRoom();
    owner.setPDrlgRoom(ownerDrlg);
    owner.setPpRoomList(new D2ActiveRoom[] {first, second});
    owner.setNNumRooms(2);

    assertTrue(Dungeon.areAllNearRoomsFlagged(owner));
    second.setDwFlags(0);
    assertFalse(Dungeon.areAllNearRoomsFlagged(owner));
    second.setDwFlags(1);
    ownerDrlg.setNRoomsNear(3);
    assertFalse(Dungeon.areAllNearRoomsFlagged(owner));
  }

  @Test
  void tileInactivityCounterResetsWhileClientsRemain() {
    D2ActiveRoom room = new D2ActiveRoom();

    assertEquals(1, Dungeon.getTileCountFromRoom(room));
    assertEquals(2, Dungeon.getTileCountFromRoom(room));
    room.setNNumClients(1);
    assertEquals(0, Dungeon.getTileCountFromRoom(room));
    assertEquals(0, room.getNTileCount());
    room.setNNumClients(0);
    assertEquals(1, Dungeon.getTileCountFromRoom(room));
  }

  private static RoomGraph roomGraph(int levelId, D2DrlgRoomStatus status) {
    D2DrlgAct act = new D2DrlgAct();
    D2DrlgStrc drlg = new D2DrlgStrc();
    act.setDrlg(drlg);
    drlg.setAct(act);
    D2DrlgLevel level = new D2DrlgLevel();
    level.setLevelId(levelId);
    level.setDrlg(drlg);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setLevel(level);
    room.setRoomStatus(status);
    level.setFirstRoomEx(room);
    D2ActiveRoom active = new D2ActiveRoom();
    active.setPDrlgRoom(room);
    active.setAct(act);
    act.setRoom(active);
    return new RoomGraph(act, drlg, level, room, active);
  }

  private static final class RoomGraph {
    final D2DrlgAct act;
    final D2DrlgStrc drlg;
    final D2DrlgLevel level;
    final D2DrlgRoom room;
    final D2ActiveRoom active;

    RoomGraph(D2DrlgAct act, D2DrlgStrc drlg, D2DrlgLevel level,
        D2DrlgRoom room, D2ActiveRoom active) {
      this.act = act;
      this.drlg = drlg;
      this.level = level;
      this.room = room;
      this.active = active;
    }
  }
}
