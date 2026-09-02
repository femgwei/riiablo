package com.riiablo.item;

import com.badlogic.gdx.utils.Array;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Armor;
import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.codec.excel.Weapons;

/** Data-only D2Game item initialization and trait rules. */
public final class NativeItemGeneration {
  private NativeItemGeneration() {}

  public interface RandomSource {
    int nextInt(int bound);
  }

  /** Mirrors ITEMS_ComputeCraftedMagicAffixLevel for non-crafted drops. */
  public static int affixLevel(int itemLevel, int qualityLevel, int magicLevel) {
    int qlvl = Math.max(0, qualityLevel);
    int ilvl = Math.max(Math.max(1, itemLevel), qlvl);
    int result;
    if (magicLevel > 0) {
      result = magicLevel + ilvl;
    } else {
      int halfQlvl = qlvl / 2;
      int distance = 99 - halfQlvl;
      result = ilvl >= distance ? 2 * ilvl - halfQlvl - distance : ilvl - halfQlvl;
    }
    return Math.max(1, Math.min(99, result));
  }

  /** ITEMS_GetMaxSockets plus the difficulty cap applied by sub_6FC4D6B0. */
  public static int maxSockets(Item item, int itemLevel, int difficulty) {
    if (item == null || item.base == null || item.typeEntry == null
        || item.base.gemsockets <= 0 || item.base.stackable) return 0;
    int[] table = item.typeEntry.MaxSock;
    int tier = itemLevel > 40 ? 2 : itemLevel > 25 ? 1 : 0;
    int typeMax = table == null || table.length == 0 ? 0
        : table[Math.min(tier, table.length - 1)];
    int difficultyMax = difficulty <= 0 ? 3 : difficulty == 1 ? 4 : 6;
    return Math.max(0, Math.min(Math.min(item.base.gemsockets, typeMax),
        Math.min(6, difficultyMax)));
  }

  public static void initializeBaseStats(Item item, RandomSource random) {
    if (item == null || item.base == null || random == null) return;
    if (item.base instanceof Armor.Entry) {
      Armor.Entry armor = (Armor.Entry) item.base;
      item.attrs.base().put(Stat.armorclass,
          between(random, Math.min(armor.minac, armor.maxac), Math.max(armor.minac, armor.maxac)));
      initializeDurability(item, armor.durability, random);
    } else if (item.base instanceof Weapons.Entry) {
      initializeDurability(item, ((Weapons.Entry) item.base).durability, random);
    }

    if (item.base.stackable) {
      int min = Math.max(1, item.base.minstack);
      int max = Math.max(min, item.base.maxstack);
      // Native stack rolls use [min,max), except a degenerate one-value range.
      int quantity = max == min ? min : min + random.nextInt(max - min);
      item.attrs.base().put(Stat.quantity, quantity);
    }
  }

  public static boolean rollSockets(Item item, Quality quality, int itemLevel,
      int difficulty, int startSeed, RandomSource random) {
    if (item == null || quality == null
        || quality != Quality.NORMAL && quality != Quality.HIGH
        || random == null) return false;
    int max = maxSockets(item, itemLevel, difficulty);
    if (max <= 0 || random.nextInt(100) >= 33) return false;
    int sockets = Math.floorMod(startSeed, max) + 1;
    item.flags |= Item.ITEMFLAG_SOCKETED;
    item.attrs.base().put(Stat.item_numsockets, sockets);
    item.sockets = new Array<>(sockets);
    return true;
  }

  public static boolean rollEthereal(Item item, Quality quality, RandomSource random) {
    if (!canBeEthereal(item, quality) || random.nextInt(100) >= 5) return false;
    applyEthereal(item);
    return true;
  }

  public static boolean canBeEthereal(Item item, Quality quality) {
    if (item == null || item.base == null || quality == null
        || item.base.nodurability || item.base.quest > 0
        || quality == Quality.LOW || quality == Quality.SET) return false;
    StatRef max = item.attrs.base().get(Stat.maxdurability);
    return (item.base instanceof Armor.Entry || item.base instanceof Weapons.Entry)
        && max != null && max.asInt() > 0;
  }

  public static void applyEthereal(Item item) {
    item.flags |= Item.ITEMFLAG_ETHEREAL;
    if (item.base instanceof Weapons.Entry) {
      scaleBase(item, Stat.mindamage);
      scaleBase(item, Stat.maxdamage);
      scaleBase(item, Stat.secondary_mindamage);
      scaleBase(item, Stat.secondary_maxdamage);
      scaleBase(item, Stat.item_throw_mindamage);
      scaleBase(item, Stat.item_throw_maxdamage);
    } else {
      scaleBase(item, Stat.armorclass);
    }
    StatRef max = item.attrs.base().get(Stat.maxdurability);
    if (max != null && max.asInt() > 0) {
      int etherealMax = max.asInt() / 2 + 1;
      item.attrs.base().put(Stat.maxdurability, etherealMax);
      item.attrs.base().put(Stat.durability, etherealMax);
    }
  }

  private static void initializeDurability(Item item, int durability, RandomSource random) {
    int max = item.base.nodurability ? 0 : Math.max(0, Math.min(255, durability));
    item.attrs.base().put(Stat.maxdurability, max);
    if (max <= 0) return;
    int half = max >> 1;
    int current = half <= 0 ? max : half + random.nextInt(half);
    item.attrs.base().put(Stat.durability, Math.max(1, Math.min(255, current)));
  }

  private static int between(RandomSource random, int min, int max) {
    return max <= min ? min : min + random.nextInt(max - min + 1);
  }

  private static void scaleBase(Item item, short stat) {
    StatRef value = item.attrs.base().get(stat);
    if (value != null) item.attrs.base().put(stat, 3 * value.asInt() / 2);
  }
}
