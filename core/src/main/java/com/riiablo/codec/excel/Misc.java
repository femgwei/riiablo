package com.riiablo.codec.excel;

@Excel.Binned
public class Misc extends Excel<Misc.Entry> {
  public static class Entry extends ItemEntry {
    @Column public int     pSpell;
    @Column public String  state;
    @Column public String  len;
    @Column(format = "stat%d", startIndex = 1, endIndex = 4)
    public String[] stat;
    @Column(format = "calc%d", startIndex = 1, endIndex = 4)
    public String[] calc;
    @Column public int     spelldesc;
    @Column public String  spelldescstr;
  }
}
