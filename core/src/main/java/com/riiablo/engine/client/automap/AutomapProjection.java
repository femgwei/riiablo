package com.riiablo.engine.client.automap;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.map.DT1;

/** Shared world-to-Automap coordinate conventions. */
public final class AutomapProjection {
  private AutomapProjection() {}

  /** DT1 tile origin to the center of its first 5x5 subtile footprint. */
  public static void tileCenter(int tileX, int tileY, Vector2 out) {
    out.set(tileX * DT1.Tile.SUBTILE_SIZE + DT1.Tile.SUBTILE_SIZE * 0.5f,
        tileY * DT1.Tile.SUBTILE_SIZE + DT1.Tile.SUBTILE_SIZE * 0.5f);
  }

  /** Converts a world point to the coordinate space used by AutomapCamera. */
  public static void worldToAutomap(float worldX, float worldY, Vector2 out) {
    out.set(worldX, worldY);
  }

  /** Returns the DT1 tile index containing a world-subtile coordinate. */
  public static int tileIndex(int worldSubtile) {
    // Java integer division truncates toward zero; D2 coordinates can be
    // negative, so floor division is required at Zone boundaries.
    return Math.floorDiv(worldSubtile, DT1.Tile.SUBTILE_SIZE);
  }

  /** Returns the exclusive tile bound covering [worldStart, worldEnd). */
  public static int tileEndExclusive(int worldEndExclusive) {
    int size = DT1.Tile.SUBTILE_SIZE;
    return Math.floorDiv(worldEndExclusive + size - 1, size);
  }
}
