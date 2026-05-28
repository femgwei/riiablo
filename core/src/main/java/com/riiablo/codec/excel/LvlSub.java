package com.riiablo.codec.excel;

/**
 * LvlSub.txt - 野外子类型 / 替换规则表
 *
 * 参考：
 * - D2MOO: D2LvlSubTxt + DATATBLS_LoadLvlSubTxt
 * - OpenDiablo2: LevelSubstitutionRecord（level_substitutions_record.go）
 */
@Excel.Binned
public class LvlSub extends Excel<LvlSub.Entry> {

  public static class Entry extends Excel.Entry {
    @Override
    public String toString() {
      return "LvlSub[" + Name + ", Type=" + Type + ", File=" + File + "]";
    }

    /** Name - 说明用，无逻辑作用 */
    @Column
    public String Name;

    /**
     * Type - Levels.txt 中 SubType 使用的 ID。
     * 同一 Type 的多行组成一组，通过在组内的索引映射到 SubTheme / SubWaypoint / SubShrine。
     * 对应 LvlSub.txt 的 Type 列。
     */
    @Column
    public int Type;

    /** File - 对应的 ds1 文件路径 */
    @Column
    public String File;

    /** Expansion - 0=经典，1=资料片 */
    @Column
    public boolean Expansion;

    /** BordType - 边界类型（0/1/2，非墙体一般为 -1） */
    @Column
    public int BordType;

    /** GridSize - 网格尺寸（1 或 2，通常表示 4x4 等块大小） */
    @Column
    public int GridSize;

    /** Dt1Mask - dt1 掩码，部分行对应 LvlTypes 的条目 */
    @Column
    public int Dt1Mask;

    /** Prob0-Prob4 - 不同子通道的出现概率 */
    @Column(format = "Prob%d", startIndex = 0, endIndex = 5)
    public int Prob[];

    /** Trials0-Trials4 - 地板 / 预设试验次数（生成尝试次数或混合概率） */
    @Column(format = "Trials%d", startIndex = 0, endIndex = 5)
    public int Trials[];

    /** Max0-Max4 - 每个子通道的最大生成数量（以 Grid 为单位） */
    @Column(format = "Max%d", startIndex = 0, endIndex = 5)
    public int Max[];
  }

  /**
   * 按 Type 分组获取记录（对应 Levels.SubType）
   */
  public Entry[] getByType(int type) {
    java.util.ArrayList<Entry> list = new java.util.ArrayList<>();
    // 注意：Excel 已经实现 Iterable<T>，直接遍历实际条目即可，
    // 不要使用 orderedEntries（某些表可能不会填充该结构）。
    for (Entry sub : this) {
      if (sub != null && sub.Type == type) {
        list.add(sub);
      }
    }
    return list.toArray(new Entry[0]);
  }
}

