package com.riiablo.codec.excel;

/** Native COMPOSIT object animation tokens, indexed by physical Objects row. */
public class ObjType extends Excel<ObjType.Entry> {
  @Excel.Index
  public static class Entry extends Excel.Entry {
    @Column public String Name;
    @Column public String Token;
  }
}
