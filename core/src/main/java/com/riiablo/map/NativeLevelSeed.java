package com.riiablo.map;

import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;

/** Adapter for the native D2MOO DRLG level-seed contract. */
public final class NativeLevelSeed {
  private NativeLevelSeed() {}

  /** Returns D2MOO's Drlg.startSeed for a game low seed. */
  public static int startSeed(int gameSeed) {
    D2Seed seed = new D2Seed();
    Seed.initLowSeed(seed, gameSeed);
    return (int) Seed.rollRandomNumber(seed);
  }

  /** Returns the native level seed ({@code levelId + drlg.startSeed}). */
  public static int forLevel(int gameSeed, int levelId) {
    return levelId + startSeed(gameSeed);
  }

  /** Context overload; Act and difficulty select tables, not the 1.10f seed. */
  public static int forLevel(int gameSeed, int act, int levelId, int difficulty) {
    return forLevel(gameSeed, levelId);
  }
}
