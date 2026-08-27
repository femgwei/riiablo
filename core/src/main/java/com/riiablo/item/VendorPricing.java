package com.riiablo.item;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Armor;
import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.codec.excel.Npc;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;

/** Native-style transaction pricing shared by local UI and the game server. */
public final class VendorPricing {
  public static final int MULTIPLIER_SCALE = 1024;
  public static final int MAX_GOLD = 50000;
  public enum Transaction { BUY, SELL, REPAIR, GAMBLE }
  private VendorPricing() {}

  public static int buyPrice(Item item) {
    if (item != null && item.hasFlag2(Item.ITEMFLAG2_INSTORE) && item.vendorPrice >= 0) return item.vendorPrice;
    return transactionCost(item, null, Transaction.BUY, 0);
  }
  public static int sellPrice(Item item) { return transactionCost(item, null, Transaction.SELL, 0); }
  public static int gamblePrice(Item item) { return transactionCost(item, null, Transaction.GAMBLE, 0); }
  public static int gamblePrice(Item item, CharData character) {
    return transactionCost(item, null, Transaction.GAMBLE, reducedPrices(character));
  }
  public static int repairPrice(Item item, Npc.Entry npc, CharData character) {
    return transactionCost(item, npc, Transaction.REPAIR, reducedPrices(character));
  }
  public static int buyPrice(Item item, Npc.Entry npc, CharData character) {
    return transactionCost(item, npc, Transaction.BUY, reducedPrices(character));
  }
  public static int sellPrice(Item item, Npc.Entry npc, CharData character, int difficulty) {
    return transactionCost(item, npc, Transaction.SELL, reducedPrices(character), difficulty);
  }

  /** Calculates cost using Items.txt/Npc.txt values. reducedPrices is a percent. */
  public static int transactionCost(Item item, Npc.Entry npc, Transaction transaction, int reducedPrices) {
    return transactionCost(item, npc, transaction, reducedPrices, 0);
  }

  public static int transactionCost(Item item, Npc.Entry npc, Transaction transaction,
                                    int reducedPrices, int difficulty) {
    if (item == null || item.base == null || transaction == null) return 0;
    if (item.hasFlag(Item.ITEMFLAG_BEGINNER)) return 1;
    if (transaction == Transaction.REPAIR
        && (item.hasFlag(Item.ITEMFLAG_ETHEREAL) || item.base.nodurability)) return 0;
    ItemEntry base = item.base;
    if (transaction == Transaction.GAMBLE) {
      int gamble = base.gambleCost > 0 ? base.gambleCost : baseCost(base);
      return Math.max(1, applyReduced(gamble, reducedPrices));
    }
    int cost = baseCost(base);
    int quantity = quantity(item);
    if (isArmor(item) && base instanceof Armor.Entry) {
      Armor.Entry armor = (Armor.Entry) base;
      int ac = stat(item, Stat.armorclass, armor.minac);
      if (armor.maxac > 0 && armor.maxac != armor.minac - 1) cost = Math.max(1, ac * cost / armor.maxac);
    }
    if (base.stackable && quantity > 1) cost = safeMultiply(cost, quantity);
    cost = applyQuality(cost, item);
    // Native nBuyCost is the amount paid by an NPC to the player.
    if (item.hasFlag(Item.ITEMFLAG_ETHEREAL) && transaction == Transaction.SELL) cost /= 4;

    int multiplier = MULTIPLIER_SCALE;
    if (npc != null) {
      switch (transaction) {
        case BUY: multiplier = positiveOrDefault(npc.sellMult); break;
        case SELL: multiplier = positiveOrDefault(npc.buyMult); break;
        case REPAIR: multiplier = positiveOrDefault(npc.repMult); break;
        default: break;
      }
    }
    cost = scale(cost, multiplier);
    if (transaction == Transaction.BUY || transaction == Transaction.REPAIR) cost = applyReduced(cost, reducedPrices);
    else if (transaction == Transaction.SELL && npc == null) cost /= 4;
    if (transaction == Transaction.REPAIR) {
      int max = stat(item, Stat.maxdurability, 0);
      int current = stat(item, Stat.durability, max);
      if (max <= 0 || current >= max) return 0;
      cost = Math.max(1, cost * (max - Math.max(0, current)) / max);
    }
    if (transaction == Transaction.SELL && npc != null) {
      int maxBuy = difficulty <= 0 ? npc.maxBuy : difficulty == 1 ? npc.maxBuyNormal : npc.maxBuyHell;
      if (maxBuy > 0) cost = Math.min(cost, maxBuy);
    }
    return Math.min(MAX_GOLD, Math.max(1, cost));
  }

  private static int baseCost(ItemEntry base) {
    if (base.cost > 0) return base.cost;
    int level = Math.max(1, base.level);
    int size = Math.max(1, base.invwidth * base.invheight);
    int power = Math.max(0, base.mindam) + Math.max(0, base.maxdam);
    return Math.max(1, level * 10 + size * 5 + power * 4);
  }

