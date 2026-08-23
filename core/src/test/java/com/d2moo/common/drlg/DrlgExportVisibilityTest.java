package com.d2moo.common.drlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DrlgExportVisibilityTest {
  @Test
  void collectsOnlyNativeInactiveWarpChainByIdentity() {
    D2DrlgTileDataStrc inactiveTail = new D2DrlgTileDataStrc();
    D2DrlgTileDataStrc inactiveHead = new D2DrlgTileDataStrc();
    inactiveHead.setUnk0x20(inactiveTail);
    D2DrlgTileDataStrc active = new D2DrlgTileDataStrc();
    active.setDwFlags(0x000008);

    D2RoomTile warp = new D2RoomTile();
    warp.setUnk0x0C(inactiveHead);
    warp.setUnk0x10(active);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setRoomTiles(warp);

    Set<D2DrlgTileDataStrc> inactive = DrlgExport.collectInactiveWarpTiles(room);

    assertEquals(2, inactive.size());
    assertTrue(inactive.contains(inactiveHead));
    assertTrue(inactive.contains(inactiveTail));
    assertFalse(inactive.contains(active));
  }

  @Test
  void preservesOrdinaryHiddenTilesOutsideInactiveWarpChain() {
    D2DrlgTileDataStrc ordinaryHidden = new D2DrlgTileDataStrc();
    ordinaryHidden.setDwFlags(0x000008);
    D2DrlgRoom room = new D2DrlgRoom();

    assertFalse(DrlgExport.collectInactiveWarpTiles(room).contains(ordinaryHidden));
  }
}
