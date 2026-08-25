package com.riiablo.engine.server.object;

import com.riiablo.codec.excel.Objects;
import com.riiablo.codec.excel.Shrines;

/** Selects a native shrine row using the rules in D2Game Objects.cpp. */
public final class NativeShrineResolver {
  private static final int FIRST_PRESET_SHRINE = 574;
  private static final int LAST_PRESET_SHRINE = 579;
  private static final int[][] PRESET_RANGES = {
      {2, 6}, {7, 7}, {8, 11}, {12, 12}, {1, 5}, {14, 14}
  };

  private NativeShrineResolver() {}

  public static int resolve(Shrines shrines, Objects.Entry object,
      int originalClassId, int levelId, int gameSeed, int x, int y) {
    if (shrines == null || shrines.size() == 0) return -1;
    NativeRandom random = new NativeRandom(objectSeed(
        gameSeed, levelId, originalClassId, x, y));

    if (originalClassId >= FIRST_PRESET_SHRINE
        && originalClassId <= LAST_PRESET_SHRINE) {
      int[] range = PRESET_RANGES[originalClassId - FIRST_PRESET_SHRINE];
      int width = range[1] - range[0];
      int shrineId = range[0] + (width <= 0 ? 0 : random.nextInt(width));
      // OBJECTS_SpawnShrine maps both exchange shrines to the health shrine.
      if (shrineId == 4 || shrineId == 5) shrineId = 2;
      return existingOrFallback(shrines, shrineId);
    }

    int shrineId;
    int parm0 = parm(object, 0);
    if (parm0 != 0) {
      int effectClass;
      switch (parm0 - 1) {
        case 0:
          effectClass = 2;
          break;
        case 1:
          effectClass = 3;
          break;
        default:
          effectClass = random.nextInt(10) == 0 ? 1 : 4;
          break;
      }
      shrineId = selectEffectClass(shrines, effectClass, levelId, random);
    } else {
      shrineId = selectAny(shrines, levelId, random);
    }

    // OBJECTS_InitFunction01_Shrine performs this compatibility remapping.
    switch (shrineId) {
      case 4: return existingOrFallback(shrines, 2);
      case 5: return existingOrFallback(shrines, 3);
      case 16: return existingOrFallback(shrines, 18);
      default: return existingOrFallback(shrines, shrineId);
    }
  }

  private static int selectEffectClass(Shrines shrines, int effectClass,
      int levelId, NativeRandom random) {
    int count = 0;
    for (int i = 0; i < shrines.size(); i++) {
      Shrines.Entry row = shrines.get(i);
      if (row != null && row.EffectClass == effectClass) count++;
    }
    if (count == 0) return 1;

    int selected = 1;
    // Native code retries at most the number of effect-class buckets (8).
    for (int attempt = 0; attempt < 8; attempt++) {
      int ordinal = random.nextInt(count);
      for (int i = 0; i < shrines.size(); i++) {
        Shrines.Entry row = shrines.get(i);
        if (row == null || row.EffectClass != effectClass) continue;
        if (ordinal-- == 0) {
          selected = i == 0 ? 1 : i;
          break;
        }
      }
      Shrines.Entry row = shrines.get(selected);
      if (row != null && levelId >= row.LevelMin) break;
    }
    return selected;
  }

  private static int selectAny(Shrines shrines, int levelId, NativeRandom random) {
    if (shrines.size() <= 1) return 0;
    int selected = 1;
    for (int attempt = 0; attempt < 8; attempt++) {
      selected = 1 + random.nextInt(shrines.size() - 1);
      Shrines.Entry row = shrines.get(selected);
      if (row != null && levelId >= row.LevelMin) break;
    }
    return selected;
  }

  private static int existingOrFallback(Shrines shrines, int shrineId) {
    if (shrines.get(shrineId) != null) return shrineId;
    if (shrines.get(1) != null) return 1;
    return shrines.get(0) != null ? 0 : -1;
  }

  private static int parm(Objects.Entry object, int index) {
    return object == null || object.Parm == null || index >= object.Parm.length
        ? 0 : object.Parm[index];
  }

  static int objectSeed(int seed, int levelId, int classId, int x, int y) {
    int hash = seed;
    hash = 31 * hash + levelId;
    hash = 31 * hash + classId;
    hash = 31 * hash + x;
    hash = 31 * hash + y;
    hash ^= hash >>> 16;
    return hash;
  }

  private static final class NativeRandom {
    private int state;

    NativeRandom(int seed) {
      state = seed == 0 ? 0x6D2B79F5 : seed;
    }

    int nextInt(int bound) {
      if (bound <= 1) return 0;
      int x = state;
      x ^= x << 13;
      x ^= x >>> 17;
      x ^= x << 5;
      state = x;
      return Math.floorMod(x, bound);
    }
  }
}
