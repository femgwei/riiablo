package com.riiablo.engine.server;

import com.badlogic.gdx.utils.IntMap;
import com.d2moo.common.collision.D2Collision;
import com.riiablo.map.Map;

/** Event ledger for footprints in the actual shared room bits, not an owner-count grid. */
final class SharedUnitCollisionIndex {
  static final int WITH_SIZE = -1;

  static final class Footprint {
    final Map.Zone zone;
    final int x, y, pattern, size, mask;
    final boolean hidden;
    final int generation;
    final Map.Zone[] seamZones;
    final int[] seamGenerations;

    Footprint(Map.Zone zone, int x, int y, int pattern, int size, int mask, boolean hidden) {
      this.zone = zone;
      this.x = x;
      this.y = y;
      this.pattern = pattern;
      this.size = size;
      this.mask = mask;
      this.hidden = hidden;
      generation = zone.nativeCollisionGeneration();
      // Only boundary footprints need a room-ring snapshot. Interior units
      // keep no per-cell arrays. A later unload/reload cannot erase fresh bits
      // in a replacement grid through an old footprint reference.
      if (zone.nativeCollisionZone(x - 1, y - 1) == zone
          && zone.nativeCollisionZone(x + 1, y + 1) == zone) {
        seamZones = null; seamGenerations = null;
      } else {
        seamZones = new Map.Zone[9]; seamGenerations = new int[9];
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
          int index = (dy + 1) * 3 + dx + 1;
          Map.Zone owner = zone.nativeCollisionZone(x + dx, y + dy);
          seamZones[index] = owner;
          seamGenerations[index] = owner == null ? 0 : owner.nativeCollisionGeneration();
        }
      }
    }

    Footprint at(int x, int y) {
      Map.Zone owner = zone.nativeCollisionZone(x, y);
      return new Footprint(owner == null ? zone : owner, x, y, pattern, size, mask, hidden);
    }

    boolean same(Footprint other) {
      return other != null && zone == other.zone && x == other.x && y == other.y
          && pattern == other.pattern && size == other.size && mask == other.mask
          && hidden == other.hidden && generation == other.generation
          && java.util.Arrays.equals(seamZones, other.seamZones)
          && java.util.Arrays.equals(seamGenerations, other.seamGenerations);
    }

    Map.Zone cellZone(int cx, int cy) {
      int dx = cx - x, dy = cy - y;
      if (Math.abs(dx) > 1 || Math.abs(dy) > 1) return null;
      return seamZones == null ? zone : seamZones[(dy + 1) * 3 + dx + 1];
    }

    int cellGeneration(int cx, int cy) {
      return seamGenerations == null ? generation : seamGenerations[(cy - y + 1) * 3 + cx - x + 1];
    }

