package com.riiablo.codec.excel;

/** Native shrine definitions loaded from {@code Shrines.txt}. */
@Excel.Binned
public class Shrines extends Excel<Shrines.Entry> {
  @Excel.Index
  public static class Entry extends Excel.Entry {
    @Column public int Code;
    @Column public int Arg0;
    @Column public int Arg1;
    @Column(format = "Duration in frames") public int DurationInFrames;
    @Column(format = "reset time in minutes") public int ResetTimeInMinutes;
    @Column(format = "rarity") public int Rarity;
    @Column(format = "view name") public String ViewName;
    @Column(format = "niftyphrase") public String NiftyPhrase;
    @Column(format = "effectclass") public int EffectClass;
    @Column public int LevelMin;

    @Override
    public String toString() {
      return ViewName;
    }
  }
}
