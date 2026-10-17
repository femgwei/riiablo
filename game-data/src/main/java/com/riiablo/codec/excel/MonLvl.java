package com.riiablo.codec.excel;

/**
 * Monster Level table - contains level-based stat multipliers for monsters.
 * 
 * Each row represents a level (0-99), and contains multipliers for:
 * - HP (Hit Points)
 * - AC (Armor Class)
 * - TH (To Hit / Attack Rating)
 * - DM (Damage)
 * - XP (Experience)
 * 
 * For each stat, there are values for Normal, Nightmare, and Hell difficulties.
 * There are also "L-" prefixed versions for expansion (LoD) content.
 * 
 * Reference: D2MOD source/D2Common/include/DataTbls/MonsterTbls.h:82
 */
@Excel.Binned
public class MonLvl extends Excel<MonLvl.Entry> {
  public static class Entry extends Excel.Entry {
    @Key
    @Column public int Level;
    
    // Classic version - Normal, Nightmare, Hell
    @Column(format = "AC%s", values = {"", "(N)", "(H)"}, endIndex = 3)
    public int     AC[];
    @Column(format = "TH%s", values = {"", "(N)", "(H)"}, endIndex = 3)
    public int     TH[];
    @Column(format = "HP%s", values = {"", "(N)", "(H)"}, endIndex = 3)
    public int     HP[];
    @Column(format = "DM%s", values = {"", "(N)", "(H)"}, endIndex = 3)
    public int     DM[];
    @Column(format = "XP%s", values = {"", "(N)", "(H)"}, endIndex = 3)
    public int     XP[];
    
    // Expansion (LoD) version - Normal, Nightmare, Hell
    @Column(format = "L-AC%s", values = {"", "(N)", "(H)"}, endIndex = 3)
    public int     LAC[];
    @Column(format = "L-TH%s", values = {"", "(N)", "(H)"}, endIndex = 3)
    public int     LTH[];
    @Column(format = "L-HP%s", values = {"", "(N)", "(H)"}, endIndex = 3)
    public int     LHP[];
    @Column(format = "L-DM%s", values = {"", "(N)", "(H)"}, endIndex = 3)
    public int     LDM[];
    @Column(format = "L-XP%s", values = {"", "(N)", "(H)"}, endIndex = 3)
    public int     LXP[];
  }
}
