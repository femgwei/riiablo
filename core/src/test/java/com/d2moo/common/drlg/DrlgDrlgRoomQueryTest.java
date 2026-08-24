package com.d2moo.common.drlg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.d2moo.common.datatbls.D2LvlPrestTxt;
import com.d2moo.common.dungeon.Dungeon;
import org.junit.jupiter.api.Test;

class DrlgDrlgRoomQueryTest {
  @Test
  void compactsOnlyActiveNearRoomsAndClearsUnusedCapacity() {
    D2DrlgRoom firstDrlg = new D2DrlgRoom();
    D2ActiveRoom first = new D2ActiveRoom();
    first.setPDrlgRoom(firstDrlg);
    D2DrlgRoom inactive = new D2DrlgRoom();
    D2DrlgRoom secondDrlg = new D2DrlgRoom();
    D2ActiveRoom second = new D2ActiveRoom();
    second.setPDrlgRoom(secondDrlg);

    D2DrlgRoom owner = new D2DrlgRoom();
    owner.setPpRoomsNear(new D2DrlgRoom[] {firstDrlg, inactive, null, secondDrlg});
    owner.setNRoomsNear(4);
    D2ActiveRoom stale = new D2ActiveRoom();
    D2ActiveRoom[] result = {stale, stale, stale, stale};

    int count = DrlgDrlgRoom.reorderNearRoomList(owner, result);

    assertEquals(2, count);
    assertArrayEquals(new D2ActiveRoom[] {first, second, null, null}, result);
  }

  @Test
  void reportsNativeLosPopulationAndWaypointRoomState() {
    D2DrlgLevel level = level(17);
    D2DrlgRoom room = room(level);
    D2ActiveRoom active = new D2ActiveRoom();
    active.setPDrlgRoom(room);

    room.setType(D2DrlgTypes.DRLGTYPE_MAZE);
    assertTrue(DrlgDrlgRoom.checkLOSDraw(room));
    assertTrue(Dungeon.checkLOSDraw(active));

    room.setType(D2DrlgTypes.DRLGTYPE_PRESET);
    room.setFlags(0);
    assertFalse(DrlgDrlgRoom.checkLOSDraw(room));
    room.setFlags(D2DrlgRoomFlags.NO_LOS_DRAW);
    assertTrue(DrlgDrlgRoom.checkLOSDraw(room));

    room.setType(D2DrlgTypes.DRLGTYPE_OUTDOOR);
    assertFalse(DrlgDrlgRoom.checkLOSDraw(room));

    room.setFlags(D2DrlgRoomFlags.HAS_WAYPOINT);
    assertTrue(Dungeon.hasWaypoint(active));
    assertEquals(17, Dungeon.getLevelIdFromPopulatedRoom(active));
    room.setFlags(room.getFlags() | D2DrlgRoomFlags.POPULATION_ZERO);
    assertEquals(0, Dungeon.getLevelIdFromPopulatedRoom(active));
    assertEquals(0, Dungeon.getLevelIdFromPopulatedRoom(null));
  }

  @Test
  void returnsPickedPresetFilePathInsteadOfPlaceholder() {
    D2LvlPrestTxt record = new D2LvlPrestTxt();
    record.setSzFile(2, "data\\global\\tiles\\test.ds1");
    D2DrlgMapStrc map = new D2DrlgMapStrc();
    map.setPLvlPrestTxtRecord(record);
    map.setNPickedFile(2);
    D2DrlgPresetRoomStrc preset = new D2DrlgPresetRoomStrc();
    preset.setPMap(map);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setType(D2DrlgTypes.DRLGTYPE_PRESET);
    room.setMazeOrOutdoor(preset);
    D2ActiveRoom active = new D2ActiveRoom();
    active.setPDrlgRoom(room);

    assertEquals("data\\global\\tiles\\test.ds1",
        DrlgDrlgRoom.getPickedLevelPrestFilePathFromRoomEx(room));
    assertEquals("data\\global\\tiles\\test.ds1",
        Dungeon.getPickedLevelPrestFilePathFromRoom(active));

    room.setType(D2DrlgTypes.DRLGTYPE_OUTDOOR);
    assertEquals("None", DrlgDrlgRoom.getPickedLevelPrestFilePathFromRoomEx(room));
    assertNull(Dungeon.getPickedLevelPrestFilePathFromRoom(null));
  }

