package com.riiablo.map;

import com.d2moo.common.drlg.D2LvlPrestIds;
import com.d2moo.common.seed.Seed;
import com.d2moo.common.drlg.D2Seed;

/** Native Act II Arcane Sanctuary branch/preset mapping. */
public final class Act2ArcaneSanctuaryLayout {
  public enum Direction {
    NORTH(3, D2LvlPrestIds.LVLPREST_ACT2_ARCANE_N,
        D2LvlPrestIds.LVLPREST_ACT2_ARCANE_SUMMONER_N),
    EAST(0, D2LvlPrestIds.LVLPREST_ACT2_ARCANE_E,
        D2LvlPrestIds.LVLPREST_ACT2_ARCANE_SUMMONER_E),
    SOUTH(1, D2LvlPrestIds.LVLPREST_ACT2_ARCANE_S,
        D2LvlPrestIds.LVLPREST_ACT2_ARCANE_SUMMONER_S),
    WEST(2, D2LvlPrestIds.LVLPREST_ACT2_ARCANE_W,
        D2LvlPrestIds.LVLPREST_ACT2_ARCANE_SUMMONER_W);

    private final int nativeRotation;
    private final int branchPresetDef;
    private final int summonerPresetDef;

    Direction(int nativeRotation, int branchPresetDef, int summonerPresetDef) {
      this.nativeRotation = nativeRotation;
      this.branchPresetDef = branchPresetDef;
      this.summonerPresetDef = summonerPresetDef;
    }

    public int nativeRotation() {
      return nativeRotation;
    }

    public int branchPresetDef() {
      return branchPresetDef;
    }

    public int summonerPresetDef() {
      return summonerPresetDef;
    }
  }

  private Act2ArcaneSanctuaryLayout() {}

  /** Matches D2MOO's {@code SEED_RollRandomNumber(level->pSeed) & 3}. */
  public static Direction fromLevelSeed(int levelSeed) {
    D2Seed seed = new D2Seed();
    Seed.initLowSeed(seed, levelSeed);
    int index = (int) (Seed.rollRandomNumber(seed) & 3L);
    return Direction.values()[index];
  }

  public static boolean isSummonerPreset(int def) {
    for (Direction direction : Direction.values()) {
      if (direction.summonerPresetDef == def) return true;
    }
    return false;
  }
}
