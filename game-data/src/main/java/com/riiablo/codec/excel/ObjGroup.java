package com.riiablo.codec.excel;

/** Physical-row indexed D2Common DATATBLS_GetObjGroupTxtRecord table. */
public class ObjGroup extends Excel<ObjGroup.Entry> {
  @Excel.Index
  public static class Entry extends Excel.Entry {
    @Column public String GroupName;
    @Column(format = "ID%d", endIndex = 8) public int[] ID;
    @Column(format = "DENSITY%d", endIndex = 8) public int[] DENSITY;
    @Column(format = "PROB%d", endIndex = 8) public int[] PROB;
    @Column public int SHRINES;
    @Column public int WELLS;
  }
}
