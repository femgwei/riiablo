package com.riiablo.engine.server.quest;

import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;

/**
 * Deterministic Act II tomb selection used by the native DRLG and A2Q6.
 *
 * <p>D2Common does not choose the staff tomb from the visible map order.  It
 * advances the Act II DRLG seed once for {@code dwStartSeed}, then draws two
 * different values in {@code [0, 7)}.  Keeping this small value object
 * independent of Map makes the rule usable by headless servers, map builders,
 * and object resolution without copying D2MOO's native memory model.</p>
 */
public final class Act2TombSelection {
  public static final int FIRST_TOMB_LEVEL = D2LevelIds.LEVEL_TALRASHASTOMB1;
  public static final int LAST_TOMB_LEVEL = D2LevelIds.LEVEL_TALRASHASTOMB7;
  private static final int TOMB_COUNT = LAST_TOMB_LEVEL - FIRST_TOMB_LEVEL + 1;

  /** D2Game::ACT2Q6_GetObjectIdForArcaneThing order. */
  private static final int[] ARCANE_OBJECT_IDS = {313, 312, 308, 310, 311, 309, 307};

  private final int staffTombLevel;
  private final int bossTombLevel;

  private Act2TombSelection(int staffTombLevel, int bossTombLevel) {
    this.staffTombLevel = staffTombLevel;
    this.bossTombLevel = bossTombLevel;
  }

  /** Reproduces the Act II branch of D2Common::DRLG_AllocDrlg. */
  public static Act2TombSelection forGameSeed(int gameSeed) {
    D2Seed seed = new D2Seed();
    Seed.initLowSeed(seed, gameSeed);
    // Native DRLG stores this value before choosing the two tomb offsets.
    Seed.rollRandomNumber(seed);

    int staffOffset;
    int bossOffset;
    do {
      staffOffset = Seed.rollLimitedRandomNumber(seed, TOMB_COUNT);
      bossOffset = Seed.rollLimitedRandomNumber(seed, TOMB_COUNT);
    } while (staffOffset == bossOffset);
    return new Act2TombSelection(FIRST_TOMB_LEVEL + staffOffset,
        FIRST_TOMB_LEVEL + bossOffset);
  }

  public int staffTombLevel() {
    return staffTombLevel;
  }

  public int bossTombLevel() {
    return bossTombLevel;
  }

  public boolean isTomb(int levelId) {
    return levelId >= FIRST_TOMB_LEVEL && levelId <= LAST_TOMB_LEVEL;
  }

  public boolean isStaffTomb(int levelId) {
    return levelId == staffTombLevel;
  }

  public boolean isBossTomb(int levelId) {
    return levelId == bossTombLevel;
  }

  /**
   * Returns the Objects.txt class used for the first Arcane Thing in a tomb.
   * The staff tomb has no Arcane Symbol.  Other tombs consume the native
   * sequence in tomb-id order with the staff slot removed.
   */
  public int arcaneSymbolObjectFor(int levelId) {
    if (!isTomb(levelId) || isStaffTomb(levelId)) return -1;
    int slot = 0;
    for (int tomb = FIRST_TOMB_LEVEL; tomb <= levelId; tomb++) {
      if (tomb == staffTombLevel) continue;
      if (tomb == levelId) return ARCANE_OBJECT_IDS[slot % (ARCANE_OBJECT_IDS.length - 1)];
      slot++;
    }
    return -1;
  }

  /** Exposed for tests and diagnostics without leaking the mutable array. */
  static int[] arcaneObjectOrder() {
    return ARCANE_OBJECT_IDS.clone();
  }

  @Override
  public String toString() {
    return "Act2TombSelection{staff=" + staffTombLevel
        + ", boss=" + bossTombLevel + '}';
  }
}
