package com.riiablo.drlg;

/**
 * DRLG 生成用的 Tile 网格，对应 D2MOO 的 D2DrlgTileGridStrc。
 *
 * floorIds 保留为现有地形生成代码的主 floor 层；wallIds 和 shadowIds
 * 保留 D2MOO 导出的多层结构，避免同一坐标的墙体互相覆盖。
 * 支持 D2MOD 风格的 DirtPathGrid：先标记路径经过的格子，再按连通性生成土路瓦片。
 */
public class TileGrid {
  /** Matches the maximum layer counts supported by DS1/Map. */
  public static final int MAX_WALL_LAYERS = 4;

  /** 宽度（tile 数） */
  public final int width;
  /** 高度（tile 数） */
  public final int height;

  /**
   * floorIds[y][x] 存储一个“地形类型 ID”，目前直接用 dt1 的 mainIndex 或自定义类型。
   * -1 表示尚未写入（空）。
   */
  public final int[][] floorIds;

  /**
   * wallIds[layer][y][x] stores up to four wall/roof tiles at one coordinate.
   * -1 means that the slot is unassigned.
   */
  public final int[][][] wallIds;

  /**
   * shadowIds[y][x] stores the single shadow layer supported by riiablo.
   * -1 means that the slot is unassigned.
   */
  public final int[][] shadowIds;

  /**
   * D2MOD: pDirtPathGrid 等价物。标记路径经过的格子，供 DRLG_OUTDOORS_GenerateDirtPath 使用。
   * dirtPathFlags[y][x] == true 表示该格有路径经过。
   */
  public final boolean[][] dirtPathFlags;

  public TileGrid(int width, int height) {
    this.width = width;
    this.height = height;
    this.floorIds = new int[height][width];
    this.wallIds = new int[MAX_WALL_LAYERS][height][width];
    this.shadowIds = new int[height][width];
    this.dirtPathFlags = new boolean[height][width];
    clearExportedTileIds();
  }

  /** Resets the floor layer to the unassigned sentinel. */
  public void clearFloorIds() {
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        floorIds[y][x] = -1;
      }
    }
  }

  /** Resets every D2MOO-exported render layer without changing path flags. */
  public void clearExportedTileIds() {
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        floorIds[y][x] = -1;
        shadowIds[y][x] = -1;
        for (int layer = 0; layer < MAX_WALL_LAYERS; layer++) {
          wallIds[layer][y][x] = -1;
        }
      }
    }
  }

  public boolean inBounds(int x, int y) {
    return x >= 0 && x < width && y >= 0 && y < height;
  }
}

