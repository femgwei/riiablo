package com.riiablo.engine.server;

import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.LongMap;

/**
 * Small dynamic footprint grid used by authoritative movement.
 *
 * <p>D2 does not rely on a physics engine to push monsters apart.  It writes
 * each unit footprint into the room collision grid and atomically transfers
 * that footprint when a step succeeds.  This class is the in-memory
 * equivalent for the server and the single-player local simulation.</p>
 */
public final class UnitCollisionGrid {
  private static final class Footprint {
    int x;
    int y;
    int size;
  }

  private final LongMap<Integer> cells = new LongMap<>();
  private final IntMap<Footprint> units = new IntMap<>();

  public void clear() {
    cells.clear();
    units.clear();
  }

  public void put(int entityId, int x, int y, int size) {
    remove(entityId);
    Footprint footprint = new Footprint();
    footprint.x = x;
    footprint.y = y;
    footprint.size = Math.max(1, size);
    units.put(entityId, footprint);
    forEachCell(footprint, (cellX, cellY) -> increment(key(cellX, cellY)));
  }

  public void remove(int entityId) {
    Footprint footprint = units.remove(entityId);
    if (footprint == null) return;
    forEachCell(footprint, (cellX, cellY) -> decrement(key(cellX, cellY)));
  }

  /** Returns whether a footprint can occupy the destination. */
  public boolean isFree(int moverId, int ignoredEntityId,
      int x, int y, int size) {
    return isFree(moverId, ignoredEntityId, x, y, size, null);
  }

  /** Returns whether a footprint is free after applying a native collision mask. */
  public boolean isFree(int moverId, int ignoredEntityId,
      int x, int y, int size, UnitBlocker blocker) {
    Footprint candidate = new Footprint();
    candidate.x = x;
    candidate.y = y;
    candidate.size = Math.max(1, size);
    final boolean[] free = {true};
    forEachCell(candidate, (cellX, cellY) -> {
      Integer occupants = cells.get(key(cellX, cellY));
      if (occupants != null && occupants > 0) {
        // A cell count alone is sufficient for the fast path.  The mover is
        // removed before authoritative movement checks, and path queries use
        // ignoredEntityId only for the target unit; both are handled by the
        // owner map below when a cell is occupied.
        if (hasOtherUnit(cellX, cellY, moverId, ignoredEntityId, blocker)) free[0] = false;
      }
    });
    return free[0];
  }

  /** Atomically transfers a unit footprint, restoring it if blocked. */
  public boolean move(int entityId, int ignoredEntityId,
      int x, int y, int size) {
    return move(entityId, ignoredEntityId, x, y, size, null);
  }

  public boolean move(int entityId, int ignoredEntityId,
      int x, int y, int size, UnitBlocker blocker) {
    Footprint previous = units.get(entityId);
    if (previous != null) {
      int oldX = previous.x;
      int oldY = previous.y;
      int oldSize = previous.size;
      remove(entityId);
      if (isFree(entityId, ignoredEntityId, x, y, size, blocker)) {
        put(entityId, x, y, size);
        return true;
      }
      put(entityId, oldX, oldY, oldSize);
      return false;
    }
    if (!isFree(entityId, ignoredEntityId, x, y, size, blocker)) return false;
    put(entityId, x, y, size);
    return true;
  }

  private boolean hasOtherUnit(int cellX, int cellY,
      int moverId, int ignoredEntityId, UnitBlocker blocker) {
    long cell = key(cellX, cellY);
    for (IntMap.Entry<Footprint> entry : units.entries()) {
      if (entry.key == moverId || entry.key == ignoredEntityId) continue;
      if (blocker != null && !blocker.blocks(entry.key)) continue;
      Footprint footprint = entry.value;
      if (contains(footprint, cellX, cellY)) return true;
    }
    return false;
  }

  public interface UnitBlocker {
    boolean blocks(int entityId);
  }

  private static boolean contains(Footprint footprint, int x, int y) {
    int radius = Math.max(0, footprint.size - 1);
    return x >= footprint.x - radius && x <= footprint.x + radius
        && y >= footprint.y - radius && y <= footprint.y + radius;
  }

  private void increment(long cell) {
    Integer count = cells.get(cell);
    cells.put(cell, count == null ? 1 : count + 1);
  }

  private void decrement(long cell) {
    Integer count = cells.get(cell);
    if (count == null || count <= 1) cells.remove(cell);
    else cells.put(cell, count - 1);
  }

  private static long key(int x, int y) {
    return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
  }

  private interface CellConsumer {
    void accept(int x, int y);
  }

  private static void forEachCell(Footprint footprint, CellConsumer consumer) {
    int radius = Math.max(0, footprint.size - 1);
    for (int y = footprint.y - radius; y <= footprint.y + radius; y++) {
      for (int x = footprint.x - radius; x <= footprint.x + radius; x++) {
        consumer.accept(x, y);
      }
    }
  }
}
