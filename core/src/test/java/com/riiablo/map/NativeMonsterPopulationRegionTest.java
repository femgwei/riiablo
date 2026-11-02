package com.riiablo.map;

import com.d2moo.common.drlg.D2DrlgCoord;
import com.d2moo.common.drlg.D2DrlgLogicalRoomInfo;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2RoomCoordListStrc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMonsterPopulationRegionTest {
  @Test void projectsTileEdgesAndSkipsNodesZeroIndicesAndEmptyBoxes() {
    NativeMonsterPopulationRegion[] regions = NativeMonsterPopulationRegion.capture(splitRoom(), 1640, -2280);
    assertEquals(1, regions.length);
    NativeMonsterPopulationRegion region = regions[0];
    assertEquals(7, region.index);
    assertEquals(1650, region.x);
    assertEquals(-2270, region.y);
    assertEquals(20, region.width);
    assertEquals(20, region.height);
    assertEquals(40, region.densityAttempts());
    assertTrue(region.contains(1654, -2266));
    assertFalse(region.contains(1655, -2265), "hole belongs to a different logical index");
    assertFalse(region.contains(1670, -2266), "right edge is exclusive");
    assertFalse(region.contains(1654, -2250), "bottom edge is exclusive");
  }

  @Test void snapshotDoesNotDependOnFreedOrMutatedNativeLists() {
    D2DrlgRoom room = splitRoom();
    NativeMonsterPopulationRegion region = NativeMonsterPopulationRegion.capture(room, 0, -40)[0];
    room.getLogicalRoomInfo().getPCoordList().setNIndex(999);
    room.getLogicalRoomInfo().getPCoordList().getPBox(1).setNWidth(0);
    room.setLogicalRoomInfo(null);
    assertTrue(region.contains(14, -26));
    assertFalse(region.contains(15, -25));
    assertEquals(20, region.width);
  }

  @Test void nativeRoomWithoutListsDoesNotFallBackToWholeRoom() {
    D2DrlgRoom room = splitRoom();
    room.setLogicalRoomInfo(null);
    assertEquals(0, NativeMonsterPopulationRegion.capture(room, 0, 0).length);
  }

  @Test void singleListRoomsRetainTheirWholeRoomAndNativeDensityBudget() {
    D2DrlgRoom room = splitRoom();
    D2DrlgLogicalRoomInfo info = room.getLogicalRoomInfo();
    D2RoomCoordListStrc list = list(1, false, 100, 200, 108, 208);
    info.setHasCoordList(true);
    info.setPCoordList(list);
    NativeMonsterPopulationRegion region = NativeMonsterPopulationRegion.capture(room, 0, 0)[0];
    assertTrue(region.contains(15, 15), "single-list rooms have no index-grid holes");
    assertEquals(173, region.densityAttempts());
  }

  static D2DrlgRoom splitRoom() {
    D2DrlgRoom room = new D2DrlgRoom();
    room.setNTileXPos(100); room.setNTileYPos(200);
    room.setNTileWidth(8); room.setNTileHeight(8);
    D2DrlgLogicalRoomInfo info = new D2DrlgLogicalRoomInfo();
    info.initializeCoordListCells(8, 8);
    room.setLogicalRoomInfo(info);
    D2RoomCoordListStrc normal = list(7, false, 102, 202, 106, 206);
    D2RoomCoordListStrc node = list(8, true, 100, 200, 108, 208);
    D2RoomCoordListStrc zero = list(0, false, 100, 200, 108, 208);
    D2RoomCoordListStrc empty = list(9, false, 0, 0, 0, 0);
    normal.setPNext(node); node.setPNext(zero); zero.setPNext(empty);
    info.setPCoordList(normal);
    for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
      info.setCoordListCell(x, y, x >= 2 && x < 6 && y >= 2 && y < 6
          && !(x == 3 && y == 3) ? normal : node);
    }
    return room;
  }

  private static D2RoomCoordListStrc list(int index, boolean node, int x, int y, int right, int bottom) {
    D2RoomCoordListStrc list = new D2RoomCoordListStrc();
    list.setNIndex(index); list.setBNode(node);
    D2DrlgCoord box = list.getPBox(1);
    box.setNPosX(x); box.setNPosY(y); box.setNWidth(right); box.setNHeight(bottom);
    return list;
  }
}
