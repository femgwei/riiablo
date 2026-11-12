package com.riiablo.codec.excel;

/** Ordered equipment tiers; duplicate monster names are intentional. */
public class MonEquip extends Excel<MonEquip.Entry> {
  public static class Entry extends Excel.Entry {
    @Key @Column public String monster;
    @Column public int level;
    @Column public boolean oninit;
    @Column(format = "item%d", startIndex = 1, endIndex = 4) public String[] item;
    @Column(format = "loc%d", startIndex = 1, endIndex = 4) public String[] loc;
    @Column(format = "mod%d", startIndex = 1, endIndex = 4) public int[] mod;
  }
}
