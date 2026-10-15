package com.d2moo.common.drlg;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.d2moo.common.seed.Seed;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Retail 1.10f regressions from D2MOO b5b0888 / 1c7d34c, without game assets. */
class DrlgUpstreamParityTest {
  @Test
  void riverBridgeSearchStartsOnAnInteriorRow() {
    D2DrlgLevel level = outdoors(8, 6);
    level.getOutdoors().setDwFlags(DrlgOutdoors.OUTDOOR_BRIDGE);
    D2DrlgGridStrc grid = level.getOutdoors().getPGrid(0);
    D2Seed seed = new D2Seed();
    level.setSeed(seed);
    // Low=2 / high=666 gives a zero roll: old code selects edge row 0.
    Seed.initLowSeed(seed, 2);
    assertEquals(0, Seed.rollLimitedRandomNumber(seed, 4));
    Seed.initLowSeed(seed, 2);
    try (MockedStatic<DrlgPreset> presets = unitPresets()) {
      DrlgOutWild.spawnRiver(level, 2);
    }
    assertEquals(D2LvlPrestIds.LVLPREST_ACT1_BRIDGE,
        DrlgDrlgGrid.getGridEntry(grid, 2, 1));
    assertNotEquals(D2LvlPrestIds.LVLPREST_ACT1_BRIDGE,
        DrlgDrlgGrid.getGridEntry(grid, 2, 0));
  }

  @Test
  void barricadeHorizontalEdgesScanWidthOnANonSquareGrid() {
    D2DrlgLevel level = outdoors(12, 6);
    level.setLevelId(D2LevelIds.LEVEL_BLOODYFOOTHILLS);
    put(level.getOutdoors().getPGrid(2), 8, 0, 0x400);
    put(level.getOutdoors().getPGrid(2), 8, 4, 0x400);
    try (MockedStatic<DrlgPreset> presets = unitPresets()) {
      DrlgOutSiege.placeBarricadeEntrancesAndExits(level);
    }
    assertEquals(D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_EXIT_32X16,
        DrlgDrlgGrid.getGridEntry(level.getOutdoors().getPGrid(0), 8, 0));
    assertEquals(D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_ENTRANCE_32X16,
        DrlgDrlgGrid.getGridEntry(level.getOutdoors().getPGrid(0), 8, 4));
    assertEquals(1, DrlgOutdoors.getPackedGrid2Info(level.getOutdoors(), 8, 0).getNPickedFile(),
        "Bloody Foothills uses file 1, not 2");
  }

  @Test
  void dirtPathWritesTheRoomsLastFloorColumn() {
    D2DrlgLevel level = outdoors(8, 8);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setLevel(level);
    room.setNTileWidth(4);
    room.setNTileHeight(4);
    D2DrlgOutdoorRoomStrc data = new D2DrlgOutdoorRoomStrc();
    room.setMazeOrOutdoor(data);
    DrlgDrlgGrid.initializeGridCells(null, data.getPFloorGrid(), 5, 5);
    D2DrlgVertexStrc start = new D2DrlgVertexStrc(), end = new D2DrlgVertexStrc();
    start.setNPosX(4);
    start.setNPosY(0);
    end.setNPosX(4);
    end.setNPosY(4);
    start.setPNext(end);
    level.getOutdoors().setNVertices(1);
    level.getOutdoors().setPPathStarts(0, start);
    DrlgOutdoors.generateDirtPath(level, room);
    assertNotEquals(0, DrlgDrlgGrid.getGridEntry(data.getPDirtPathGrid(), 5, 2));
    assertNotEquals(0, DrlgDrlgGrid.getGridEntry(data.getPFloorGrid(), 4, 1));
  }

