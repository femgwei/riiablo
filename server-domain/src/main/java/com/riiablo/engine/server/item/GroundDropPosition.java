package com.riiablo.engine.server.item;

import java.util.Set;

import com.badlogic.gdx.math.Vector2;

/**
 * Native-style ground-item subtile placement.  D2 reserves one logical item
 * unit per subtile, but does not promise sprite-level separation.  Callers
 * collect the occupied cells for the current level, then use this helper to
 * choose the first free cell in an expanding Chebyshev ring.
 */
public final class GroundDropPosition {
  private GroundDropPosition() {}

  public static Vector2 findFree(float originX, float originY, Set<Long> occupied,
      int maxRadius, Vector2 out) {
    int centerX = Math.round(originX);
    int centerY = Math.round(originY);
    int limit = Math.max(0, maxRadius);
    for (int radius = 0; radius <= limit; radius++) {
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dx = -radius; dx <= radius; dx++) {
          if (radius != 0 && Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
          int x = centerX + dx;
          int y = centerY + dy;
          if (!occupied.contains(key(x, y))) {
            out.set(x, y);
            return out;
          }
        }
      }
    }
    // Native code eventually gives up in a saturated area; preserving the
    // requested subtile is preferable to moving an item an unbounded distance.
    return out.set(centerX, centerY);
  }

  public static long key(int x, int y) {
    return ((long) x << 32) ^ (y & 0xffffffffL);
  }
}
