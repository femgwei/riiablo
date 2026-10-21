package com.riiablo.codec.excel;

/** Data-only DATATBLS bounds and fallback semantics shared by maps and units. */
public final class NativeTableValues {
  private NativeTableValues() {}

  public static int difficulty(int difficulty) {
    return Math.max(0, Math.min(2, difficulty));
  }

  public static int value(int[] values, int difficulty, int fallback) {
    int index = difficulty(difficulty);
    if (values == null || values.length == 0) return fallback;
    // Missing difficulty columns use Normal, as in the existing DATATBLS adapter.
    return values[index < values.length ? index : 0];
  }

  public static int levelSizeX(Levels.Entry level, int difficulty, int fallback) {
    return Math.max(1, value(level == null ? null : level.SizeX, difficulty, fallback));
  }

  public static int levelSizeY(Levels.Entry level, int difficulty, int fallback) {
    return Math.max(1, value(level == null ? null : level.SizeY, difficulty, fallback));
  }

  public static int minGroup(MonStats.Entry monster) {
    return monster == null ? 1 : Math.max(1, monster.MinGrp);
  }

  public static int maxGroup(MonStats.Entry monster) {
    return monster == null ? 1 : Math.max(minGroup(monster), monster.MaxGrp);
  }
}
