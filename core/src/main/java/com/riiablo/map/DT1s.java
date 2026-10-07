package com.riiablo.map;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;

import java.util.Locale;

public class DT1s {
  // TODO: tiles and prob are both keyed with tile ID, can speed up if using one map to Pair<prob, tiles>
  ObjectSet<DT1>          dt1s  = new ObjectSet<>();
  IntMap<Array<DT1.Tile>> tiles = new IntMap<>();
  IntIntMap               prob  = new IntIntMap();
  ObjectMap<String, IntMap<Array<DT1.Tile>>> sourceTiles = new ObjectMap<>();
  ObjectMap<DT1.Tile, String> tileSources = new ObjectMap<>();
  ObjectMap<DT1, String> dt1Sources = new ObjectMap<>();

  void add(DT1.Tile tile) {
    //if (tile.rarity == 0) return;
    Array<DT1.Tile> tiles = this.tiles.get(tile.id);
    if (tiles == null) this.tiles.put(tile.id, tiles = new Array<>());
    tiles.add(tile);
    prob.getAndIncrement(tile.id, 0, tile.rarity);
  }

  void remove(DT1.Tile tile) {
    //if (tile.rarity == 0) return;
    Array<DT1.Tile> tiles = this.tiles.get(tile.id);
    if (tiles == null) return;
    tiles.removeValue(tile, true);
    prob.getAndIncrement(tile.id, 0, -tile.rarity);
  }

  public boolean add(DT1 dt1) {
    return add(dt1, dt1.fileName);
  }

  public boolean add(DT1 dt1, String fileName) {
    if (!dt1s.add(dt1)) return false;
    String source = normalize(fileName);
    dt1Sources.put(dt1, source);
    IntMap<Array<DT1.Tile>> indexed = sourceTiles.get(source);
    if (indexed == null) sourceTiles.put(source, indexed = new IntMap<>());
    for (DT1.Tile tile : dt1.tiles) {
      add(tile);
      Array<DT1.Tile> variants = indexed.get(tile.id);
      if (variants == null) indexed.put(tile.id, variants = new Array<>());
      variants.add(tile);
      tileSources.put(tile, source);
    }
    return true;
  }

  public boolean remove(DT1 dt1) {
    if (!dt1s.remove(dt1)) return false;
    String source = dt1Sources.remove(dt1);
    for (DT1.Tile tile : dt1.tiles) {
      remove(tile);
      tileSources.remove(tile);
    }
    sourceTiles.remove(source);
    return true;
  }

  public DT1.Tile get(int orientation, int mainIndex, int subIndex) {
    int id = DT1.Tile.Index.create(orientation, mainIndex, subIndex);
    Array<DT1.Tile> tiles = this.tiles.get(id);
    return next(id, tiles);
  }

  public DT1.Tile get(DS1.Cell cell) {
    Array<DT1.Tile> tiles = this.tiles.get(cell.id);
    return next(cell.id, tiles);
  }

  public DT1.Tile get(int id) {
    Array<DT1.Tile> tiles = this.tiles.get(id);
    return next(id, tiles);
  }

  public DT1.Tile get(String sourceFile, int id) {
    if (sourceFile == null) return null;
    IntMap<Array<DT1.Tile>> indexed = sourceTiles.get(normalize(sourceFile));
    return indexed == null ? null : next(indexed.get(id));
  }

  public DT1.Tile getSibling(DT1.Tile tile, int id) {
    String source = tileSources.get(tile);
    DT1.Tile sibling = get(source, id);
    return sibling != null ? sibling : get(id);
  }

  /**
   * Looks up an animated DT1 frame without applying the normal rarity
   * weighting used for random terrain selection. For lava tiles, D2Common
   * defines rarity as the animation frame index (0, 1, ...). D2CMP searches
   * every loaded tile library for the matching orientation/style/sequence;
   * it does not require every individual frame to repeat the lava flag.
   */
  public DT1.Tile getAnimationFrame(DT1.Tile tile, int frame) {
    if (tile == null || frame < 0) return tile;
    Array<DT1.Tile> variants = tiles.get(tile.id);
    if (variants == null) return tile;
    for (DT1.Tile candidate : variants) {
      if (candidate != null && candidate.rarity == frame) return candidate;
    }
    return variants.size == 0 ? tile : variants.first();
  }

  /**
   * D2Common classifies the whole orientation/style/sequence group from its
   * animated candidate. The tile selected for a map cell may itself be a
   * different frame, so test the complete group rather than that one record.
   */
  public boolean isAnimated(DT1.Tile tile) {
    if (tile == null) return false;
    Array<DT1.Tile> variants = tiles.get(tile.id);
    if (variants == null) return tile.isAnimatedMaterial();
    for (DT1.Tile candidate : variants) {
      if (candidate != null && candidate.isAnimatedMaterial()) return true;
    }
    return false;
  }

  /** Number of animation frames available for an animated tile. */
  public int getAnimationFrameCount(DT1.Tile tile) {
    if (!isAnimated(tile)) return 1;
    Array<DT1.Tile> variants = tiles.get(tile.id);
    return variants == null ? 1 : Math.max(1, variants.size);
  }

  private DT1.Tile next(int id, Array<DT1.Tile> tiles) {
    if (tiles == null) return null;
    int sum = prob.get(id, 0);
    int random = sum == 0 ? 0 : MathUtils.random(sum - 1);
    for (DT1.Tile tile : tiles) {
      random -= tile.rarity;
      if (random <= 0) {
        return tile;
      }
    }

    return null;
  }

  private DT1.Tile next(Array<DT1.Tile> tiles) {
    if (tiles == null) return null;
    int sum = 0;
    for (DT1.Tile tile : tiles) sum += tile.rarity;
    int random = sum == 0 ? 0 : MathUtils.random(sum - 1);
    for (DT1.Tile tile : tiles) {
      random -= tile.rarity;
      if (random <= 0) return tile;
    }
    return null;
  }

  private static String normalize(String fileName) {
    return fileName == null ? null
        : fileName.replace('\\', '/').toLowerCase(Locale.ROOT);
  }

  public void clear() {
    dt1s.clear();
    tiles.clear();
    prob.clear();
    sourceTiles.clear();
    tileSources.clear();
    dt1Sources.clear();
  }
}
