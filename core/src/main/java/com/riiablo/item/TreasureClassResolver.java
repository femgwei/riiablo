package com.riiablo.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.riiablo.codec.excel.TreasureClassEx;

/**
 * Expands native TreasureClassEx rows into raw item tokens.
 *
 * <p>This is deliberately separate from item construction and entity spawning:
 * it mirrors the selection/stack portion of D2Game's D2GAME_DropTC_6FC51360,
 * while leaving quality rolls and authoritative ground-item creation to their
 * respective server adapters.
 */
public final class TreasureClassResolver {
  public static final int NATIVE_MAX_DEPTH = 64;
  public static final int NATIVE_MAX_DROPS = 6;

  public interface RandomSource {
    /** Returns a value in the range {@code [0, bound)}. */
    int nextInt(int bound);
  }

  public static final class Drop {
    public final String token;
    public final int Magic;
    public final int Rare;
    public final int Set;
    public final int Unique;
    public final int Superior;
    public final int Normal;

    private Drop(String token, Quality quality) {
      this.token = token;
      Magic = quality.Magic;
      Rare = quality.Rare;
      Set = quality.Set;
      Unique = quality.Unique;
      Superior = quality.Superior;
      Normal = quality.Normal;
    }

    @Override
    public String toString() {
      return token;
    }
  }

  private static final class Quality {
    int Magic;
    int Rare;
    int Set;
    int Unique;
    int Superior;
    int Normal;

    static Quality root(TreasureClassEx.Entry entry) {
      Quality quality = new Quality();
      quality.Magic = entry.Magic;
      quality.Rare = entry.Rare;
      quality.Set = entry.Set;
      quality.Unique = entry.Unique;
      quality.Superior = entry.Superior;
      quality.Normal = entry.Normal;
      return quality;
    }

    Quality child(TreasureClassEx.Entry entry) {
      Quality quality = new Quality();
      quality.Magic = inherit(Magic, entry.Magic);
      quality.Rare = inherit(Rare, entry.Rare);
      quality.Set = inherit(Set, entry.Set);
      quality.Unique = inherit(Unique, entry.Unique);
      quality.Superior = inherit(Superior, entry.Superior);
      quality.Normal = inherit(Normal, entry.Normal);
      return quality;
    }

    private static int inherit(int parent, int child) {
      return parent == 0 || child > parent ? child : parent;
    }
  }

  private final TreasureClassEx table;

  public TreasureClassResolver(TreasureClassEx table) {
    if (table == null) throw new NullPointerException("table");
    this.table = table;
  }

  public List<Drop> resolve(String treasureClass, int level, RandomSource random) {
    return resolve(treasureClass, level, random, NATIVE_MAX_DROPS);
  }

  public List<Drop> resolve(String treasureClass, int level, RandomSource random, int maxDrops) {
    if (treasureClass == null) throw new NullPointerException("treasureClass");
    if (random == null) throw new NullPointerException("random");
    if (maxDrops <= 0) return Collections.emptyList();

    int id = table.index(treasureClass);
    TreasureClassEx.Entry root = id < 0 ? table.get(treasureClass) : table.getForLevel(id, level);
    if (root == null) return Collections.emptyList();

    List<Drop> drops = new ArrayList<>(Math.min(maxDrops, NATIVE_MAX_DROPS));
    expand(root, Quality.root(root), random, Math.min(maxDrops, NATIVE_MAX_DROPS), 0, drops);
    return Collections.unmodifiableList(drops);
  }

  private void expand(TreasureClassEx.Entry entry, Quality quality, RandomSource random,
      int maxDrops, int depth, List<Drop> drops) {
    if (depth >= NATIVE_MAX_DEPTH || drops.size() >= maxDrops) return;

    int picks = nativePickCount(entry.Picks);
    for (int remaining = picks; remaining > 0 && drops.size() < maxDrops; remaining--) {
      String token;
      if (entry.Picks < 0) {
        // Negative Picks visit the raw cumulative probability slots in order.
        int deterministicRoll = picks - remaining;
        if (deterministicRoll >= entry.itemProbability()) break;
        token = entry.selectItem(deterministicRoll);
      } else {
        int total = entry.totalProbability();
        if (total <= 0) break;
        int roll = checkedRoll(random, total);
        int noDrop = Math.max(0, entry.NoDrop);
        if (roll < noDrop) continue;
        token = entry.selectItem(roll - noDrop);
      }

      if (token == null || token.isEmpty()) continue;
      String lookupToken = baseToken(token);
      TreasureClassEx.Entry child = table.get(lookupToken);
      if (child == null) {
        drops.add(new Drop(token, quality));
      } else {
        expand(child, quality.child(child), random, maxDrops, depth + 1, drops);
      }
    }
  }

  private static int nativePickCount(int picks) {
    if (picks == Integer.MIN_VALUE) return Integer.MAX_VALUE;
    return Math.max(Math.abs(picks), 1);
  }

  private static int checkedRoll(RandomSource random, int bound) {
    int roll = random.nextInt(bound);
    if (roll < 0 || roll >= bound) {
      throw new IllegalArgumentException("random source returned " + roll + " for bound " + bound);
    }
    return roll;
  }

  /** Returns the lookup name before native comma arguments and optional quotes. */
  public static String baseToken(String token) {
    int start = token.startsWith("\"") ? 1 : 0;
    int comma = token.indexOf(',', start);
    int quote = token.indexOf('"', start);
    int end = comma < 0 ? token.length() : comma;
    if (quote >= 0 && quote < end) end = quote;
    return token.substring(start, end);
  }
}
