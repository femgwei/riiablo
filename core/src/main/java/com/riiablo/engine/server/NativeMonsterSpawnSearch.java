package com.riiablo.engine.server;

import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;

/** 1.10F D2Game 6FC691E5..6FC69481, normal perimeter branch only. */
final class NativeMonsterSpawnSearch {
  interface Probe { boolean accept(int x, int y); }
  interface CollisionProbe { boolean free(int x, int y, int size, int mask); }

  static boolean usesSpecialFloor(int monsterId, int spawnCol) {
    // 1.10F binary 6FC68FEE..6FC69008: tentacle chains and hellmeteor bypass water tiles.
    return spawnCol == 1 && !(monsterId >= 258 && monsterId <= 263) && monsterId != 153;
  }

  static boolean searchSpecialFloor(D2Seed seed, com.d2moo.common.drlg.D2ActiveRoom room,
      CollisionProbe collision, Probe selected) {
    if (room == null || room.getPRoomTiles() == null) return false;
    com.d2moo.common.drlg.D2DrlgRoomTilesStrc tiles = room.getPRoomTiles();
    com.d2moo.common.drlg.D2DrlgTileDataStrc[] floors = tiles.getPFloorTiles();
    int count = tiles.getNFloors();
    if (floors == null || count <= 0 || count > floors.length) return false;
    int start = (int) ((Seed.rollRandomNumber(seed) & 0xffffffffL) % count);
    if (start == 0) start = 1;
    // Native starts at 1 even for count=1 (unsafe native access). Fail closed
    // after the native RNG roll rather than inventing a different candidate.
    if (start >= count) return false;
    int stop = start - 1;
    int index = start;
    do {
      com.d2moo.common.drlg.D2DrlgTileDataStrc floor = floors[index];
      if (floor != null && floor.getPTile() != null
          && (com.d2moo.common.d2cmp.D2Cmp.getTileFlags(floor.getPTile()) & 2) != 0) {
        int x = 5 * (room.getNTileXPos() + floor.getNPosX()) + 3;
        int y = 5 * (room.getNTileYPos() + floor.getNPosY()) + 3;
        if (collision.free(x, y, 1, 0x100)) {
          for (int side = 0; side < 4; side++) {
            int dx = side == 1 ? 3 : side == 3 ? -3 : 0;
            int dy = side == 0 ? -3 : side == 2 ? 3 : 0;
            if (collision.free(x + dx, y + dy, 2, 0x1c09))
              return selected.accept(x, y); // Spawn at center, not at the free berth.
          }
        }
      }
      index = (index + 1) % count;
    } while (index != stop);
    return false;
  }

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
