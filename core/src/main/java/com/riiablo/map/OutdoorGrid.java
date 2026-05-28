package com.riiablo.map;

import com.badlogic.gdx.utils.IntArray;
import com.riiablo.codec.excel.Levels;

/**
 * 室外区域网格系统
 * 
 * 参考 D2MOD: DRLGGRID
 * 用于精细控制野外区域的地形生成，每个网格为 8x8 tiles
 */
public class OutdoorGrid {
  private static final int GRID_SIZE = 8; // 每个网格 8x8 tiles

  /**
   * 网格信息（打包的）
   * 参考 D2MOD: D2DrlgOutdoorPackedGrid2InfoStrc
   */
  public static class PackedGrid2Info {
    /** 是否有预设文件 */
    public boolean hasPickedFile;
    /** 预设文件索引 */
    public int pickedFile;
    /** 未知标志 b08 */
    public boolean unkb08;
    /** 打包值（用于存储到网格中） */
    public int packedValue;

    public void pack() {
      packedValue = 0;
      if (hasPickedFile) {
        packedValue |= (pickedFile & 0xF) << 16;
      }
      if (unkb08) {
        packedValue |= 1 << 8;
      }
    }

    public void unpack(int packed) {
      packedValue = packed;
      hasPickedFile = ((packed >> 16) & 0xF) != 0;
      if (hasPickedFile) {
        pickedFile = (packed >> 16) & 0xF;
      }
      unkb08 = ((packed >> 8) & 1) != 0;
    }
  }

  /**
   * 网格数据
   * 参考 D2MOD: D2DrlgGridStrc
   */
  public static class Grid {
    private int width;
    private int height;
    private IntArray cells;

    public Grid(int width, int height) {
      this.width = width;
      this.height = height;
      this.cells = new IntArray(width * height);
      this.cells.setSize(width * height);
      // 初始化所有单元格为 0
      for (int i = 0; i < width * height; i++) {
        this.cells.add(0);
      }
    }

    public int get(int x, int y) {
      if (x < 0 || x >= width || y < 0 || y >= height) {
        return 0;
      }
      return cells.get(y * width + x);
    }

    public void set(int x, int y, int value) {
      if (x < 0 || x >= width || y < 0 || y >= height) {
        return;
      }
      cells.set(y * width + x, value);
    }

    public void alter(int x, int y, int mask, int value, int operation) {
      int current = get(x, y);
      int result;
      switch (operation) {
        case FLAG_OPERATION_OR:
          result = (current & ~mask) | (value & mask);
          break;
        case FLAG_OPERATION_AND:
          result = current & value;
          break;
        case FLAG_OPERATION_AND_NEGATED:
          result = current & ~mask;
          break;
        case FLAG_OPERATION_OVERWRITE:
          result = value;
          break;
        default:
          result = current;
          break;
      }
      set(x, y, result);
    }

    public void dispose() {
      if (cells != null) {
        cells.clear();
        cells = null;
      }
    }
  }

  public static final int FLAG_OPERATION_OR = 0;
  public static final int FLAG_OPERATION_AND = 1;
  public static final int FLAG_OPERATION_AND_NEGATED = 2;
  public static final int FLAG_OPERATION_OVERWRITE = 3;

  /**
   * 室外区域网格信息
   * 参考 D2MOD: D2DrlgOutdoorInfoStrc
   */
  public static class OutdoorInfo {
    /** 网格宽度（8x8 tiles 为单位） */
    public int gridWidth;
    /** 网格高度（8x8 tiles 为单位） */
    public int gridHeight;
    
    /** Grid[0]: 预设ID */
    public Grid presetGrid;
    /** Grid[1]: 区域标志 */
    public Grid areaGrid;
    /** Grid[2]: 打包的网格信息（包含文件选择等） */
    public Grid packedGrid2;
    /** Grid[3]: 其他信息 */
    public Grid otherGrid;

    /** 标志位 */
    public int flags;

