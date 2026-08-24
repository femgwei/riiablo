package com.d2moo.common.dungeon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgOutdoorRoomStrc;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgRoomFlags;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2DrlgTypes;

import org.junit.jupiter.api.Test;

class DungeonLevelMetadataTest {
  @Test
  void exposesActEnvironmentAndStaffTombMetadata() {
    Object environment = new Object();
    D2DrlgStrc drlg = new D2DrlgStrc();
    drlg.setStaffTombLevel(66);
    D2DrlgAct act = new D2DrlgAct();
    act.setDrlg(drlg);
    act.setEnvironment(environment);

    assertSame(environment, Dungeon.getEnvironmentFromAct(act));
    assertEquals(66, Dungeon.getHoradricStaffTombLevelId(act));
    assertNull(Dungeon.getEnvironmentFromAct(null));
    assertEquals(0, Dungeon.getHoradricStaffTombLevelId(null));
  }

  @Test
  void countsOnlyRoomsWithoutPopulationZeroFlag() {
    D2DrlgRoom populated = new D2DrlgRoom();
    D2DrlgRoom empty = new D2DrlgRoom();
    D2DrlgRoom populatedToo = new D2DrlgRoom();
    empty.setFlags(D2DrlgRoomFlags.POPULATION_ZERO);
    populated.setDrlgRoomNext(empty);
    empty.setDrlgRoomNext(populatedToo);

    D2DrlgLevel level = new D2DrlgLevel();
    level.setLevelId(8);
    level.setFirstRoomEx(populated);
    D2DrlgStrc drlg = new D2DrlgStrc();
    drlg.setLevel(level);
    D2DrlgAct act = new D2DrlgAct();
    act.setDrlg(drlg);

    assertEquals(2, Dungeon.getNumberOfPopulatedRoomsInLevel(act, 8));
    assertEquals(0, Dungeon.getNumberOfPopulatedRoomsInLevel(null, 8));
  }

  @Test
  void flattensNativeWarpCoordinateMemoryLayoutAsAValueCopy() {
    D2DrlgLevel level = new D2DrlgLevel();
    level.setNRoomCenterWarpX(new int[] {10, 20, 30, 0, 0, 0, 0, 0, 0});
    level.setNRoomCenterWarpY(new int[] {40, 50, 60, 0, 0, 0, 0, 0, 0});
    level.setNRoomCoords(3);
    D2DrlgRoom drlgRoom = new D2DrlgRoom();
    drlgRoom.setLevel(level);
    D2ActiveRoom room = new D2ActiveRoom();
    room.setPDrlgRoom(drlgRoom);

    int[] expected = {
        10, 20, 30, 0, 0, 0, 0, 0, 0,
        40, 50, 60, 0, 0, 0, 0, 0, 0,
        3
    };
    int[] coordinates = Dungeon.getWarpCoordinatesFromRoom(room);
    assertArrayEquals(expected, coordinates);
    coordinates[0] = 999;
    assertEquals(10, level.getNRoomCenterWarpX()[0]);
    assertNull(Dungeon.getWarpCoordinatesFromRoom(null));
  }

  @Test
  void preservesNativeOutdoorFlag80TypeCheckAndRawValue() {
    D2DrlgOutdoorRoomStrc outdoor = new D2DrlgOutdoorRoomStrc();
    outdoor.setDwFlags(0x180);
    D2DrlgRoom drlgRoom = new D2DrlgRoom();
    drlgRoom.setType(D2DrlgTypes.DRLGTYPE_MAZE);
    drlgRoom.setMazeOrOutdoor(outdoor);
    D2ActiveRoom room = new D2ActiveRoom();
    room.setPDrlgRoom(drlgRoom);

    assertEquals(0x80, Dungeon.getOutdoorRoomFlag80(room));
    drlgRoom.setType(D2DrlgTypes.DRLGTYPE_OUTDOOR);
    assertEquals(0, Dungeon.getOutdoorRoomFlag80(room),
        "D2Common 0x6FD779F0 explicitly checks DRLGTYPE_MAZE");
    assertEquals(0, Dungeon.getOutdoorRoomFlag80(null));
  }
}
