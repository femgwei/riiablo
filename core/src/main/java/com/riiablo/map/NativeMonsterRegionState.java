package com.riiablo.map;

import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.Array;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.monster.NativeMonsterRegion;

import java.util.Arrays;

/**
 * Replays D2Game's shared MONSTERREGION initialization stream.
 *
 * <p>The original initializes every Levels.txt row in numeric order, even if
 * the current map only materializes one level.  Caching the rows here is
 * important for headless probes: asking for Dry Hills directly must still
 * consume the same rows 1..41 before producing row 42.</p>
 */
final class NativeMonsterRegionState {
  static final class Selection {
    final MonStats.Entry[] selected;
    final MonStats.Entry[] weighted;

    Selection(MonStats.Entry[] selected, MonStats.Entry[] weighted) {
      this.selected = selected;
      this.weighted = weighted;
    }
  }

  private final Map map;
  private final int difficulty;
  private final D2Seed regionSeed;
  private final IntMap<Selection> selections = new IntMap<>();
  private int nextLevelId = 1;
  private final int monsterSeed;

  NativeMonsterRegionState(Map map) {
    this.map = map;
    this.difficulty = map.getDifficulty();
    D2Seed gameSeed = new D2Seed(map.seed(), 666);
    monsterSeed = (int) Seed.rollRandomNumber(gameSeed);
    regionSeed = new D2Seed(monsterSeed, 666);
  }

  int monsterSeed() {
    return monsterSeed;
  }

  void reset() {
    selections.clear();
    nextLevelId = 1;
    regionSeed.setSeeds(monsterSeed, 666);
  }

  Selection forLevel(int levelId) {
    if (levelId <= 0) return new Selection(new MonStats.Entry[0], new MonStats.Entry[0]);
    while (nextLevelId <= levelId) {
      selections.put(nextLevelId, build(nextLevelId));
      nextLevelId++;
    }
    Selection result = selections.get(levelId);
    return result == null ? new Selection(new MonStats.Entry[0], new MonStats.Entry[0]) : result;
  }

  private Selection build(int levelId) {
    Levels.Entry level = map.files() == null || map.files().Levels == null
        ? null : map.files().Levels.get(levelId);
    if (level == null || level.NumMon <= 0) return new Selection(new MonStats.Entry[0], new MonStats.Entry[0]);

    String[] columns = NativeMonsterRegion.monsterColumns(level, difficulty);
    Array<String> available = new Array<>();
    for (String id : columns) {
      if (id != null && !id.isEmpty() && !"0".equals(id)) available.add(id);
    }
    int selectionCount = Math.min(Math.min(level.NumMon, 13), available.size);
    if (selectionCount <= 0) return new Selection(new MonStats.Entry[0], new MonStats.Entry[0]);

    Array<MonStats.Entry> selected = new Array<>();
    for (int i = 0; i < selectionCount && available.size > 0; i++) {
      int index = regionSeedRoll(available.size);
      String id = available.get(index);
      if (i == 0 && level.rangedspawn && available.size > 1) {
        for (int attempt = 0; attempt < 20; attempt++) {
          MonStats.Entry candidate = map.files().monstats.get(id);
          if (candidate != null && candidate.rangedtype) break;
          index = regionSeedRoll(available.size);
          id = available.get(index);
        }
      }
      // The native code removes the selected slot after the ranged rerolls.
      available.removeIndex(index);
      MonStats.Entry entry = map.files().monstats.get(id);
      if (entry != null && entry.isSpawn) selected.add(entry);
    }

    MonStats.Entry[] chosen = selected.toArray(MonStats.Entry.class);
    // D2Game's MONSTERREGION_InitializeAll calls sub_6FC62020 for every
    // selected monster before the next level is initialized.  Besides choosing
    // composite variants, that routine advances the shared region seed.  The
    // variants are stored on the native region record and are not needed by
    // this selection object, but the exact draw sequence is needed for parity
    // with later levels and weighted monster rolls.
    for (MonStats.Entry entry : chosen) consumeComponentVariants(entry);

    int rarity = 0;
    for (MonStats.Entry entry : chosen) rarity += Math.max(0, entry.Rarity);
    if (rarity <= 0) return new Selection(chosen, new MonStats.Entry[0]);
    MonStats.Entry[] weighted = new MonStats.Entry[rarity];
    int offset = 0;
    for (MonStats.Entry entry : chosen) {
      for (int i = 0; i < Math.max(0, entry.Rarity); i++) weighted[offset++] = entry;
    }
    return new Selection(chosen, weighted);
  }

