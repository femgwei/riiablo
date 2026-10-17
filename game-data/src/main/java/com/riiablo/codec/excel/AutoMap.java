package com.riiablo.codec.excel;

/**
 * AutoMap.txt 数据表
 * 定义小地图显示使用的瓷砖图标映射规则
 * 
 * 参考: D2MOD D2AutomapCellStrc / OpenDiablo2 AutoMapRecord
 */
@Excel.Binned
public class AutoMap extends Excel<AutoMap.Entry> {
  
  public static class Entry extends Excel.Entry {
    @Override
    public String toString() {
      return LevelName + " - " + TileName;
    }

    /**
     * 关卡名称，格式为 "章节号 关卡类型"
     * 例如: "1 Barracks" 表示第一章的兵营
     */
    @Column
    public String LevelName;

    /**
     * 瓷砖名称，表示特定的瓷砖方向
     * 参考: https://d2mods.info/forum/kb/viewarticle?a=468
     */
    @Column
    public String TileName;

    /**
     * 样式索引，二维瓷砖数组的第一维索引
     * tiles[Style][]
     */
    @Column
    public int Style;

    /**
     * 起始序列，二维瓷砖数组的第二维起始索引
     * -1 表示忽略序列检查
     */
    @Column
    public int StartSequence;

    /**
     * 结束序列，二维瓷砖数组的第二维结束索引
     * -1 表示忽略序列检查
     */
    @Column
    public int EndSequence;

    // Type1-Type4 字段为注释字段，暂不加载

    /**
     * 小地图图标帧索引数组
     * 对应 MaxiMap(s).dc6 中的帧
     * -1 表示该位置无图标
     */
    @Column(startIndex = 1, endIndex = 5, format = "Cel%d")
    public int[] Cel;
  }
}