    public OutdoorInfo(int width, int height) {
      // 将 tile 尺寸转换为网格尺寸（8 tiles = 1 grid）
      this.gridWidth = width / GRID_SIZE;
      this.gridHeight = height / GRID_SIZE;
      
      // 初始化所有网格
      this.presetGrid = new Grid(gridWidth, gridHeight);
      this.areaGrid = new Grid(gridWidth, gridHeight);
      this.packedGrid2 = new Grid(gridWidth, gridHeight);
      this.otherGrid = new Grid(gridWidth, gridHeight);
    }

    /**
     * 获取打包的网格信息
     * 参考 D2MOD: DRLGOUTDOORS_GetPackedGrid2Info
     */
    public PackedGrid2Info getPackedGrid2Info(int x, int y) {
      PackedGrid2Info info = new PackedGrid2Info();
      int packed = packedGrid2.get(x, y);
      info.unpack(packed);
      return info;
    }

    /**
     * 设置打包的网格信息
     */
    public void setPackedGrid2Info(int x, int y, PackedGrid2Info info) {
      info.pack();
      packedGrid2.set(x, y, info.packedValue);
    }

    public void dispose() {
      if (presetGrid != null) {
        presetGrid.dispose();
        presetGrid = null;
      }
      if (areaGrid != null) {
        areaGrid.dispose();
        areaGrid = null;
      }
      if (packedGrid2 != null) {
        packedGrid2.dispose();
        packedGrid2 = null;
      }
      if (otherGrid != null) {
        otherGrid.dispose();
        otherGrid = null;
      }
    }
  }

  /**
   * 根据 LevelType 获取 Dt1Mask
   * 参考 D2MOD: DRLGOUTDOORS_GenerateLevel (switch pLevel->nLevelType)
   */
  public static int getDt1MaskForLevelType(int levelType) {
    // 参考 D2MOD 中的常量定义
    // LVLTYPE_ACT1_WILDERNESS = 1
    // LVLTYPE_ACT2_DESERT = 2
    // LVLTYPE_ACT3_JUNGLE = 3
    // LVLTYPE_ACT3_KURAST = 4
    // LVLTYPE_ACT4_MESA = 5
    // LVLTYPE_ACT4_LAVA = 6
    // LVLTYPE_ACT5_SIEGE = 7
    // LVLTYPE_ACT5_BARRICADE = 8

    switch (levelType) {
      case 1: // LVLTYPE_ACT1_WILDERNESS
        return 0x44103;
      case 3: // LVLTYPE_ACT3_JUNGLE
        return 0x04;
      case 2: // LVLTYPE_ACT2_DESERT
      case 4: // LVLTYPE_ACT3_KURAST
      case 5: // LVLTYPE_ACT4_MESA
      case 6: // LVLTYPE_ACT4_LAVA
        return 0x01;
      case 7: // LVLTYPE_ACT5_SIEGE
      case 8: // LVLTYPE_ACT5_BARRICADE
        return 0x11;
      default:
        return 0x00;
    }
  }

  /**
   * 根据 Level 的 Act 和 LevelType 获取 Dt1Mask
   * 对于 Act1 的野外区域，即使 LevelType=2，也应该使用 Act1 Wilderness 的 mask
   * 参考 D2MOD: DRLGOUTDOORS_GenerateLevel
   * 
   * 注意：Levels.txt 中的 Act 字段是 0-based（0=Act1, 1=Act2, ...）
   */
  public static int getDt1MaskForLevel(Levels.Entry level) {
    // 如果是 Act1 的野外区域（非城镇），使用 Act1 Wilderness 的 mask
    // Act1 的野外区域（如 Blood Moor, Cold Plains, Stony Field）的 LevelType 可能是 2
    // 但应该使用 Act1 Wilderness 的 DT1 文件（mask = 0x44103）
    // Levels.txt 中的 Act 字段是 0-based：0=Act1, 1=Act2, 2=Act3, 3=Act4, 4=Act5
    if (level.Act == 0) {
      // Act1 的城镇（LevelType=1）和非城镇区域都使用 Act1 Wilderness 的 mask
      // 因为它们的 DT1 文件都在 Act1/Outdoors 目录下
      return 0x44103;
    }
    
    // 其他 Act 根据 LevelType 判断
    return getDt1MaskForLevelType(level.LevelType);
  }

  public static final int GRID_SIZE_TILES = GRID_SIZE;
}
