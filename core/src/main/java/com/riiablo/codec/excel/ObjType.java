package com.riiablo.codec.excel;

/** Native COMPOSIT object animation tokens, indexed by physical Objects row. */
public class ObjType extends Excel<ObjType.Entry> {
  @Override
  protected void put(int id, Entry value) {
    // COMPOSIT_GetCof zero-terminates space-padded raw tokens before
    // constructing paths. ObjType row 523 contains "ss ".
    if (value.Token != null) {
      int padding = value.Token.indexOf(' ');
      if (padding >= 0) value.Token = value.Token.substring(0, padding);
    }
    super.put(id, value);
  }
  @Excel.Index
  public static class Entry extends Excel.Entry {
    @Column public String Name;
    @Column public String Token;
  }
}