  @Test
  void wallsNeverJoinAnExistingFloorLink() {
    D2DrlgRoom room = new D2DrlgRoom();
    D2DrlgTileGrid grid = new D2DrlgTileGrid();
    room.setTileGrid(grid);
    D2DrlgTileDataStrc wall = new D2DrlgTileDataStrc();
    grid.getPTiles().setPWallTiles(new D2DrlgTileDataStrc[] {wall});
    D2DrlgTileLinkStrc floors = new D2DrlgTileLinkStrc();
    floors.setBFloor(true);
    D2DrlgTileDataStrc floor = new D2DrlgTileDataStrc();
    floors.setPMapTile(floor);
    grid.setPMapLinks(floors);
    DrlgRoomTile.addLinkedTileData(null, room, DrlgRoomTile.TILETYPE_WALL_LEFT, 0, 0, 0);
    assertSame(floor, floors.getPMapTile());
    assertFalse(grid.getPMapLinks().isBFloor());
    assertSame(wall, grid.getPMapLinks().getPMapTile());
    assertSame(floors, grid.getPMapLinks().getPNext());
  }

  @Test
  void referencedStatusDoesNotRunInitializationAgain() {
    D2DrlgRoom near = spy(new D2DrlgRoom());
    near.setRoomStatus(D2DrlgRoomStatus.UNTILE);
    near.getRoomsInList()[D2DrlgRoomStatus.UNTILE.getValue()] = 1;
    near.setFlags(D2DrlgRoomFlags.TILELIB_LOADED);
    near.setType(-1);
    D2DrlgRoom source = new D2DrlgRoom();
    source.setNRoomsNear(1);
    source.setPpRoomsNear(new D2DrlgRoom[] {near});
    clearInvocations(near);
    DrlgActivate.roomExPropagateSetStatus(null, source, (byte) D2DrlgRoomStatus.UNTILE.getValue());
    assertEquals(2, near.getRoomsInList()[D2DrlgRoomStatus.UNTILE.getValue()]);
    verify(near, never()).getFlags(); // UNTILE callback reads flags even if already loaded.
  }

  @Test
  void unreferencedStatusDoesRunInitialization() {
    D2DrlgRoom near = spy(new D2DrlgRoom());
    D2DrlgStrc drlg = new D2DrlgStrc();
    DrlgActivate.initializeRoomExStatusLists(drlg);
    D2DrlgLevel level = new D2DrlgLevel();
    level.setDrlg(drlg);
    near.setLevel(level);
    near.setRoomStatus(D2DrlgRoomStatus.COUNT);
    near.setFlags(D2DrlgRoomFlags.TILELIB_LOADED);
    near.setType(-1);
    D2DrlgRoom source = new D2DrlgRoom();
    source.setNRoomsNear(1);
    source.setPpRoomsNear(new D2DrlgRoom[] {near});
    DrlgActivate.roomExPropagateSetStatus(null, source, (byte) D2DrlgRoomStatus.UNTILE.getValue());
    assertEquals(D2DrlgRoomStatus.UNTILE, near.getRoomStatus());
    assertEquals(1, near.getRoomsInList()[D2DrlgRoomStatus.UNTILE.getValue()]);
    verify(near, atLeastOnce()).getFlags();
    near.getRoomsInList()[D2DrlgRoomStatus.UNTILE.getValue()] = 0;
    DrlgActivate.roomExIdentifyRealStatus(near);
    assertEquals(D2DrlgRoomStatus.COUNT, near.getRoomStatus());
    assertNull(near.getStatusNext());
  }

  private static D2DrlgLevel outdoors(int width, int height) {
    D2DrlgLevel level = new D2DrlgLevel();
    level.setDrlg(new D2DrlgStrc());
    D2DrlgOutdoorInfoStrc data = new D2DrlgOutdoorInfoStrc();
    level.setOutdoors(data);
    data.setNGridWidth(width);
    data.setNGridHeight(height);
    for (int i = 0; i < 3; i++) DrlgDrlgGrid.initializeGridCells(null, data.getPGrid(i), width, height);
    return level;
  }

  private static void put(D2DrlgGridStrc grid, int x, int y, int flags) {
    DrlgDrlgGrid.alterGridFlag(grid, x, y, flags, DrlgDrlgGrid.FlagOperation.OVERWRITE);
  }

  private static MockedStatic<DrlgPreset> unitPresets() {
    MockedStatic<DrlgPreset> presets = mockStatic(DrlgPreset.class);
    presets.when(() -> DrlgPreset.getSizeX(anyInt())).thenReturn(8);
    presets.when(() -> DrlgPreset.getSizeY(anyInt())).thenReturn(8);
    return presets;
  }
}
