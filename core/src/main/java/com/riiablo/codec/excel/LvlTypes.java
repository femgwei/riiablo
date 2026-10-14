package com.riiablo.codec.excel;

@Excel.Binned
public class LvlTypes extends Excel<LvlTypes.Entry> {
  // DATATBLS_GetLevelTypesTxtRecord indexes the compiled source rows directly.
  // The authored Id column is descriptive, not the lookup key.
  @Excel.Index
  public static class Entry extends Excel.Entry {
    @Override
    public String toString() {
      return Name;
    }

    @Column public String  Name;
    @Column
    public int     Id;
    @Column(format = "File %d", startIndex = 1, endIndex = 33)
    public String  File[];
    @Column public boolean Beta;
    @Column public int     Act;
    @Column public boolean Expansion;
  }
}