  private static int applyQuality(int cost, Item item) {
    if (!item.isIdentified() || item.quality == null) return cost;
    int mult = MULTIPLIER_SCALE, add = 0;
    switch (item.quality) {
      case MAGIC:
        if (Riiablo.files != null) {
          int prefix = item.qualityId & Item.MAGIC_AFFIX_MASK;
          int suffix = item.qualityId >>> Item.MAGIC_AFFIX_SIZE;
          com.riiablo.codec.excel.MagicAffix p = Riiablo.files.MagicPrefix.get(prefix);
          com.riiablo.codec.excel.MagicAffix s = Riiablo.files.MagicSuffix.get(suffix);
          if (p != null) { mult += p.multiply; add += p.add; }
          if (s != null) { mult += s.multiply; add += s.add; }
        }
        break;
      case UNIQUE:
        if (item.qualityData instanceof com.riiablo.codec.excel.UniqueItems.Entry) {
          com.riiablo.codec.excel.UniqueItems.Entry e = (com.riiablo.codec.excel.UniqueItems.Entry) item.qualityData;
          mult += e.cost_mult; add = e.cost_add;
        }
        break;
      case SET:
        if (item.qualityData instanceof com.riiablo.codec.excel.SetItems.Entry) {
          com.riiablo.codec.excel.SetItems.Entry e = (com.riiablo.codec.excel.SetItems.Entry) item.qualityData;
          mult += e.cost_mult; add = e.cost_add;
        }
        break;
      case LOW:
        mult = MULTIPLIER_SCALE / 2;
        break;
      default: break;
    }
    return Math.max(1, safeMultiply(cost, positiveOrDefault(mult)) / MULTIPLIER_SCALE + add);
  }

  private static int quantity(Item item) { return Math.max(1, Math.min(511, stat(item, Stat.quantity, 1))); }
  private static boolean isArmor(Item item) { return item.base instanceof Armor.Entry; }
  private static int stat(Item item, short stat, int fallback) {
    if (item.attrs == null) return fallback;
    StatRef ref = item.attrs.base().get(stat);
    return ref == null ? fallback : ref.asInt();
  }
  private static int positiveOrDefault(int value) { return value > 0 ? value : MULTIPLIER_SCALE; }
  private static int applyReduced(int value, int percent) { int pct = Math.max(0, Math.min(99, percent)); return value - value * pct / 100; }
  private static int scale(int value, int multiplier) { long result = (long) value * positiveOrDefault(multiplier) / MULTIPLIER_SCALE; return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result; }
  private static int safeMultiply(int a, int b) { long result = (long) a * b; return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result; }

  public static boolean buy(CharData character, Item item) {
    return buy(character, item, null);
  }
  public static boolean buy(CharData character, Item item, Npc.Entry npc) {
    if (character == null || item == null || !item.hasFlag2(Item.ITEMFLAG2_INSTORE)) return false;
    int price = buyPrice(item, npc, character);
    if (!canSpend(character, price)) return false;
    ItemData items = character.getItems();
    if (!items.addToInventory(item)) return false;
    item.flags2 &= ~Item.ITEMFLAG2_INSTORE;
    spend(character, price);
    return true;
  }
  public static boolean gamble(CharData character, Item item) {
    if (character == null || item == null || !item.hasFlag2(Item.ITEMFLAG2_INSTORE)) return false;
    int price = gamblePrice(item, character);
    if (!canSpend(character, price)) return false;
    ItemData items = character.getItems();
    if (!items.addToInventory(item)) return false;
    item.flags2 &= ~Item.ITEMFLAG2_INSTORE;
    spend(character, price);
    return true;
  }
  public static boolean sell(CharData character, int itemIndex) {
    return sell(character, itemIndex, null, 0);
  }
  public static boolean sell(CharData character, int itemIndex, Npc.Entry npc, int difficulty) {
    if (character == null) return false;
    ItemData items = character.getItems();
    if (itemIndex < 0 || itemIndex >= items.getItems().size) return false;
    Item item = items.getItem(itemIndex);
    if (item == null || item.location != Location.STORED || item.storeLoc != StoreLoc.INVENTORY) return false;
    int value = sellPrice(item, npc, character, difficulty);
    if (!items.removeOwnedItem(itemIndex)) return false;
    addGold(character, value);
    return true;
  }
  public static int availableGold(CharData character) {
    if (character == null || character.getStats() == null) return 0;
    return Math.max(0, value(character.getStats().get(Stat.gold))) + Math.max(0, value(character.getStats().get(Stat.goldbank)));
  }
  public static boolean chargeGold(CharData character, int amount) { if (character == null || amount < 0 || !canSpend(character, amount)) return false; spend(character, amount); return true; }
  public static void grantGold(CharData character, int amount) { if (character != null && amount > 0) addGold(character, amount); }
  /** Applies an authoritative wallet snapshot received from D2GS. */
  public static void setGoldSnapshot(CharData character, int carried, int bank) {
    if (character == null) return;
    setGold(character, Math.max(0, carried), Math.max(0, bank));
  }
  public static int reducedPrices(CharData character) {
    return character == null || character.getStats() == null ? 0
        : Math.max(0, Math.min(99, value(character.getStats().get(Stat.item_reducedprices))));
  }
  private static boolean canSpend(CharData character, int amount) { return amount >= 0 && availableGold(character) >= amount; }
  private static void spend(CharData character, int amount) { int carried = Math.max(0, value(character.getStats().get(Stat.gold))); int fromCarried = Math.min(carried, amount); setGold(character, carried - fromCarried, Math.max(0, value(character.getStats().get(Stat.goldbank))) - (amount - fromCarried)); }
  private static void addGold(CharData character, int amount) { int carried = Math.max(0, value(character.getStats().get(Stat.gold))); setGold(character, Math.min(MAX_GOLD, carried + Math.max(0, amount)), Math.max(0, value(character.getStats().get(Stat.goldbank)))); }
  private static void setGold(CharData character, int gold, int bank) { character.getStats().base().put(Stat.gold, gold); character.getStats().aggregate().put(Stat.gold, gold); character.getStats().base().put(Stat.goldbank, bank); character.getStats().aggregate().put(Stat.goldbank, bank); }
  private static int value(StatRef ref) { return ref == null ? 0 : ref.asInt(); }
}
