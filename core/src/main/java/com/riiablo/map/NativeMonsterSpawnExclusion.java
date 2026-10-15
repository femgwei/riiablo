package com.riiablo.map;

import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.DrlgDrlgWarp;

/** Copied level-wide MonsterRegion::sub_6FC66260 metadata, independent of freed DRLG. */
public final class NativeMonsterSpawnExclusion {
  private final int[] x, y;

  private NativeMonsterSpawnExclusion(int[] x, int[] y) {
    this.x = x;
    this.y = y;
  }

  /** Capture room centers (already subtiles) and tile-11 spawn (game tiles). */
  public static void capture(D2DrlgLevel level, Map.Zone zone) {
    if (level == null || level.getLevelCoords() == null || zone.levelId() <= 0) return;
    int[] sx = {-1}, sy = {-1};
    // Tile export has already initialized rooms. Metadata capture must not
    // activate additional rooms or consume the population/room random stream.
    DrlgDrlgWarp.findActSpawnRoomEx(level.getDrlg(), zone.levelId(), 11, sx, sy);
    zone.setNativeMonsterSpawnExclusion(project(level.getRoomCenterWarpX(),
        level.getRoomCenterWarpY(), level.getNRoomCoords(), sx[0], sy[0],
        level.getLevelCoords().getNPosX(), level.getLevelCoords().getNPosY(), zone.x, zone.y));
  }

  static NativeMonsterSpawnExclusion project(int[] warpX, int[] warpY, int count,
      int spawnTileX, int spawnTileY, int levelTileX, int levelTileY, int zoneX, int zoneY) {
    count = Math.max(0, Math.min(9, Math.min(count, Math.min(warpX.length, warpY.length))));
    // The native positivity test precedes projection: compatibility world
    // coordinates may legitimately be negative.
    boolean spawn = spawnTileX > 0 && spawnTileY > 0;
    int[] x = new int[count + (spawn ? 1 : 0)], y = new int[x.length];
    int dx = zoneX - levelTileX * 5, dy = zoneY - levelTileY * 5;
    for (int i = 0; i < count; i++) {
      x[i] = warpX[i] + dx;
      y[i] = warpY[i] + dy;
    }
    if (spawn) {
      x[count] = spawnTileX * 5 + dx;
      y[count] = spawnTileY * 5 + dy;
    }
    return new NativeMonsterSpawnExclusion(x, y);
  }

  boolean excludes(int candidateX, int candidateY, int warpDist) {
    if (warpDist <= 0) return false;
    for (int i = 0; i < x.length; i++) {
      long dx = (long) candidateX - x[i], dy = (long) candidateY - y[i];
      // WarpDist is already the squared-distance threshold; strict <, not <=.
      if (dx * dx + dy * dy < warpDist) return true;
    }
    return false;
  }
}
