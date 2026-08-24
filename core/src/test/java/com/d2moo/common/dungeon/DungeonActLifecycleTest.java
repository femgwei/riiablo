package com.d2moo.common.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;

import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgCoord;
import com.d2moo.common.drlg.D2DrlgCoords;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.environment.D2DrlgEnvironment;
import com.d2moo.common.environment.Environment;

import org.junit.jupiter.api.Test;

class DungeonActLifecycleTest {
  @Test
  void environmentAllocationUsesNativeNoonDefaults() {
    D2DrlgEnvironment environment = Environment.allocDrlgEnvironment(null);

    assertEquals(Environment.ENVCYCLE_NOON, environment.getCycleIndex());
    assertEquals(Environment.ENVPERIOD_DAY, environment.getPeriodOfDay());
    assertEquals(0, environment.getTicks());
    assertEquals(128, environment.getTimeRate());
    assertEquals(128, environment.getIntensity());
    assertEquals(255, environment.getRed());
    assertEquals(255, environment.getGreen());
    assertEquals(255, environment.getBlue());
    assertFalse(environment.isEclipse());
  }

  @Test
  void callbackRunsAfterNewRoomIsLinkedAndNotForExistingRoom() {
    D2DrlgAct act = new D2DrlgAct();
    D2DrlgRoom drlgRoom = room(3, 7, 4, 6);
    drlgRoom.setPpRoomsNear(new D2DrlgRoom[] {drlgRoom});
    drlgRoom.setNRoomsNear(1);
    AtomicInteger calls = new AtomicInteger();

    Dungeon.setActCallbackFunc(act, allocated -> {
      calls.incrementAndGet();
      assertSame(act, allocated.getAct());
      assertSame(allocated, act.getRoom());
      assertSame(allocated, allocated.getPpRoomList()[0]);
    });

    D2ActiveRoom allocated = Dungeon.allocRoom(
        act, drlgRoom, coords(3, 7, 4, 6), null, 123, 0);
    D2ActiveRoom existing = Dungeon.allocRoom(
        act, drlgRoom, coords(3, 7, 4, 6), null, 456, 0);

    assertSame(allocated, existing);
    assertEquals(1, calls.get());
  }

  @Test
  void freeActClearsRoomsEnvironmentCallbackAndPendingState() {
    D2DrlgAct act = new D2DrlgAct();
    act.setEnvironment(Environment.allocDrlgEnvironment(null));
    act.setHasPendingRoomsUpdates(true);
    act.setHasPendingRoomDeletions(true);
    act.setHasPendingUnitListUpdates(true);
    Dungeon.setActCallbackFunc(act, room -> {});

    D2DrlgRoom firstDrlg = room(0, 0, 5, 5);
    D2DrlgRoom secondDrlg = room(5, 0, 5, 5);
    firstDrlg.setPpRoomsNear(new D2DrlgRoom[] {firstDrlg, secondDrlg});
    firstDrlg.setNRoomsNear(2);
    secondDrlg.setPpRoomsNear(new D2DrlgRoom[] {firstDrlg, secondDrlg});
    secondDrlg.setNRoomsNear(2);
    D2ActiveRoom first = Dungeon.allocRoom(
        act, firstDrlg, coords(0, 0, 5, 5), null, 1, 0);
    D2ActiveRoom second = Dungeon.allocRoom(
        act, secondDrlg, coords(5, 0, 5, 5), null, 2, 0);

    Dungeon.freeAct(act);

    assertNull(act.getDrlg());
    assertNull(act.getRoom());
    assertNull(act.getEnvironment());
    assertNull(act.getPfnActCallBack());
    assertFalse(act.isHasPendingRoomsUpdates());
    assertFalse(act.isHasPendingRoomDeletions());
    assertFalse(act.isHasPendingUnitListUpdates());
    assertNull(first.getAct());
    assertNull(second.getAct());
    assertNull(first.getPDrlgRoom());
    assertNull(second.getPDrlgRoom());
    assertNull(firstDrlg.getRoom());
    assertNull(secondDrlg.getRoom());
  }

  private static D2DrlgRoom room(int x, int y, int width, int height) {
    D2DrlgCoord coord = new D2DrlgCoord();
    coord.setNTileXPos(x);
    coord.setNTileYPos(y);
    coord.setNTileWidth(width);
    coord.setNTileHeight(height);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setDrlgCoord(coord);
    return room;
  }

  private static D2DrlgCoords coords(int x, int y, int width, int height) {
    D2DrlgCoords coords = new D2DrlgCoords();
    coords.setNTileXPos(x);
    coords.setNTileYPos(y);
    coords.setNTileWidth(width);
    coords.setNTileHeight(height);
    coords.setNSubtileX(x * 5);
    coords.setNSubtileY(y * 5);
    coords.setNSubtileWidth(width * 5);
    coords.setNSubtileHeight(height * 5);
    return coords;
  }
}
