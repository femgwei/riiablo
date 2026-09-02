package com.riiablo.engine.server.monster;

import com.riiablo.codec.excel.Levels;

/**
 * Small, data-only portion of D2MOO's MONSTERREGION initialization.
 *
 * D2MOO keeps separate normal and nightmare/hell monster lists in Levels.txt
 * and rolls density on a 0..99999 scale.  The map builders use this helper so
 * they do not silently fall back to the normal list when a difficulty list is
 * present, while tests can verify the selection without booting a world.
 */
public final class NativeMonsterRegion {
  private NativeMonsterRegion() {}

  /** Returns the difficulty-specific list, falling back only when it is empty. */
  public static String[] monsterColumns(Levels.Entry level, int difficulty) {
    if (level == null) return new String[0];
    String[] preferred = difficulty > 0 ? level.nmon : level.mon;
    if (hasValue(preferred)) return preferred;
    return level.mon == null ? new String[0] : level.mon;
  }

  /** D2MOO caps the number of selected region entries at thirteen. */
  public static int selectedEntryCount(Levels.Entry level, int difficulty) {
    if (level == null || level.NumMon <= 0) return 0;
    return Math.min(Math.min(level.NumMon, 13), monsterColumns(level, difficulty).length);
  }

  /** Inclusive native density check: roll 0..99999 succeeds when roll <= MonDen. */
  public static boolean densityRoll(int monDen, int roll) {
    if (monDen <= 0) return false;
    int normalizedRoll = Math.floorMod(roll, 100000);
    return normalizedRoll <= Math.min(monDen, 100000);
  }

  private static boolean hasValue(String[] values) {
    if (values == null) return false;
    for (String value : values) {
      if (value != null && !value.isEmpty() && !"0".equals(value)) return true;
    }
    return false;
  }
}
