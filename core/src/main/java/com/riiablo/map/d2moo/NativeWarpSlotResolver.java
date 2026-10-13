package com.riiablo.map.d2moo;

import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgRoomFlags;

/** UNIT_TILE carries a LvlWarp definition id, not its Vis/Warp slot. */
final class NativeWarpSlotResolver {
  private NativeWarpSlotResolver() {}

  static int resolve(D2DrlgLevel level, int[] vis, int[] warps, int roomId, int warpId) {
    if (level == null || vis == null || warps == null || warpId < 0) return -1;
    D2DrlgRoom room = level.getFirstRoomEx();
    for (int i = 0; room != null && i < roomId; i++) room = room.getDrlgRoomNext();
    int count = Math.min(8, Math.min(vis.length, warps.length));
    int unique = -1, flagged = -1, matches = 0, flaggedMatches = 0;
    for (int slot = 0; slot < count; slot++) {
      if (vis[slot] <= 0 || warps[slot] != warpId) continue;
      unique = slot; matches++;
      if (room != null && (room.getFlags() & (D2DrlgRoomFlags.HAS_WARP_0 << slot)) != 0) {
        flagged = slot; flaggedMatches++;
      }
    }
    if (flaggedMatches == 1) return flagged;
    return matches == 1 ? unique : -1;
  }
}
