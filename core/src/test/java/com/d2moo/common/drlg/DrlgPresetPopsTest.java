package com.d2moo.common.drlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.d2moo.common.d2cmp.D2TileData;
import com.d2moo.common.datatbls.D2LvlPrestTxt;

import org.junit.jupiter.api.Test;

class DrlgPresetPopsTest {
  @Test
  void collectsBurialGroundsTombStonesInSubtileCoordinates() {
    D2DrlgRoom room = presetRoom(new D2DrlgMapStrc(), 20, 30, 4, 3);
    D2DrlgLevel level = new D2DrlgLevel();
    level.setLevelId(D2LevelIds.LEVEL_BURIALGROUNDS);
    room.setLevel(level);
    D2DrlgPresetRoomStrc preset = (D2DrlgPresetRoomStrc) room.getMazeOrOutdoor();
    D2DrlgGridStrc walls = preset.getPWallGrid(0);
    DrlgDrlgGrid.initializeGridCells(null, walls, 4, 3);
    D2C_PackedTileInformation tombStone = new D2C_PackedTileInformation();
    tombStone.setNTileStyle(10);
    tombStone.setNTileSequence(23);
    DrlgDrlgGrid.alterGridFlag(walls, 2, 1, tombStone.getNPackedValue(),
        DrlgDrlgGrid.FlagOperation.OVERWRITE);

    DrlgPreset.collectTombStoneTileCoords(room);

    assertEquals(1, preset.getNTombStoneTiles());
    assertEquals(5 * 22 + 2, preset.getPTombStoneTiles()[0].getX());
    assertEquals(5 * 31 + 2, preset.getPTombStoneTiles()[0].getY());
  }

  @Test
  void scansDs1PopEndpointsIntoNativeAbsoluteBounds() {
    D2DrlgMapStrc map = new D2DrlgMapStrc();
    D2DrlgCoord mapCoords = coord(100, 200, 4, 3);
    map.setPDrlgCoord(mapCoords);
    D2LvlPrestTxt prest = new D2LvlPrestTxt();
    prest.setDwPops(1);
    map.setPLvlPrestTxtRecord(prest);

    D2DrlgFileStrc file = new D2DrlgFileStrc();
    file.setNWidth(4);
    file.setNHeight(3);
    file.setNWallLayers(1);
    int[] types = new int[20];
    int[] walls = new int[20];
    types[1 * 5 + 1] = DrlgRoomTile.TILETYPE_WALL_LEFT_EXIT;
    types[2 * 5 + 3] = DrlgRoomTile.TILETYPE_WALL_RIGHT_EXIT;
    walls[1 * 5 + 1] = 8 << 20 | 4 << 8;
    walls[2 * 5 + 3] = 8 << 20 | 4 << 8;
    file.setPTileTypeLayer(0, types);
    file.setPWallLayer(0, walls);
    map.setPFile(file);

    DrlgPreset.initializePopsFromFile(map);

    assertEquals(1, map.getNPops());
    assertEquals(1, map.getPPopsIndex()[0]);
    assertEquals(4, map.getPPopsSubIndex()[0]);
    assertEquals(101, map.getPPopsLocation()[0].getNPosX());
    assertEquals(201, map.getPPopsLocation()[0].getNPosY());
    assertEquals(3, map.getPPopsLocation()[0].getNWidth());
    assertEquals(2, map.getPPopsLocation()[0].getNHeight());
  }

  @Test
  void updatesSelectedPopOrientationAndWallTransitionState() {
    D2DrlgMapStrc map = new D2DrlgMapStrc();
    D2LvlPrestTxt prest = new D2LvlPrestTxt();
    prest.setDwPopPad(0);
    map.setPLvlPrestTxtRecord(prest);
    map.setNPops(1);
    map.setPPopsIndex(new int[] {1});
    map.setPPopsSubIndex(new int[] {4});
    map.setPPopsOrientation(new int[] {0});
    map.setPPopsLocation(new D2DrlgCoord[] {coord(10, 20, 2, 2)});

    D2DrlgRoom room = presetRoom(map, 8, 18, 8, 8);
    room.setPpRoomsNear(new D2DrlgRoom[] {room});
    room.setNRoomsNear(1);
    room.setRoom(new D2ActiveRoom());
    D2TileData tileEntry = new D2TileData();
    tileEntry.setNTileId(4);
    D2DrlgTileDataStrc wall = new D2DrlgTileDataStrc();
    wall.setNPosX(2);
    wall.setNPosY(2);
    wall.setPTile(tileEntry);
    D2DrlgRoomTilesStrc tiles = new D2DrlgRoomTilesStrc();
    tiles.setPWallTiles(new D2DrlgTileDataStrc[] {wall});
    D2DrlgTileGrid tileGrid = new D2DrlgTileGrid();
    tileGrid.setPTiles(tiles);
    room.setTileGrid(tileGrid);

    DrlgPreset.updatePops(room, 51, 101, false, 12345);

    assertEquals(12345, map.getPPopsOrientation()[0]);
    assertNotEquals(0, wall.getUnk0x24() & 2);
    assertEquals(255, wall.getNGreen() & 0xFF);
    assertEquals(0, wall.getNBlue() & 0xFF);
  }

  private static D2DrlgRoom presetRoom(
      D2DrlgMapStrc map, int x, int y, int width, int height) {
    D2DrlgPresetRoomStrc preset = new D2DrlgPresetRoomStrc();
    preset.setPMap(map);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setType(D2DrlgTypes.DRLGTYPE_PRESET);
    room.setMazeOrOutdoor(preset);
    room.setDrlgCoord(coord(x, y, width, height));
    return room;
  }

  private static D2DrlgCoord coord(int x, int y, int width, int height) {
    D2DrlgCoord coord = new D2DrlgCoord();
    coord.setNPosX(x);
    coord.setNPosY(y);
    coord.setNWidth(width);
    coord.setNHeight(height);
    return coord;
  }
}
