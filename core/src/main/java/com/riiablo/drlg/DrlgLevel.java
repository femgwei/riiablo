package com.riiablo.drlg;

import com.riiablo.codec.excel.Levels;

/**
 * 单个关卡的 DRLG 视图（简化版），对应 D2MOO 的 D2DrlgLevelStrc。
 *
 * 当前用于承载元数据、DRLG 网格系统（8x8 单元）和 TileGrid（tile 级别），
 * 用来“影子记录”生成结果，不参与正式渲染或逻辑判断。
 */
public class DrlgLevel {
  /** 对应 Levels.txt 的记录 */
  public final Levels.Entry levelsEntry;

  /** LevelId / Act / DrlgType / LevelType / SubType 等元信息，直接从 Levels.Entry 拷贝 */
  public final int levelId;
  public final int act;
  public final int drlgType;
  public final int levelType;
  public final int subType;

  /** 关卡在当前布局下的宽高（tile 数；默认来自 SizeX[diff]/SizeY[diff]） */
  public final int tilesX;
  public final int tilesY;

  /** DRLG 网格系统（8x8 tile 单元），对应 D2MOO 的 D2DrlgOutdoorInfoStrc.pGrid[0-3] */
  public final DrlgGrid drlgGrid;
  
  /** 对应的 Tile 网格（tile 级别），用于记录最终生成的地板 ID */
  public final TileGrid grid;

  public DrlgLevel(Levels.Entry entry, int diff) {
    this(entry, diff, entry.SizeX[diff], entry.SizeY[diff]);
  }

  /**
   * 支持自定义尺寸，用于 D2MOO 按 seed 旋转或调整过的布局区域；
   * TileGrid、DrlgGrid 和 Zone 必须使用同一组最终宽高。
   */
  public DrlgLevel(Levels.Entry entry, int diff, int customTilesX, int customTilesY) {
    this.levelsEntry = entry;
    this.levelId = entry.Id;
    this.act = entry.Act;
    this.drlgType = entry.DrlgType;
    this.levelType = entry.LevelType;
    this.subType = entry.SubType;

    this.tilesX = customTilesX;
    this.tilesY = customTilesY;
    
    // 初始化 DRLG 网格系统（8x8 tile 单元）
    int gridWidth = tilesX / 8;  // 每个网格单元是 8x8 tiles
    int gridHeight = tilesY / 8;
    this.drlgGrid = new DrlgGrid(gridWidth, gridHeight);
    
    // 初始化 Tile 网格（tile 级别）
    this.grid = new TileGrid(tilesX, tilesY);
  }

  @Override
  public String toString() {
    return "DrlgLevel{" +
        "levelId=" + levelId +
        ", act=" + act +
        ", drlgType=" + drlgType +
        ", levelType=" + levelType +
        ", subType=" + subType +
        ", tilesX=" + tilesX +
        ", tilesY=" + tilesY +
        ", gridSize=" + drlgGrid.gridWidth + "x" + drlgGrid.gridHeight +
        '}';
  }
}