  @Test
  void resolvesWarpDestinationThroughPairedRoomTiles() {
    D2DrlgRoom source = room(level(4));
    D2DrlgRoom destination = room(level(9));
    D2ActiveRoom sourceActive = new D2ActiveRoom();
    sourceActive.setPDrlgRoom(source);
    D2ActiveRoom destinationActive = new D2ActiveRoom();
    destinationActive.setPDrlgRoom(destination);

    D2RoomTile sourceTile = roomTile(4, destination);
    D2RoomTile destinationTile = roomTile(9, source);
    source.setRoomTiles(sourceTile);
    destination.setRoomTiles(destinationTile);

    assertEquals(9, DrlgDrlgRoom.getWarpDestinationLevel(source, 4));
    assertEquals(9, Dungeon.getWarpDestinationLevel(sourceActive, 4));
    assertEquals(0, Dungeon.getWarpDestinationLevel(sourceActive, 99));
    assertSame(sourceActive, Dungeon.getRoomFromAct(actWithRoom(sourceActive)));
  }

  @Test
  void exposesActAndTownQueriesWithNativeZeroBasedActNumbers() {
    D2DrlgAct act = new D2DrlgAct();
    D2DrlgStrc drlg = new D2DrlgStrc();
    act.setDrlg(drlg);
    act.setInitSeed(0x12345678);
    act.setTownId(D2LevelIds.LEVEL_ROGUEENCAMPMENT);
    D2ActiveRoom townRoom = new D2ActiveRoom();
    townRoom.setPDrlgRoom(room(level(D2LevelIds.LEVEL_ROGUEENCAMPMENT)));
    act.setRoom(townRoom);

    assertSame(drlg, Dungeon.getDrlgFromAct(act));
    assertEquals(0x12345678, Dungeon.getInitSeedFromAct(act));
    assertEquals(D2LevelIds.LEVEL_ROGUEENCAMPMENT, Dungeon.getTownLevelIdFromAct(act));
    assertEquals(D2LevelIds.LEVEL_ROGUEENCAMPMENT,
        Dungeon.getTownLevelIdFromActNo(D2C_Acts.ACT_I));
    assertEquals(D2LevelIds.LEVEL_HARROGATH,
        Dungeon.getTownLevelIdFromActNo(D2C_Acts.ACT_V));
    assertTrue(Dungeon.isTownLevelId(D2LevelIds.LEVEL_ROGUEENCAMPMENT));
    assertTrue(Dungeon.isRoomInTown(townRoom));
    assertEquals(D2LevelIds.LEVEL_ROGUEENCAMPMENT,
        Dungeon.getLevelIdFromRoom(townRoom));
    assertThrows(IllegalArgumentException.class, () -> Dungeon.getTownLevelIdFromActNo(5));
    assertNull(Dungeon.getDrlgFromAct(null));
    assertEquals(0, Dungeon.getInitSeedFromAct(null));
  }

  private static D2DrlgAct actWithRoom(D2ActiveRoom room) {
    D2DrlgAct act = new D2DrlgAct();
    act.setRoom(room);
    return act;
  }

  private static D2DrlgLevel level(int levelId) {
    D2DrlgLevel level = new D2DrlgLevel();
    level.setLevelId(levelId);
    return level;
  }

  private static D2DrlgRoom room(D2DrlgLevel level) {
    D2DrlgRoom room = new D2DrlgRoom();
    room.setLevel(level);
    return room;
  }

  private static D2RoomTile roomTile(int levelId, D2DrlgRoom destination) {
    D2LvlWarpTxt record = new D2LvlWarpTxt();
    record.setDwLevelId(levelId);
    D2RoomTile tile = new D2RoomTile();
    tile.setPLvlWarpTxtRecord(record);
    tile.setPDrlgRoom(destination);
    return tile;
  }
}
