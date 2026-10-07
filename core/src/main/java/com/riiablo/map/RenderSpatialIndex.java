package com.riiablo.map;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.LongMap;
import com.riiablo.map.DT1.Tile;

/**
 * Reusable per-frame index from world tile coordinates to renderable entity ids.
 *
 * <p>The renderer walks the visible isometric tile buffer several times. Keeping
 * the entity subscription in one spatial pass avoids rescanning every entity for
 * every visible tile while preserving the existing per-tile draw ordering.</p>
 */
final class RenderSpatialIndex {
  private static final int INITIAL_CELL_CAPACITY = 64;

  private final LongMap<Cell> cells = new LongMap<>(INITIAL_CELL_CAPACITY);
  private final Array<Cell> storage = new Array<>(false, INITIAL_CELL_CAPACITY);
  private int activeCellCount;
  private int entityCount;

  void beginFrame() {
    cells.clear();
    activeCellCount = 0;
    entityCount = 0;
  }

  void add(int entityId, float worldX, float worldY) {
    if (!Float.isFinite(worldX) || !Float.isFinite(worldY)) return;
    int tileX = worldToTile(worldX);
    int tileY = worldToTile(worldY);
    long key = key(tileX, tileY);
    Cell cell = cells.get(key);
    if (cell == null) {
      cell = obtainCell();
      cells.put(key, cell);
    }
    cell.entities.add(entityId);
    entityCount++;
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
    return activeCellCount;
  }

  int entityCount() {
    return entityCount;
  }

  private Cell obtainCell() {
    Cell cell;
    if (activeCellCount < storage.size) {
      cell = storage.get(activeCellCount);
      cell.entities.clear();
    } else {
      cell = new Cell();
      storage.add(cell);
    }
    activeCellCount++;
    return cell;
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
}
