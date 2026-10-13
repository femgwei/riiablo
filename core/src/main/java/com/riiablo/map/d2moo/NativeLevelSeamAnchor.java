package com.riiablo.map.d2moo;

import com.d2moo.common.drlg.*;

/** Native RoomEx orths and outdoor level boxes, never a whole-zone centre fallback. */
public final class NativeLevelSeamAnchor {
  private NativeLevelSeamAnchor() {}

  public static int[] resolve(D2DrlgLevel source, D2DrlgLevel destination) {
    if (source == null || destination == null || source.getLevelCoords() == null) return null;
    for (D2DrlgLevel owner : new D2DrlgLevel[] {source, destination}) {
      D2DrlgLevel target = owner == source ? destination : source;
      for (D2DrlgRoom room = owner.getFirstRoomEx(); room != null; room = room.getDrlgRoomNext()) {
        for (D2DrlgOrth orth = room.getDrlgOrth(); orth != null; orth = orth.getPNext()) {
          D2DrlgLevel neighbor = orth.getPLevel();
          if (neighbor == null && orth.getPDrlgRoom() != null) neighbor = orth.getPDrlgRoom().getLevel();
          if (neighbor != target) continue;
          int x = room.getNTileXPos(), y = room.getNTileYPos(), direction = orth.getNDirection();
          if (direction == 0 || direction == 2) {
            x += direction == 2 ? room.getNTileWidth() : 0;
            y += room.getNTileHeight() / 2;
          } else if (direction == 1 || direction == 3) {
            x += room.getNTileWidth() / 2;
            y += direction == 3 ? room.getNTileHeight() : 0;
          } else continue;
          return local(source, x, y);
        }
      }
      if (owner.getOutdoors() == null) continue;
      for (D2DrlgOrth orth = owner.getOutdoors().getPRoomData(); orth != null; orth = orth.getPNext()) {
        if (orth.getPLevel() != target) continue;
        D2DrlgCoord a = owner.getLevelCoords(), b = target.getLevelCoords();
        int left = Math.max(a.getNPosX(), b.getNPosX());
        int right = Math.min(a.getNPosX() + a.getNWidth(), b.getNPosX() + b.getNWidth());
        int top = Math.max(a.getNPosY(), b.getNPosY());
        int bottom = Math.min(a.getNPosY() + a.getNHeight(), b.getNPosY() + b.getNHeight());
        if (left <= right && top <= bottom) return local(source, (left + right) / 2, (top + bottom) / 2);
      }
    }
    return null;
  }

  private static int[] local(D2DrlgLevel source, int x, int y) {
    return new int[] {(x - source.getLevelCoords().getNPosX()) * 5,
        (y - source.getLevelCoords().getNPosY()) * 5};
  }
}
