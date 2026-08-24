package com.d2moo.common.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.d2moo.common.datatbls.D2LevelDefBin;
import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgGridStrc;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgRoomFlags;
import com.d2moo.common.drlg.D2DrlgRoomTilesStrc;
import com.d2moo.common.drlg.D2DrlgTileDataStrc;
import com.d2moo.common.drlg.D2PresetUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DungeonRoomContentTest {
  @AfterEach
  void clearTables() {
    DataTbls.unloadLevelDefsBin();
  }

  @Test
  void returnsNativeTileArraysAndExplicitPopulatedCounts() {
    D2DrlgTileDataStrc[] floors = tiles(3);
    D2DrlgTileDataStrc[] walls = tiles(2);
    D2DrlgTileDataStrc[] roofs = tiles(1);
    D2DrlgRoomTilesStrc roomTiles = new D2DrlgRoomTilesStrc();
    roomTiles.setPFloorTiles(floors);
    roomTiles.setPWallTiles(walls);
    roomTiles.setPRoofTiles(roofs);
    roomTiles.setNFloors(2);
    roomTiles.setNWalls(1);
    roomTiles.setNRoofs(1);
    D2ActiveRoom room = new D2ActiveRoom();
    room.setPRoomTiles(roomTiles);

    int[] count = {-1};
    assertSame(floors, Dungeon.getFloorTilesFromRoom(room, count));
    assertEquals(2, count[0]);
    assertSame(walls, Dungeon.getWallTilesFromRoom(room, count));
    assertEquals(1, count[0]);
    assertSame(roofs, Dungeon.getRoofTilesFromRoom(room, count));
    assertEquals(1, count[0]);

    assertNull(Dungeon.getFloorTilesFromRoom(null, count));
    assertEquals(0, count[0]);
    assertNull(Dungeon.getWallTilesFromRoom(new D2ActiveRoom(), count));
    assertEquals(0, count[0]);
  }

  @Test
  void exposesEmbeddedActTileCacheAndMemoryPool() {
    D2DrlgAct act = new D2DrlgAct();
    Object memoryPool = new Object();
    act.setPMemPool(memoryPool);

    D2DrlgTileDataStrc tileData = Dungeon.getTileDataFromAct(act);
    assertSame(tileData, act.getTileData());
    assertSame(memoryPool, Dungeon.getMemPoolFromAct(act));
    assertNull(Dungeon.getTileDataFromAct(null));
    assertNull(Dungeon.getMemPoolFromAct(null));

    act.setTileData(null);
    assertTrue(act.getTileData() != null, "native pTileData is embedded, never null");
  }

  @Test
  void preservesNativePresetUnitOneShotSemantics() {
    D2PresetUnit preset = new D2PresetUnit();
    D2DrlgRoom drlgRoom = new D2DrlgRoom();
    drlgRoom.setPresetUnits(preset);
    D2ActiveRoom room = new D2ActiveRoom();
    room.setPDrlgRoom(drlgRoom);

    assertSame(preset, Dungeon.getPresetUnitsFromRoom(room));
    assertTrue((drlgRoom.getFlags() & D2DrlgRoomFlags.PRESET_UNITS_SPAWNED) != 0);
    assertNull(Dungeon.getPresetUnitsFromRoom(room));

    drlgRoom.setFlags(D2DrlgRoomFlags.AUTOMAP_REVEAL);
    assertSame(preset, Dungeon.getPresetUnitsFromRoom(room));
    assertSame(preset, Dungeon.getPresetUnitsFromRoom(room));
  }

  @Test
  void storesAndReturnsCollisionGridByIdentity() {
    D2ActiveRoom room = new D2ActiveRoom();
    D2DrlgGridStrc collision = new D2DrlgGridStrc(8, 6);

    assertNull(Dungeon.getCollisionGridFromRoom(room));
    Dungeon.setCollisionGridInRoom(room, collision);
    assertSame(collision, Dungeon.getCollisionGridFromRoom(room));
    Dungeon.setCollisionGridInRoom(room, null);
    assertNull(Dungeon.getCollisionGridFromRoom(room));
  }

  @Test
  void readsUnsignedRoomLightingFromLevelDefinition() {
    D2LevelDefBin levelDef = new D2LevelDefBin();
    levelDef.setDwLevelId(33);
    levelDef.setNIntensity((byte) 200);
    levelDef.setNRed((byte) 10);
    levelDef.setNGreen((byte) 20);
    levelDef.setNBlue((byte) 30);
    DataTbls.setLevelDefBinCache(new D2LevelDefBin[] {levelDef});
    D2DrlgLevel level = new D2DrlgLevel();
    level.setLevelId(33);
    D2DrlgRoom drlgRoom = new D2DrlgRoom();
    drlgRoom.setLevel(level);
    D2ActiveRoom room = new D2ActiveRoom();
    room.setPDrlgRoom(drlgRoom);
    byte[] intensity = {(byte) 0xFF};
    byte[] red = {(byte) 0xFF};
    byte[] green = {(byte) 0xFF};
    byte[] blue = {(byte) 0xFF};

    Dungeon.getRGBIntensityFromRoom(room, intensity, red, green, blue);

    assertEquals(200, Byte.toUnsignedInt(intensity[0]));
    assertEquals(10, Byte.toUnsignedInt(red[0]));
    assertEquals(20, Byte.toUnsignedInt(green[0]));
    assertEquals(30, Byte.toUnsignedInt(blue[0]));

    DataTbls.unloadLevelDefsBin();
    Dungeon.getRGBIntensityFromRoom(room, intensity, red, green, blue);
    assertEquals(0, intensity[0]);
    assertEquals(0, red[0]);
    assertEquals(0, green[0]);
    assertEquals(0, blue[0]);
  }

  private static D2DrlgTileDataStrc[] tiles(int count) {
    D2DrlgTileDataStrc[] tiles = new D2DrlgTileDataStrc[count];
    for (int i = 0; i < count; i++) {
      tiles[i] = new D2DrlgTileDataStrc();
    }
    return tiles;
  }
}
