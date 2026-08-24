package com.d2moo.common.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2Coord;
import com.d2moo.common.drlg.D2DrlgPresetRoomStrc;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgTypes;

import org.junit.jupiter.api.Test;

class DungeonPresetMetadataTest {
  @Test
  void exposesOnlyPresetTombStoneCoordinatesAndCount() {
    D2Coord[] tombStones = {new D2Coord(12, 34), new D2Coord(56, 78)};
    D2DrlgPresetRoomStrc preset = new D2DrlgPresetRoomStrc();
    preset.setPTombStoneTiles(tombStones);
    preset.setNTombStoneTiles(2);
    D2DrlgRoom drlgRoom = new D2DrlgRoom();
    drlgRoom.setType(D2DrlgTypes.DRLGTYPE_PRESET);
    drlgRoom.setMazeOrOutdoor(preset);
    D2ActiveRoom room = new D2ActiveRoom();
    room.setPDrlgRoom(drlgRoom);
    int[] count = {-1};

    assertSame(tombStones, Dungeon.getTombStoneTileCoords(room, count));
    assertEquals(2, count[0]);

    drlgRoom.setType(D2DrlgTypes.DRLGTYPE_OUTDOOR);
    assertNull(Dungeon.getTombStoneTileCoords(room, count));
    assertEquals(0, count[0]);
    assertNull(Dungeon.getTombStoneTileCoords(null, count));
    assertEquals(0, count[0]);
  }
}
