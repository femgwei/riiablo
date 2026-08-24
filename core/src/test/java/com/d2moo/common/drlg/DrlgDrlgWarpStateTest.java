package com.d2moo.common.drlg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.d2moo.common.dungeon.Dungeon;
import org.junit.jupiter.api.Test;

class DrlgDrlgWarpStateTest {
  private static final int MAPTILE_HIDDEN = 0x000008;

  @Test
  void selectAndDeselectToggleEveryTileInMatchingWarpChains() {
    D2DrlgTileDataStrc inactiveTail = tile(MAPTILE_HIDDEN | 0x40);
    D2DrlgTileDataStrc inactiveHead = tile(MAPTILE_HIDDEN | 0x20, inactiveTail);
    D2DrlgTileDataStrc activeTail = tile(0x80);
    D2DrlgTileDataStrc activeHead = tile(0x100, activeTail);
    D2RoomTile matching = warp(7, inactiveHead, activeHead);

    D2DrlgTileDataStrc otherInactive = tile(MAPTILE_HIDDEN | 0x200);
    D2DrlgTileDataStrc otherActive = tile(0x400);
    D2RoomTile other = warp(8, otherInactive, otherActive);
    matching.setPNext(other);

    D2DrlgRoom room = new D2DrlgRoom();
    room.setRoomTiles(matching);

    DrlgDrlgWarp.updateWarpRoomSelect(room, 7);

    assertFalse(hidden(inactiveHead));
    assertFalse(hidden(inactiveTail));
    assertTrue(hidden(activeHead));
    assertTrue(hidden(activeTail));
    assertEquals(0x20, inactiveHead.getDwFlags());
    assertEquals(0x40, inactiveTail.getDwFlags());
    assertEquals(MAPTILE_HIDDEN | 0x100, activeHead.getDwFlags());
    assertEquals(MAPTILE_HIDDEN | 0x80, activeTail.getDwFlags());
    assertEquals(MAPTILE_HIDDEN | 0x200, otherInactive.getDwFlags());
    assertEquals(0x400, otherActive.getDwFlags());

    DrlgDrlgWarp.updateWarpRoomDeselect(room, 7);

    assertTrue(hidden(inactiveHead));
    assertTrue(hidden(inactiveTail));
    assertFalse(hidden(activeHead));
    assertFalse(hidden(activeTail));
  }

  @Test
  void nativeGuardLeavesOppositeChainUntouchedWhenRequiredChainIsMissing() {
    D2DrlgTileDataStrc active = tile(0);
    D2RoomTile noInactive = warp(3, null, active);
    D2DrlgRoom selectRoom = new D2DrlgRoom();
    selectRoom.setRoomTiles(noInactive);

    DrlgDrlgWarp.updateWarpRoomSelect(selectRoom, 3);
    assertFalse(hidden(active));

    D2DrlgTileDataStrc inactive = tile(0);
    D2RoomTile noActive = warp(3, inactive, null);
    D2DrlgRoom deselectRoom = new D2DrlgRoom();
    deselectRoom.setRoomTiles(noActive);

    DrlgDrlgWarp.updateWarpRoomDeselect(deselectRoom, 3);
    assertFalse(hidden(inactive));
  }

  @Test
  void dungeonFacadeUpdatesWarpStateAndTileEnableFlags() {
    D2DrlgTileDataStrc inactive = tile(MAPTILE_HIDDEN);
    D2DrlgTileDataStrc active = tile(0);
    D2RoomTile roomTile = warp(12, inactive, active);
    roomTile.setBEnabled(true);
    D2DrlgRoom drlgRoom = new D2DrlgRoom();
    drlgRoom.setRoomTiles(roomTile);
    D2ActiveRoom room = new D2ActiveRoom();
    room.setPDrlgRoom(drlgRoom);

    Dungeon.updateWarpRoomSelect(room, 12);
    assertFalse(hidden(inactive));
    assertTrue(hidden(active));

    Dungeon.updateWarpRoomDeselect(room, 12);
    assertTrue(hidden(inactive));
    assertFalse(hidden(active));

    Dungeon.toggleRoomTilesEnableFlag(room, false);
    assertFalse(roomTile.isBEnabled());
  }

  @Test
  void findsOnlyEnabledWarpRecordForClassId() {
    D2RoomTile enabled = warp(21, null, null);
    enabled.setBEnabled(true);
    D2RoomTile disabled = warp(20, null, null);
    disabled.setBEnabled(false);
    disabled.setPNext(enabled);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setRoomTiles(disabled);

    assertNull(DrlgDrlgWarp.getLvlWarpTxtRecordFromClassId(room, 20));
    assertSame(enabled.getPLvlWarpTxtRecord(),
        DrlgDrlgWarp.getLvlWarpTxtRecordFromClassId(room, 21));
    assertNull(DrlgDrlgWarp.getLvlWarpTxtRecordFromClassId(room, -1));
  }

  @Test
  void missingWarpDefinitionUsesNativeMinusOneSentinels() {
    assertArrayEquals(new int[] {-1, -1, -1, -1, -1, -1, -1, -1},
        DrlgDrlgWarp.getWarpIdArrayFromLevelId(new D2DrlgStrc(), Integer.MAX_VALUE));
  }

  private static D2RoomTile warp(int levelId, D2DrlgTileDataStrc inactive,
      D2DrlgTileDataStrc active) {
    D2LvlWarpTxt record = new D2LvlWarpTxt();
    record.setDwLevelId(levelId);
    D2RoomTile warp = new D2RoomTile();
    warp.setPLvlWarpTxtRecord(record);
    warp.setUnk0x0C(inactive);
    warp.setUnk0x10(active);
    return warp;
  }

  private static D2DrlgTileDataStrc tile(int flags) {
    return tile(flags, null);
  }

  private static D2DrlgTileDataStrc tile(int flags, D2DrlgTileDataStrc next) {
    D2DrlgTileDataStrc tile = new D2DrlgTileDataStrc();
    tile.setDwFlags(flags);
    tile.setUnk0x20(next);
    return tile;
  }

  private static boolean hidden(D2DrlgTileDataStrc tile) {
    return (tile.getDwFlags() & MAPTILE_HIDDEN) != 0;
  }
}
