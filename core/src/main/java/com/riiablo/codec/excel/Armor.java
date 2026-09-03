package com.riiablo.codec.excel;

@Excel.Binned
public class Armor extends Excel<Armor.Entry> {
  public static class Entry extends ItemEntry {
    @Column public int     Torso;
    @Column public int     Legs;
    @Column public int     rArm;
    @Column public int     lArm;
    @Column public int     lSPad;
    @Column public int     rSPad;
    @Column public int     minac;
    @Column public int     maxac;
    @Column public int     reqstr;
    /** Native weapon-style stat scaling used by kick damage. */
    @Column public int     StrBonus;
    @Column public int     DexBonus;
    @Column public int     durability;
    @Column public int     block;
  }
}
