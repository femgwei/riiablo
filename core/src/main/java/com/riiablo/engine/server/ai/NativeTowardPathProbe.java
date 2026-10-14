package com.riiablo.engine.server.ai;

/** Initial-step projection of PathMisc.cpp PATH_Toward / sub_6FDAA880.
 * Unlike A*, TOWARD can produce points without reaching the destination.
 * Tests the ordinary zero-direction-offset branch's initial directions only;
 * full native point counts also depend on distance budget, ray and path flags.
 * This does not construct or install a path. */
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