    int bitsAt(Map.Zone cellZone, int cx, int cy) {
      if (cellZone(cx, cy) != cellZone || cellGeneration(cx, cy) != cellZone.nativeCollisionGeneration()) return 0;
      int dx = Math.abs(cx - x), dy = Math.abs(cy - y);
      if (pattern == WITH_SIZE) {
        boolean inside = size == 1 ? dx == 0 && dy == 0
            : size == 2 ? dx + dy <= 1 : size == 3 && dx <= 1 && dy <= 1;
        return inside ? mask : 0;
      }
      if (pattern == D2Collision.PATTERN_NONE) return 0;
      boolean big = pattern == D2Collision.PATTERN_BIG_UNIT_PRESENCE
          || pattern == D2Collision.PATTERN_BIG_PET_PRESENCE;
      int bits = (big ? dx <= 1 && dy <= 1 : dx + dy <= 1) ? mask : 0;
      if (mask != 0 && (big ? dx + dy <= 1 : dx == 0 && dy == 0)) {
        if (pattern == D2Collision.PATTERN_SMALL_UNIT_PRESENCE
            || pattern == D2Collision.PATTERN_BIG_UNIT_PRESENCE) bits |= D2Collision.COLLIDE_NO_PATH;
        if (pattern == D2Collision.PATTERN_SMALL_PET_PRESENCE
            || pattern == D2Collision.PATTERN_BIG_PET_PRESENCE) bits |= D2Collision.COLLIDE_PET;
      }
      return bits;
    }
  }

  private final IntMap<Footprint> footprints = new IntMap<>();

  Footprint get(int id) { return footprints.get(id); }

  void update(int id, Footprint next) {
    Footprint previous = footprints.get(id);
    if (next == null) { remove(id); return; }
    if (next.same(previous)) return; // Never resurrect bits cleared by another unit.
    if (previous != null && next.hidden && !previous.hidden) {
      // AiThink FrogDemon: reset only the centre, then change the path field.
      // BIG corners and NO_PATH arms must survive this operation.
      previous.zone.resetNativeCollisionMask(previous.x, previous.y,
          D2Collision.COLLIDE_MONSTER | D2Collision.COLLIDE_NO_PATH);
      previous = new Footprint(previous.zone, previous.x, previous.y,
          D2Collision.PATTERN_SMALL_NO_PRESENCE, next.size, next.mask, true);
      footprints.put(id, previous);
      if (next.same(previous)) return;
    }
    if (previous != null) alter(previous, false);
    footprints.put(id, next);
    alter(next, true);
  }

  void remove(int id) {
    Footprint previous = footprints.remove(id);
    if (previous != null) alter(previous, false);
  }

  /** Native clear, test, restore-or-write transaction; same-cell steps do not rewrite. */
  boolean move(int id, Footprint next, int conditionMask) {
    Footprint previous = footprints.get(id);
    if (next.same(previous)) return true;
    if (previous != null) alter(previous, false);
    int collided = check(next.zone, next.x, next.y, next.pattern, next.size, conditionMask, -1, -1);
    if (collided != 0) {
      if (previous != null) alter(previous, true);
      return false;
    }
    footprints.put(id, next);
    alter(next, true);
    return true;
  }

  /** Read-only source exclusion; unlike remove/add this cannot restore somebody else's cleared bits. */
  int check(Map.Zone zone, int x, int y, int pattern, int size, int mask, int source, int ignored) {
    int result = 0;
    int radius = pattern == WITH_SIZE ? (size >= 2 ? 1 : 0)
        : pattern == D2Collision.PATTERN_NONE ? 0 : 1;
    boolean cross = pattern == WITH_SIZE ? size == 2
        : pattern != D2Collision.PATTERN_BIG_UNIT_PRESENCE
            && pattern != D2Collision.PATTERN_BIG_PET_PRESENCE;
    Footprint own = footprints.get(source), target = footprints.get(ignored);
    for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++) {
      if (cross && dx != 0 && dy != 0) continue;
      int cx = x + dx, cy = y + dy;
      Map.Zone cellZone = zone.nativeCollisionZone(cx, cy);
      // D2Common's invalid-coordinate mask is a result, not filtered by mask.
      if (cellZone == null || !cellZone.hasNativeCollisionData()) {
        result |= D2Collision.COLLIDE_MASK_INVALID;
        continue;
      }
      int flags = cellZone.nativeCollisionFlagsWorld(cx, cy);
      if (own != null) flags &= ~own.bitsAt(cellZone, cx, cy);
      if (target != null) flags &= ~target.bitsAt(cellZone, cx, cy);
      result |= flags & mask;
    }
    return result;
  }

  private static void alter(Footprint footprint, boolean set) {
    for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
      int x = footprint.x + dx, y = footprint.y + dy;
      Map.Zone zone = footprint.cellZone(x, y);
      if (zone == null || zone.nativeCollisionGeneration() != footprint.cellGeneration(x, y)) continue;
      int bits = footprint.bitsAt(zone, x, y);
      if (bits == 0) continue;
      if (set) zone.setNativeCollisionMask(x, y, bits);
      else zone.resetNativeCollisionMask(x, y, bits);
    }
  }
}
