package com.riiablo.map.d2moo;

import com.d2moo.common.drlg.*;
import com.riiablo.map.Map;

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
    // Act V installs town/outdoor Vis links with Warp=-1 in
    // DrlgOutPlace::sub_6FD826D0; RoomEx orths need not survive export.
    // Require that native walk link as well as an exact shared edge.
    for (D2DrlgLevel owner : new D2DrlgLevel[] {source, destination}) {
      D2DrlgLevel target = owner == source ? destination : source;
      if (owner.getDrlg() == null) continue;
      int[] vis = DrlgDrlgRoom.getVisArrayFromLevelId(owner.getDrlg(), owner.getLevelId());
      int[] warps = DrlgDrlgWarp.getWarpIdArrayFromLevelId(owner.getDrlg(), owner.getLevelId());
      for (int slot = 0; vis != null && warps != null && slot < Math.min(vis.length, warps.length); slot++) {
        if (vis[slot] != target.getLevelId() || warps[slot] != -1) continue;
        D2DrlgCoord a = source.getLevelCoords(), b = destination.getLevelCoords();
        if (b == null) return null;
        int left = Math.max(a.getNPosX(), b.getNPosX());
        int right = Math.min(a.getNPosX() + a.getNWidth(), b.getNPosX() + b.getNWidth());
        int top = Math.max(a.getNPosY(), b.getNPosY());
        int bottom = Math.min(a.getNPosY() + a.getNHeight(), b.getNPosY() + b.getNHeight());
        if ((left == right && top < bottom) || (top == bottom && left < right)) {
          return local(source, (left + right) / 2, (top + bottom) / 2);
        }
      }
    }
    return null;
  }

  /**
   * Projects D2Common::sub_6FD82750 outdoor Vis/Warp=-1 links into the
   * compatibility map.  These entries are continuous level seams created by
   * DRLGROOM_AddOrth, not interactive Warp entities.
   */
  public static int installOutdoorSeams(Map map, D2DrlgStrc drlg) {
    if (map == null || drlg == null) return 0;
    int installed = 0;
    for (Map.Zone source : map.getZones()) {
      if (source == null || source.level == null) continue;
      D2DrlgLevel nativeSource = DrlgDrlg.getLevel(drlg, source.level.Id);
      if (nativeSource == null || nativeSource.getDrlgType() != D2DrlgTypes.DRLGTYPE_OUTDOOR) continue;
      int[] vis = DrlgDrlgRoom.getVisArrayFromLevelId(drlg, source.level.Id);
      int[] warp = DrlgDrlgWarp.getWarpIdArrayFromLevelId(drlg, source.level.Id);
      int slots = Math.min(8, Math.min(vis == null ? 0 : vis.length, warp == null ? 0 : warp.length));
      for (int slot = 0; slot < slots; slot++) {
        int destinationId = vis[slot];
        if (destinationId <= 0 || warp[slot] != -1) continue;
        Map.Zone destination = findZone(map, destinationId);
        D2DrlgLevel nativeDestination = DrlgDrlg.getLevel(drlg, destinationId);
        if (destination == null || destination == source || nativeDestination == null
            || !map.areZonesAdjacent(source, destination)) continue;
        int[] anchor = resolve(nativeSource, nativeDestination);
        if (anchor == null) continue;

        map.addWarpDestinationOverride(source.level.Id, slot, destinationId);
        source.setNativeSeamAnchor(slot, anchor[0], anchor[1]);
        installed++;

        int reverseSlot = findReverseSlot(drlg, destinationId, source.level.Id);
        if (reverseSlot < 0) continue;
        int[] reverseAnchor = resolve(nativeDestination, nativeSource);
        if (reverseAnchor == null) continue;
        map.addWarpDestinationOverride(destinationId, reverseSlot, source.level.Id);
        destination.setNativeSeamAnchor(reverseSlot, reverseAnchor[0], reverseAnchor[1]);
      }
    }
    return installed;
  }

  private static Map.Zone findZone(Map map, int levelId) {
    for (Map.Zone zone : map.getZones()) {
      if (zone != null && zone.level != null && zone.level.Id == levelId) return zone;
    }
    return null;
  }

  private static int findReverseSlot(D2DrlgStrc drlg, int levelId, int targetLevelId) {
    int[] vis = DrlgDrlgRoom.getVisArrayFromLevelId(drlg, levelId);
    if (vis == null) return -1;
    int[] warp = DrlgDrlgWarp.getWarpIdArrayFromLevelId(drlg, levelId);
    int slots = Math.min(8, Math.min(vis.length, warp == null ? 0 : warp.length));
    for (int slot = 0; slot < slots; slot++) {
      if (vis[slot] == targetLevelId && warp[slot] == -1) return slot;
    }
    return -1;
  }

  private static int[] local(D2DrlgLevel source, int x, int y) {
    return new int[] {(x - source.getLevelCoords().getNPosX()) * 5,
        (y - source.getLevelCoords().getNPosY()) * 5};
  }
}