  private int regionSeedRoll(int bound) {
    return Seed.rollLimitedRandomNumber(regionSeed, bound);
  }

  private void consumeComponentVariants(MonStats.Entry monster) {
    if (monster == null || map.files() == null || map.files().monstats2 == null
        || monster.MonStatsEx == null || monster.MonStatsEx.isEmpty()) return;
    MonStats2.Entry stats = map.files().monstats2.get(monster.MonStatsEx);
    if (stats == null) return;
    int[] choiceCounts = componentChoiceCounts(stats.ComponentV);
    int exponent = 0;
    for (int count : choiceCounts) {
      if (count > 1) exponent += count - 1;
    }
    // DATATBLS_MonStats2CompositLinker clamps unk0x25 to one byte.
    exponent = Math.min(254, exponent);
    consumeComponentVariants(regionSeed, choiceCounts, exponent, 3);
  }

  /**
   * Replays MonsterChoose::sub_6FC62020's seed consumption.  The generated
   * component bytes are local because the region selection API currently only
   * needs the seed position; retaining the same duplicate retry loop is what
   * preserves variable-length native draws.
   */
  static void consumeComponentVariants(D2Seed seed, int[] choiceCounts,
      int componentVariantExponent, int requestedCount) {
    if (seed == null || choiceCounts == null || requestedCount <= 0) return;

    int variantsMax = 0;
    int capacity;
    if (componentVariantExponent <= 0) capacity = 1;
    else if (componentVariantExponent == 1) capacity = 2;
    else capacity = 3;
    int count = Math.min(requestedCount, capacity);
    count = Math.min(count, 3 - variantsMax);
    if (count <= 0) return;

    byte[][] variants = new byte[3][16];
    if (variantsMax == 0) {
      for (int i = 0; i < 16; i++) {
        int choices = i < choiceCounts.length ? choiceCounts[i] : 0;
        variants[0][i] = (byte) (choices > 1
            ? Seed.rollLimitedRandomNumber(seed, choices) : 0);
      }
      --count;
      ++variantsMax;
    }

    if (count == 0) return;

    int[] indices = new int[16];
    int indexCount = 0;
    for (int i = 0; i < 16; i++) {
      int choices = i < choiceCounts.length ? choiceCounts[i] : 0;
      if (choices > 1) indices[indexCount++] = i;
    }
    if (indexCount < 1) return;

    int index1 = indices[0];
    int index2 = indices[0];
    if (indexCount != 1) {
      int randomIndex = Seed.rollLimitedRandomNumber(seed, indexCount);
      index1 = indices[randomIndex];
      indices[randomIndex] = indices[indexCount - 1];
      index2 = indices[Seed.rollLimitedRandomNumber(seed, indexCount - 1)];
    }

    for (int i = 0; i < count; i++) {
      byte[] data = variants[variantsMax];
      System.arraycopy(variants[0], 0, data, 0, 16);
      int retries = 3;
      boolean duplicate;
      do {
        int choices1 = choiceCounts[index1];
        data[index1] = (byte) Seed.rollLimitedRandomNumber(seed, choices1);
        if (index1 != index2) {
          int choices2 = choiceCounts[index2];
          data[index2] = (byte) Seed.rollLimitedRandomNumber(seed, choices2);
        }

        duplicate = false;
        for (int existing = 0; existing < variantsMax; existing++) {
          if (Arrays.equals(data, variants[existing])) {
            duplicate = true;
            --retries;
          }
        }
      } while (duplicate && retries != 0);
      ++variantsMax;
    }
  }

  private static int[] componentChoiceCounts(String[] values) {
    int[] counts = new int[16];
    if (values == null) return counts;
    int length = Math.min(values.length, counts.length);
    for (int i = 0; i < length; i++) counts[i] = nativeChoiceCount(values[i]);
    return counts;
  }

  /** Mirrors DATATBLS_MonStats2CompositLinker, including its 12-entry limit. */
  private static int nativeChoiceCount(String value) {
    if (value == null || value.isEmpty()) return 0;
    int start = value.charAt(0) == '"' ? 1 : 0;
    int end = value.indexOf('"', start);
    if (end < 0) end = value.length();
    if (start >= end) return 0;
    String[] tokens = value.substring(start, end).split(",", -1);
    int count = 0;
    for (String token : tokens) {
      if (token.isEmpty() || token.length() > 4 || count >= 12) break;
      ++count;
    }
    return count;
  }
}
