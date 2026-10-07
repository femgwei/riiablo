package com.riiablo.map;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.LongMap;
import com.riiablo.map.DT1.Tile;

/** Incremental index from world coordinates to renderable entity ids. */
final class RenderSpatialIndex {
  private static final int INITIAL_CELL_CAPACITY = 64;

  private final LongMap<Cell> cells = new LongMap<>(INITIAL_CELL_CAPACITY);
  private final IntMap<Membership> memberships = new IntMap<>();
  private final Array<Cell> freeCells = new Array<>(false, INITIAL_CELL_CAPACITY);
  private final Array<Membership> freeMemberships = new Array<>(false, INITIAL_CELL_CAPACITY);

  /**
   * Adds an entity or moves it between map cells.
   *
   * @return true only when index membership changed
   */
  boolean update(int entityId, float worldX, float worldY) {
    if (!Float.isFinite(worldX) || !Float.isFinite(worldY)) return remove(entityId);

    long key = key(worldToTile(worldX), worldToTile(worldY));
    Membership membership = memberships.get(entityId);
    if (membership != null && membership.cellKey == key) return false;

    if (membership == null) {
      membership = obtainMembership();
      memberships.put(entityId, membership);
    } else {
      removeFromCell(entityId, membership.cellKey);
    }

    Cell cell = cells.get(key);
    if (cell == null) {
      cell = obtainCell();
      cells.put(key, cell);
    }
    cell.entities.add(entityId);
    membership.cellKey = key;
    return true;
  }

  /** Removes an entity and releases an empty cell for reuse. */
  boolean remove(int entityId) {
    Membership membership = memberships.remove(entityId);
    if (membership == null) return false;
    removeFromCell(entityId, membership.cellKey);
    freeMemberships.add(membership);
    return true;
  }

  /** Clears all membership while retaining allocated cells and records for reuse. */
  void clear() {
    for (Cell cell : cells.values()) {
      cell.entities.clear();
      freeCells.add(cell);
    }
    cells.clear();
    for (Membership membership : memberships.values()) freeMemberships.add(membership);
    memberships.clear();
  }

  IntArray entitiesAtSubtile(int subtileX, int subtileY) {
    return entitiesAtTile(
        Math.floorDiv(subtileX, Tile.SUBTILE_SIZE),
        Math.floorDiv(subtileY, Tile.SUBTILE_SIZE));
  }

  IntArray entitiesAtTile(int tileX, int tileY) {
    Cell cell = cells.get(key(tileX, tileY));
    return cell == null ? null : cell.entities;
  }

  int activeCellCount() {
    return cells.size;
  }

  int entityCount() {
    return memberships.size;
  }

  private void removeFromCell(int entityId, long cellKey) {
    Cell cell = cells.get(cellKey);
    if (cell == null || !cell.entities.removeValue(entityId)) return;
    if (cell.entities.size == 0) {
      cells.remove(cellKey);
      freeCells.add(cell);
    }
  }

  private Cell obtainCell() {
    Cell cell = freeCells.size == 0 ? new Cell() : freeCells.pop();
    cell.entities.clear();
    return cell;
  }

  private Membership obtainMembership() {
    return freeMemberships.size == 0 ? new Membership() : freeMemberships.pop();
  }

  private static int worldToTile(float worldCoordinate) {
    return MathUtils.floor(worldCoordinate / Tile.SUBTILE_SIZE);
  }

  private static long key(int tileX, int tileY) {
    return ((long) tileX << 32) ^ (tileY & 0xFFFFFFFFL);
  }

  private static final class Cell {
    final IntArray entities = new IntArray(false, 4);
  }

  private static final class Membership {
    long cellKey;
  }
}
