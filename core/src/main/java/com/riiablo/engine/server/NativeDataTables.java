package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonLvl;
import com.riiablo.codec.excel.MonStats;

/**
 * Safe, data-only accessors mirroring the bounds and fallback rules used by
 * D2Common's DATATBLS helpers.  Keeping these rules in one place prevents a
 * missing difficulty column from becoming an accidental array exception or a
 * silent use of the wrong difficulty's data.
 */
public final class NativeDataTables {
  private NativeDataTables() {}

  public static int difficulty(int difficulty) {
    return Math.max(Riiablo.NORMAL, Math.min(Riiablo.HELL, difficulty));
  }

  public static int value(int[] values, int difficulty, int fallback) {
    int index = difficulty(difficulty);
    if (values == null || values.length == 0) return fallback;
    // A number of shipped tables only contain the Normal column.  Native
    // DATATBLS callers use that column for higher difficulties when the
    // difficulty-specific field is absent; they do not invent a zero value.
    return values[index < values.length ? index : 0];
  }

  public static int levelSizeX(Levels.Entry level, int difficulty, int fallback) {
    return Math.max(1, value(level == null ? null : level.SizeX, difficulty, fallback));
  }

  public static int levelSizeY(Levels.Entry level, int difficulty, int fallback) {
    return Math.max(1, value(level == null ? null : level.SizeY, difficulty, fallback));
  }

  public static int areaLevel(Levels.Entry level, int difficulty, boolean expansion) {
    if (level == null) return 0;
    int[] values = expansion ? level.MonLvlEx : level.MonLvl;
    return value(values, difficulty, 0);
  }

  public static int monsterLevel(MonStats.Entry monster, int difficulty) {
    if (monster == null) return 1;
    return Math.max(1, value(monster.Level, difficulty, 1));
  }

  public static int minGroup(MonStats.Entry monster) {
    return monster == null ? 1 : Math.max(1, monster.MinGrp);
  }

  public static int maxGroup(MonStats.Entry monster) {
    if (monster == null) return 1;
    return Math.max(minGroup(monster), monster.MaxGrp);
  }

  public static int partyMin(MonStats.Entry monster) {
    return monster == null ? 0 : Math.max(0, monster.PartyMin);
  }

  public static int partyMax(MonStats.Entry monster) {
    if (monster == null) return 0;
    return Math.max(partyMin(monster), monster.PartyMax);
  }

  public static int monLvlValue(int[] values, int difficulty) {
    return value(values, difficulty, 0);
  }

  public static int monLvlValue(MonLvl.Entry level, int[] values, int difficulty) {
    return level == null ? 0 : monLvlValue(values, difficulty);
  }
}
