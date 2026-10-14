package com.riiablo.engine.server.ai;

import com.riiablo.engine.server.combat.NativeMeleeDistance;

/** Read-only nonzero-point projection of PathMisc.cpp PATH_Toward.
 * Assumes positive native velocity, zero direction offset and stable collision
 * queries. Does not model the dynamic-path wrapper, flags or install a path. */
final class NativeTowardPathProbe {
  private static final int[][] DIRECTIONS = {
      {5, 4, 6}, {4, 5, 6}, {4, 3, 5}, {4, 3, 2}, {3, 4, 2},
      {6, 5, 4}, {5, 4, 6}, {4, 3, 5}, {3, 4, 2}, {2, 3, 4},
      {6, 7, 5}, {6, 7, 5}, {6, 7, 5}, {2, 1, 3}, {2, 1, 3},
      {6, 7, 0}, {7, 0, 6}, {0, 1, 7}, {1, 0, 2}, {2, 1, 0},
      {7, 0, 6}, {0, 7, 6}, {0, 1, 7}, {0, 1, 2}, {1, 0, 2}
  };
  private static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1};
  private static final int[] DY = {0, 1, 1, 1, 0, -1, -1, -1};

  interface Blocked { boolean at(int x, int y); }

  /** PATH_RayTrace's clipped endpoint, not an A* reachability result. */
  static final class RayResult {
    final int x, y;
    final boolean clear;

    RayResult(int x, int y, boolean clear) {
      this.x = x;
      this.y = y;
      this.clear = clear;
    }
  }

  static boolean hasPathPoints(int sx, int sy, int tx, int ty,
      int maxDistance, int targetTolerance, Blocked blocked) {
    return hasPathPoints(sx, sy, tx, ty, maxDistance, targetTolerance, blocked, false);
  }

  /** D2Common_10142 normalizes duplicate source points; if there is no next
   * point, its final cleanup clears dwPathPoints again. */
  static boolean hasPathPointsAfterNormalization(int sx, int sy, int tx, int ty,
      int maxDistance, int targetTolerance, Blocked blocked) {
    return hasPathPoints(sx, sy, tx, ty, maxDistance, targetTolerance, blocked, true);
  }

  private static boolean hasPathPoints(int sx, int sy, int tx, int ty,
      int maxDistance, int targetTolerance, Blocked blocked, boolean normalize) {
    if (sx == tx && sy == ty) return false;
    RayResult ray = trace(sx, sy, tx, ty, blocked);
    // PATH_Toward stores one point for a clear ray OR a clipped endpoint
    // within field_14 (PATH_UpdateTargetUnit returns 1 for players/monsters).
    if (ray.clear || heuristic(ray.x - tx, ray.y - ty) <= targetTolerance) {
      return !normalize || ray.x != sx || ray.y != sy;
    }
    if (ray.x != sx || ray.y != sy) return true; // A partial ray is already a path point.
    // With no ray progress, the first successful local step changes direction
    // from PATH_DIR_NULL but does NOT append the source. A budget of one exits
    // at LABEL_22 with zero points. At least one further iteration is needed
    // to append that step, regardless of whether it succeeds, blocks or turns.
    return maxDistance > 1 && hasStep(sx, sy, tx, ty, blocked);
  }

  /** sub_6FDABA50 uses its literal near-distance table below eight cells;
   * only larger deltas use 2*major + minor. */
  static int heuristic(int dx, int dy) {
    // The existing unit-size-one projection has exactly the same table,
    // +1 rule, and large-distance formula; do not maintain a second copy.
    return NativeMeleeDistance.between(0, 0, 1, dx, dy, 1);
  }

  /** PATH_RayTrace uses (abs(delta)+1) and checks the dominant-axis cell
   * before the minor-axis cell. A blocked intermediate cell returns the last
   * completed iteration, not the last individually tested free cell. */
  static RayResult trace(int sx, int sy, int tx, int ty, Blocked blocked) {
    int x = sx, y = sy, lastX = sx, lastY = sy;
    int nx = Math.abs(tx - sx) + 1, ny = Math.abs(ty - sy) + 1;
    int stepX = tx >= sx ? 1 : -1, stepY = ty >= sy ? 1 : -1;
    if (nx == ny) {
      while (x != tx) {
        x += stepX;
        y += stepY;
        if (blocked.at(x, y)) return new RayResult(lastX, lastY, false);
        lastX = x;
        lastY = y;
      }
    } else if (nx > ny) {
      int offset = ny;
      while (x != tx) {
        x += stepX;
        if (blocked.at(x, y)) return new RayResult(lastX, lastY, false);
        offset += ny;
        if (offset >= nx) {
          offset -= nx;
          y += stepY;
          if (offset > 0 && blocked.at(x, y)) return new RayResult(lastX, lastY, false);
        }
        lastX = x;
        lastY = y;
      }
    } else {
      int offset = nx;
      while (y != ty) {
        y += stepY;
        if (blocked.at(x, y)) return new RayResult(lastX, lastY, false);
        offset += nx;
        if (offset >= ny) {
          offset -= ny;
          x += stepX;
          if (offset > 0 && blocked.at(x, y)) return new RayResult(lastX, lastY, false);
        }
        lastX = x;
        lastY = y;
      }
    }
    return new RayResult(tx, ty, true);
  }

  static boolean hasStep(int sx, int sy, int tx, int ty, Blocked blocked) {
    if (sx == tx && sy == ty) return false;
    for (int direction : DIRECTIONS[directionIndex(tx - sx, ty - sy)]) {
      if (!blocked.at(sx + DX[direction], sy + DY[direction])) return true;
    }
    return false;
  }

  /** PathMisc.cpp sub_6FDAB610, including slope and parity branches. */
  private static int directionIndex(int dx, int dy) {
    int ax = Math.abs(dx);
    int ay = Math.abs(dy);
    if (ax < 2 * ay) {
      if (ay >= 2 * ax) {
        if (dx < 0) {
          if (dy < -1) return 5;
          if (dy > 1) dy = 2;
          return dy + 7;
        }
        dx &= 1;
      }
    } else {
      if (dy >= 0) dy &= 1;
      else dy = -1;
    }
    dx = Math.max(-2, Math.min(2, dx));
    if (dy < -1) return 5 * dx + 10;
    dy = Math.min(2, dy);
    return dy + 5 * dx + 12;
  }

  private NativeTowardPathProbe() {}
}
