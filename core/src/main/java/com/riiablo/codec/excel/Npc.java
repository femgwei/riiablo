package com.riiablo.codec.excel;

/** Native Npc.txt transaction multipliers. Values use the game's 1024 scale. */
@Excel.Binned
public class Npc extends Excel<Npc.Entry> {
  public static class Entry extends Excel.Entry {
    @Key @Column public String npc;
    @Column(format = "sell mult") public int sellMult;
    @Column(format = "buy mult") public int buyMult;
    @Column(format = "rep mult") public int repMult;
    @Column(format = "questflag A") public String questFlagA;
    @Column(format = "questflag B") public String questFlagB;
    @Column(format = "questflag C") public String questFlagC;
    @Column(format = "questsellmult A") public int questSellMultA;
    @Column(format = "questsellmult B") public int questSellMultB;
    @Column(format = "questsellmult C") public int questSellMultC;
    @Column(format = "questbuymult A") public int questBuyMultA;
    @Column(format = "questbuymult B") public int questBuyMultB;
    @Column(format = "questbuymult C") public int questBuyMultC;
    @Column(format = "questrepmult A") public int questRepMultA;
    @Column(format = "questrepmult B") public int questRepMultB;
    @Column(format = "questrepmult C") public int questRepMultC;
    @Column(format = "max buy") public int maxBuy;
    @Column(format = "max buy (N)") public int maxBuyNormal;
    @Column(format = "max buy (H)") public int maxBuyHell;
  }
}
