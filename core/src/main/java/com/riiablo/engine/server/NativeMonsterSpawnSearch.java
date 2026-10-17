package com.riiablo.engine.server;

import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;

/** 1.10F D2Game 6FC691E5..6FC69481, normal perimeter branch only. */
final class NativeMonsterSpawnSearch {
  interface Probe { boolean accept(int x, int y); }

  static boolean search(D2Seed seed, int x, int y, int maxRadius, Probe probe) {
    for (int radius = maxRadius == 0 ? 0 : 3; radius <= maxRadius; radius += 3) {
      int dx, dy, stepX, stepY;
      boolean vertical = (Seed.rollRandomNumber(seed) & 1) != 0;
      int offset = radius == 0 ? 0 : (int) ((Seed.rollRandomNumber(seed) & 0xffffffffL) % radius);
      dx = vertical ? radius : offset;
      dy = vertical ? offset : radius;
      stepX = vertical ? 0 : 1;
      stepY = vertical ? 1 : 0;
      if ((Seed.rollRandomNumber(seed) & 1) != 0) dx = -dx;
      if ((Seed.rollRandomNumber(seed) & 1) != 0) dy = -dy;
      for (int remaining = Math.max(1, 8 * radius); remaining > 0; remaining--) {
        if (dx == -radius && dy == -radius) { stepX = 1; stepY = 0; }
        if (dx == radius && dy == -radius) { stepX = 0; stepY = 1; }
        if (dx == radius && dy == radius) { stepX = -1; stepY = 0; }
        if (dx == -radius && dy == radius) { stepX = 0; stepY = -1; }
        if (radius == 0) { stepX = 0; stepY = 0; }
        dx += stepX; dy += stepY;
        if (probe.accept(x + dx, y + dy)) return true;
      }
    }
    return false;
  }

  static int collisionMask(int spawnCol) {
    switch (spawnCol) {
      case 1: return 0x1c0;
      case 2: return com.d2moo.common.collision.D2Collision.COLLIDE_MASK_PLACEMENT;
      case 3: return 0;
      default: return com.d2moo.common.collision.D2Collision.COLLIDE_MASK_MONSTER_PATH;
    }
  }
}
