package com.riiablo.engine.server.object;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.riiablo.Files;
import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.SetItems;
import com.riiablo.codec.excel.TreasureClassEx;
import com.riiablo.codec.excel.UniqueItems;
import com.riiablo.item.Quality;
import com.riiablo.item.TreasureClassResolver;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** Converts native chest treasure-class leaves into item-construction requests. */
public final class NativeObjectDropAdapter {
  private static final Logger log = LogManager.getLogger(NativeObjectDropAdapter.class);
  private static final int[] ACT_LEVEL_RANGE = {
      2, 37,
      41, 73,
      76, 102,
      104, 108,
      109, 132
  };

  /** Native fixed-point multiplier identity used by the TC {@code mul=} argument. */
  public static final int MULTIPLIER_ONE = 256;

  public static final class Drop {
    public final String sourceToken;
    public final String code;
    public final int goldMultiplier;
    public final Quality forcedQuality;
    public final TreasureClassResolver.Drop treasureClassDrop;

    private Drop(String sourceToken, String code, int goldMultiplier,
        Quality forcedQuality, TreasureClassResolver.Drop treasureClassDrop) {
      this.sourceToken = sourceToken;
      this.code = code;
      this.goldMultiplier = goldMultiplier;
      this.forcedQuality = forcedQuality;
      this.treasureClassDrop = treasureClassDrop;
    }

    public boolean isGold() {
      return "gld".equals(code);
    }
  }

  private final Files files;
  private final TreasureClassResolver resolver;

  public NativeObjectDropAdapter(Files files) {
    if (files == null) throw new NullPointerException("files");
    this.files = files;
    resolver = new TreasureClassResolver(files.TreasureClassEx);
  }

  public List<Drop> rollChest(Levels.Entry level, int difficulty,
      TreasureClassResolver.RandomSource random) {
    return rollChest(level, difficulty, random,
        TreasureClassResolver.PlayerContext.SINGLE_PLAYER);
  }

  public List<Drop> rollChest(Levels.Entry level, int difficulty,
      TreasureClassResolver.RandomSource random, TreasureClassResolver.PlayerContext players) {
    if (level == null || random == null) return Collections.emptyList();
    if (players == null) throw new NullPointerException("players");
    int safeDifficulty = Math.max(0, Math.min(difficulty, 2));
    int tier = chestTier(level, safeDifficulty);
    TreasureClassEx.Entry treasureClass = files.TreasureClassEx.getChest(
        safeDifficulty, level.Act, tier);
    if (treasureClass == null) return Collections.emptyList();

    List<TreasureClassResolver.Drop> leaves = resolver.resolve(
        treasureClass.TreasureClass, 0, random, TreasureClassResolver.NATIVE_MAX_DROPS, players);
    if (leaves.isEmpty()) return Collections.emptyList();
    List<Drop> drops = new ArrayList<>(leaves.size());
    for (TreasureClassResolver.Drop leaf : leaves) {
      Drop drop = resolveLeaf(leaf);
      if (drop != null) drops.add(drop);
    }
    return Collections.unmodifiableList(drops);
  }

  public int chestTier(Levels.Entry level, int difficulty) {
    int act = Math.max(0, Math.min(level.Act, 4));
    Levels.Entry minLevel = files.Levels.get(ACT_LEVEL_RANGE[2 * act]);
    Levels.Entry maxLevel = files.Levels.get(ACT_LEVEL_RANGE[2 * act + 1]);
    int current = areaLevel(level, difficulty);
    int min = areaLevel(minLevel, difficulty);
    int max = areaLevel(maxLevel, difficulty);
    return selectChestTier(current, min, max);
  }

  /** Mirrors OBJMODE_DropFromChestTCWithQuality's A/B/C threshold selection. */
  public static int selectChestTier(int currentLevel, int minLevel, int maxLevel) {
    int difference = Math.abs(maxLevel - minLevel);
    int offset = (difference + 1) / 3;
    if (currentLevel < minLevel + offset) return 0;
    return currentLevel >= minLevel + 2 * offset ? 2 : 1;
  }

  public static int areaLevel(Levels.Entry level, int difficulty) {
    if (level == null) return 1;
    int safeDifficulty = Math.max(0, Math.min(difficulty, 2));
    int expansion = arrayValue(level.MonLvlEx, safeDifficulty);
    if (expansion > 0) return expansion;
    return Math.max(1, arrayValue(level.MonLvl, safeDifficulty));
  }

  private Drop resolveLeaf(TreasureClassResolver.Drop leaf) {
    String name = TreasureClassResolver.baseToken(leaf.token);
    ItemEntry base = findBase(name);
    if (base != null) {
      return new Drop(leaf.token, base.code, multiplier(leaf.token), Quality.NONE, leaf);
    }

    UniqueItems.Entry unique = files.UniqueItems.get(name);
    if (unique != null && findBase(unique.code) != null) {
      return new Drop(leaf.token, unique.code, multiplier(leaf.token), Quality.UNIQUE, leaf);
    }

    SetItems.Entry set = files.SetItems.get(name);
    if (set != null) {
      String code = findBase(set._item) != null ? set._item : set.item;
      if (findBase(code) != null) {
        return new Drop(leaf.token, code, multiplier(leaf.token), Quality.SET, leaf);
      }
    }

    log.warn("[OBJECT_DROP] unresolved TreasureClass leaf: {}", leaf.token);
    return null;
  }

  public static int multiplier(String token) {
    if (token == null) return MULTIPLIER_ONE;
    String normalized = token.replace("\"", "");
    String[] arguments = normalized.split(",");
    for (int i = 1; i < arguments.length; i++) {
      String argument = arguments[i];
      int equals = argument.indexOf('=');
      if (equals <= 0 || !"mul".equalsIgnoreCase(argument.substring(0, equals))) continue;
      try {
        int multiplier = Integer.parseInt(argument.substring(equals + 1));
        return multiplier > 0 ? multiplier : MULTIPLIER_ONE;
      } catch (NumberFormatException ignored) {
        return MULTIPLIER_ONE;
      }
    }
    return MULTIPLIER_ONE;
  }

  private ItemEntry findBase(String code) {
    if (code == null || code.isEmpty()) return null;
    ItemEntry entry = files.armor.get(code);
    if (entry != null) return entry;
    entry = files.weapons.get(code);
    if (entry != null) return entry;
    return files.misc.get(code);
  }

  private static int arrayValue(int[] values, int index) {
    return values == null || values.length <= index ? 0 : values[index];
  }
}
