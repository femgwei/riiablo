package com.d2moo.common.drlg;

import com.d2moo.common.d2cmp.D2TileData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DrlgLinkedFloorTest {
  @Test void adjoiningRoomDoesNotRerollAnAlreadySelectedOrdinaryFloor() {
    D2DrlgRoom incoming = new D2DrlgRoom(), owner = new D2DrlgRoom();
    D2TileData selected = new D2TileData();
    selected.setNTileId(7);
    selected.setNSequence(2);
    D2DrlgTileDataStrc floor = new D2DrlgTileDataStrc();
    floor.setNTileType(DrlgRoomTile.TILETYPE_FLOOR);
    floor.setPTile(selected);
    DrlgRoomTile.linkedTileDataManager(null, incoming, owner, floor,
        DrlgRoomTile.TILETYPE_FLOOR, 0, 1, 1);
    assertSame(selected, floor.getPTile(), "D2MOO only substitutes style 30/sequence 0 floors");
  }
}
