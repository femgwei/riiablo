package com.riiablo.drlg;

/**
 * DRLG 生成用的 Tile 网格（简化版），对应 D2MOO 的 D2DrlgTileGridStrc。
 *
 * 这里只记录 floor 层的一个整型 ID，后续可以扩展为多层/标记。
 * 支持 D2MOD 风格的 DirtPathGrid：先标记路径经过的格子，再按连通性生成土路瓦片。
 */
public class TileGrid {
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
   * D2MOD: pDirtPathGrid 等价物。标记路径经过的格子，供 DRLG_OUTDOORS_GenerateDirtPath 使用。
   * dirtPathFlags[y][x] == true 表示该格有路径经过。
   */
  public final boolean[][] dirtPathFlags;

  public TileGrid(int width, int height) {
    this.width = width;
    this.height = height;
    this.floorIds = new int[height][width];
    this.dirtPathFlags = new boolean[height][width];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        floorIds[y][x] = -1;
        dirtPathFlags[y][x] = false;
      }
    }
  }

  public boolean inBounds(int x, int y) {
    return x >= 0 && x < width && y >= 0 && y < height;
  }
}

