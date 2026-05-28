package com.riiablo.drlg;

/**
 * DRLG 网格系统，对应 D2MOO 的 D2DrlgGridStrc。
 * 
 * 用于跟踪每个 8x8 网格单元的状态：
 * - Grid[0]: 存储 preset ID（如果有 preset）
 * - Grid[1]: 存储方向/标志（dwRoomFlags）
 * - Grid[2]: 存储打包信息（D2DrlgOutdoorPackedGrid2InfoStrc）
 * - Grid[3]: 存储其他信息（dwOutdoorFlagsEx）
 */
public class DrlgGrid {
  /** 网格宽度（8x8 tile 单元数） */
  public final int gridWidth;
  /** 网格高度（8x8 tile 单元数） */
  public final int gridHeight;
  
  /**
   * Grid[0]: preset ID（0 表示无 preset，否则是 LvlPrest.Def）
   * 单位：8x8 tile 网格
   */
  public final int[][] presetIds;
  
  /**
   * Grid[1]: 方向/标志（dwRoomFlags）
   * 单位：8x8 tile 网格
   */
  public final int[][] roomFlags;
  
  /**
   * Grid[2]: 打包信息（简化版，只存储关键标志）
   * - bHasPickedFile: 是否有选中的 preset 文件
   * - nPickedFile: 选中的文件索引
   * - bLvlLink: 是否是关卡链接
   * - bHasDirection: 是否有方向
   * - nUnkb08: 是否空白
   * 单位：8x8 tile 网格
   */
  public final PackedGrid2Info[][] grid2Info;
  
  /**
   * Grid[3]: 其他信息（dwOutdoorFlagsEx）
   * 单位：8x8 tile 网格
   */
  public final int[][] outdoorFlagsEx;
  
  /**
   * 打包的 Grid[2] 信息
   */
  public static class PackedGrid2Info {
    public boolean bHasPickedFile;
    public int nPickedFile; // 0-15
    public boolean bLvlLink;
    public boolean bHasDirection;
    public boolean nUnkb08;
    
    public PackedGrid2Info() {
      bHasPickedFile = false;
      nPickedFile = 0;
      bLvlLink = false;
      bHasDirection = false;
      nUnkb08 = false;
    }
  }
  
  public DrlgGrid(int gridWidth, int gridHeight) {
    this.gridWidth = gridWidth;
    this.gridHeight = gridHeight;
    
    this.presetIds = new int[gridHeight][gridWidth];
    this.roomFlags = new int[gridHeight][gridWidth];
    this.grid2Info = new PackedGrid2Info[gridHeight][gridWidth];
    this.outdoorFlagsEx = new int[gridHeight][gridWidth];
    
    // 初始化所有网格单元
    for (int y = 0; y < gridHeight; y++) {
      for (int x = 0; x < gridWidth; x++) {
        presetIds[y][x] = 0;
        roomFlags[y][x] = 0;
        grid2Info[y][x] = new PackedGrid2Info();
        outdoorFlagsEx[y][x] = 0;
      }
    }
  }
  
  public boolean inBounds(int gridX, int gridY) {
    return gridX >= 0 && gridX < gridWidth && gridY >= 0 && gridY < gridHeight;
  }
  
  /**
   * 检查网格单元是否可以生成（对应 DRLGOUTDOORS_TestGridCellSpawnValid）
   */
  public boolean canSpawn(int gridX, int gridY) {
    if (!inBounds(gridX, gridY)) return false;
    PackedGrid2Info info = grid2Info[gridY][gridX];
    // !(nUnkb00 || nUnkb07 || nUnkb08 || bHasPickedFile || nUnkb11 || nUnkb12)
    // 简化版：只检查关键标志
    return !info.nUnkb08 && !info.bHasPickedFile;
  }
  
  /**
   * 设置网格单元为空白（对应 DRLGOUTDOORS_SetBlankGridCell）
   */
  public void setBlank(int gridX, int gridY) {
    if (!inBounds(gridX, gridY)) return;
    presetIds[gridY][gridX] = 0;
    PackedGrid2Info info = grid2Info[gridY][gridX];
    info.bHasPickedFile = false;
    info.nPickedFile = 0;
    info.nUnkb08 = true;
  }
}
