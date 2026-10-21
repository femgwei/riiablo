package com.riiablo.map;

import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.NativeTableValues;

/** Map data access without server runtime or global session dependencies. */
public final class MapDataPort {
  private MapDataPort() {}

  public static int value(int[] values, int difficulty, int fallback) {
    return NativeTableValues.value(values, difficulty, fallback);
  }

  public static int levelSizeX(Levels.Entry level, int difficulty, int fallback) {
    return NativeTableValues.levelSizeX(level, difficulty, fallback);
  }

  public static int levelSizeY(Levels.Entry level, int difficulty, int fallback) {
    return NativeTableValues.levelSizeY(level, difficulty, fallback);
  }

  public static int minGroup(MonStats.Entry monster) {
    return NativeTableValues.minGroup(monster);
  }

  public static int maxGroup(MonStats.Entry monster) {
    return NativeTableValues.maxGroup(monster);
  }
}
