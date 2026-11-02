package com.riiablo.map;

import com.d2moo.common.drlg.D2DrlgCoord;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2RoomCoordListStrc;
import com.d2moo.common.drlg.DrlgDrlgLogic;
import java.util.ArrayList;

/** Immutable MonsterRegion coordinate-list data, retained after DRLG is released. */
final class NativeMonsterPopulationRegion {
  final int index, x, y, width, height;
  private final int roomX, roomY, tilesX, tilesY;
  private final int[] indices;

  private NativeMonsterPopulationRegion(int index, int x, int y, int width, int height,
      int roomX, int roomY, int tilesX, int tilesY, int[] indices) {
    this.index = index;
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
    this.roomX = roomX;
    this.roomY = roomY;
    this.tilesX = tilesX;
    this.tilesY = tilesY;
    this.indices = indices;
  }

  static NativeMonsterPopulationRegion wholeRoom(Map.RoomEx room) {
    return new NativeMonsterPopulationRegion(1, room.x, room.y, room.width, room.height,
        room.x, room.y, 0, 0, null);
  }

  static NativeMonsterPopulationRegion[] capture(D2DrlgRoom room, int worldX, int worldY) {
    int tilesX = room.getNTileWidth(), tilesY = room.getNTileHeight();
    int nativeX = room.getNTileXPos() * 5, nativeY = room.getNTileYPos() * 5;
    int[] indices = new int[tilesX * tilesY];
    for (int y = 0; y < tilesY; y++) for (int x = 0; x < tilesX; x++) {
      indices[y * tilesX + x] = DrlgDrlgLogic.getRoomCoordListIndex(
          room, nativeX + x * 5, nativeY + y * 5);
    }
    ArrayList<NativeMonsterPopulationRegion> regions = new ArrayList<>();
    for (D2RoomCoordListStrc list = DrlgDrlgLogic.getRoomCoordList(room);
        list != null; list = list.getPNext()) {
      // D2GAME_PopulateRoom_6FC67190 skips nodes, index zero and empty boxes.
      // pBox[1] stores absolute tile edges, not width/height extents.
      D2DrlgCoord box = list.getPBox(1);
      if (list.getNIndex() == 0 || list.isBNode() || box == null
          || (box.getNPosX() == 0 && box.getNWidth() == 0)
          || box.getNWidth() <= box.getNPosX() || box.getNHeight() <= box.getNPosY()) continue;
      regions.add(new NativeMonsterPopulationRegion(list.getNIndex(),
          worldX + box.getNPosX() * 5 - nativeX,
          worldY + box.getNPosY() * 5 - nativeY,
          (box.getNWidth() - box.getNPosX()) * 5,
          (box.getNHeight() - box.getNPosY()) * 5,
          worldX, worldY, tilesX, tilesY, indices));
    }
    return regions.toArray(new NativeMonsterPopulationRegion[0]);
  }

  int densityAttempts() {
    // Preserve the native integer operation order: width / 3 * height / 3.
    return width / 3 * height / 3;
  }

  boolean contains(int px, int py) {
    if (px < x || py < y || px >= x + width || py >= y + height) return false;
    if (indices == null) return true;
    int tx = Math.floorDiv(px - roomX, 5), ty = Math.floorDiv(py - roomY, 5);
    return tx >= 0 && ty >= 0 && tx < tilesX && ty < tilesY
        && indices[ty * tilesX + tx] == index;
  }
}
