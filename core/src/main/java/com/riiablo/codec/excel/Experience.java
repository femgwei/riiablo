package com.riiablo.codec.excel;

/** Native Experience.txt level thresholds and high-level experience ratios. */
@Excel.Binned
public class Experience extends Excel<Experience.Entry> {
  public static class Entry extends Excel.Entry {
    @Key @Column public String Level;
    @Column public long Amazon;
    @Column public long Sorceress;
    @Column public long Necromancer;
    @Column public long Paladin;
    @Column public long Barbarian;
    @Column public long Druid;
    @Column public long Assassin;
    @Column public int ExpRatio;

    public long threshold(int classId) {
      switch (classId) {
        case 0: return Amazon;
        case 1: return Sorceress;
        case 2: return Necromancer;
        case 3: return Paladin;
        case 4: return Barbarian;
        case 5: return Druid;
        case 6: return Assassin;
        default: return Amazon;
      }
    }
  }

  public Entry level(int level) {
    return get(Integer.toString(level));
  }

  public Entry max() {
    return get("MaxLvl");
  }
}
