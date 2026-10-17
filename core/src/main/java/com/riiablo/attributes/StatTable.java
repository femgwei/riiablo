package com.riiablo.attributes;

import com.riiablo.codec.excel.ItemStatCost;
import java.util.Objects;

/** Encoding metadata owned by one data set, independent of the active client. */
public final class StatTable {
  private final ItemStatCost table;
  public StatTable(ItemStatCost table) { this.table = Objects.requireNonNull(table, "table"); }
  public ItemStatCost.Entry entry(short stat) { return table.get(stat); }
  public ItemStatCost.Entry entry(String stat) { return table.get(stat); }
  public short index(String stat) { return (short) table.index(stat); }
  public int encode(short stat, int value) { return value << entry(stat).ValShift; }
  public int decode(short stat, int value) { return value >>> entry(stat).ValShift; }
}
