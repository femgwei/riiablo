package com.riiablo.map;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LosslessTxtTable;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.monster.MonsterAffix;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** MonsterChoose.cpp/MonsterUnique.cpp's random region boss selection. */
final class NativeMonsterEliteRules {
  private final LosslessTxtTable mods;
  private final LosslessTxtTable types;

  NativeMonsterEliteRules(LosslessTxtTable mods, LosslessTxtTable types) {
    this.mods = mods;
    this.types = types;
  }

  static NativeMonsterEliteRules load(MPQFileHandleResolver resolver) {
    if (resolver == null) throw new IllegalStateException("Random bosses require native TXT resources");
    try {
      return new NativeMonsterEliteRules(
          LosslessTxtTable.parse(resolver.resolve("data/global/excel/MonUMod.txt").readBytes()),
          LosslessTxtTable.parse(resolver.resolve("data/global/excel/MonType.txt").readBytes()));
    } catch (IOException e) {
      throw new GdxRuntimeException("Cannot load native random monster modifiers", e);
    }
  }

  /** The <=5 comparison is intentional: six of the hundred rolls qualify. */
  static boolean selectElite(int count, int min, int max, int visited, int rooms, Random random) {
    if (count < min && rooms > 0 && random.nextInt(100) < 100L * visited / rooms) return true;
    if (count < max && random.nextInt(100) <= 5) return true;
    // The native function also consumes this roll for its type-1/type-2 result.
    // PopulateRoom folds type 1 back into normal type 2.
    random.nextInt(100);
    return false;
  }

  static int bossMin(Levels.Entry level, int difficulty) {
    return MapDataPort.value(level.MonUMin, difficulty, 0);
  }

  static int bossMax(Levels.Entry level, int difficulty) {
    return MapDataPort.value(level.MonUMax, difficulty, 0);
  }

  Roll roll(MonStats.Entry monster, MonStats2.Entry stats, int difficulty, Random random) {
    difficulty = Math.max(0, Math.min(2, difficulty));
    boolean champion = random.nextInt(100) < mods.getNativeInt(0, "constants");
    int[] chosen = new int[champion ? 1 : difficulty + 1];
    Set<Integer> used = new HashSet<>();
    for (int i = 0; i < chosen.length; i++) {
      int total = 0;
      for (int row = 0; row < mods.rowCount(); row++) {
        if (!used.contains(row) && eligible(row, monster, stats, champion)) total += weight(row, champion, difficulty);
      }
      if (total == 0) break;
      int pick = random.nextInt(total);
      for (int row = 0; row < mods.rowCount(); row++) {
        if (used.contains(row) || !eligible(row, monster, stats, champion)) continue;
        pick -= weight(row, champion, difficulty);
        if (pick < 0) {
          used.add(row);
          chosen[i] = mods.getNativeInt(row, "id");
          break;
        }
      }
    }
    long minionAffixes = 0;
    for (int row : used) {
      if (mods.getNativeInt(row, "xfer") != 0)
        minionAffixes |= MonsterAffix.fromUniqueMods(new int[] {mods.getNativeInt(row, "id")});
    }
    return new Roll(champion, chosen, MonsterAffix.fromUniqueMods(chosen), minionAffixes);
  }

  private int weight(int row, boolean champion, int difficulty) {
    return Math.max(0, mods.getNativeInt(row, (champion ? "cpick" : "upick")
        + (difficulty == 1 ? " (N)" : difficulty == 2 ? " (H)" : "")));
  }

  private boolean eligible(int row, MonStats.Entry monster, MonStats2.Entry stats, boolean champion) {
    if (mods.getNativeInt(row, "enabled") == 0
        || (mods.getNativeInt(row, "champion") != 0) != champion) return false;
    for (int i = 1; i <= 2; i++) {
      String exclude = mods.get(row, "exclude" + i);
      if (!exclude.isEmpty() && hasType(monster.MonType, exclude, new HashSet<String>())) return false;
    }
    switch (mods.getNativeInt(row, "fPick")) {
      case 1: return mode(stats, 4); // A1
      case 2: return !monster.isMelee && !monster.nomultishot;
      case 3: return mode(stats, 2); // WL
      default: return true;
    }
  }

  private boolean hasType(String type, String expected, Set<String> seen) {
    if (type == null || type.isEmpty() || !seen.add(type)) return false;
    if (type.equalsIgnoreCase(expected)) return true;
    for (int row = 0; row < types.rowCount(); row++) {
      if (!type.equalsIgnoreCase(types.get(row, "type"))) continue;
      for (int i = 1; i <= 3; i++)
        if (hasType(types.get(row, "equiv" + i), expected, seen)) return true;
      break;
    }
    return false;
  }

  private static boolean mode(MonStats2.Entry stats, int mode) {
    return stats != null && stats.mMode != null && stats.mMode.length > mode && stats.mMode[mode];
  }

  static final class Roll {
    final boolean champion;
    final int[] mods;
    final long affixes, minionAffixes;
    final int championType;

    Roll(boolean champion, int[] mods, long affixes, long minionAffixes) {
      this.champion = champion;
      this.mods = mods;
      this.affixes = affixes;
      this.minionAffixes = minionAffixes;
      int mod = mods.length == 0 ? 16 : mods[0];
      championType = !champion ? -1 : mod == 36 ? MonsterRank.CHAMP_GHOSTLY
          : mod == 37 ? MonsterRank.CHAMP_FANATIC : mod == 38 ? MonsterRank.CHAMP_POSSESSED
          : mod == 39 ? MonsterRank.CHAMP_BERSERKER : MonsterRank.CHAMP_CHAMPION;
    }
  }
}
