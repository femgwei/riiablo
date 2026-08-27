package com.riiablo.item;

import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;

/** Deterministic local equivalent of the native NPC transaction boundary. */
public final class VendorPricing {
  private static final int MAX_GOLD = 50000;

  private VendorPricing() {}

  public static int buyPrice(Item item) {
    if (item == null || item.base == null) return 0;
    int level = Math.max(1, item.base.level);
    int size = Math.max(1, item.base.invwidth * item.base.invheight);
    int power = Math.max(0, item.base.mindam) + Math.max(0, item.base.maxdam);
    int armor = item.attrs == null ? 0 : value(item.attrs.base().get(Stat.armorclass));
    int base = Math.max(1, level * 10 + size * 5 + power * 4 + armor * 2);
    int multiplier;
    switch (item.quality == null ? Quality.NORMAL : item.quality) {
      case MAGIC: multiplier = 2; break;
      case SET: multiplier = 4; break;
      case RARE: multiplier = 3; break;
      case UNIQUE: multiplier = 5; break;
      case CRAFTED: multiplier = 4; break;
      default: multiplier = 1;
    }
    return Math.min(MAX_GOLD, Math.max(1, base * multiplier));
  }

  public static int sellPrice(Item item) {
    return Math.max(1, buyPrice(item) / 4);
  }

  public static boolean buy(CharData character, Item item) {
    if (character == null || item == null || !item.hasFlag2(Item.ITEMFLAG2_INSTORE)) return false;
    int price = buyPrice(item);
    if (!canSpend(character, price)) return false;
    ItemData items = character.getItems();
    if (!items.addToInventory(item)) return false;
    item.flags2 &= ~Item.ITEMFLAG2_INSTORE;
    spend(character, price);
    return true;
  }

  public static boolean sell(CharData character, int itemIndex) {
    if (character == null) return false;
    ItemData items = character.getItems();
    if (itemIndex < 0 || itemIndex >= items.getItems().size) return false;
    Item item = items.getItem(itemIndex);
    if (item == null || item.location != Location.STORED || item.storeLoc != StoreLoc.INVENTORY) return false;
    int value = sellPrice(item);
    if (!items.removeOwnedItem(itemIndex)) return false;
    addGold(character, value);
    return true;
  }

  public static int availableGold(CharData character) {
    if (character == null || character.getStats() == null) return 0;
    return Math.max(0, value(character.getStats().get(Stat.gold)))
        + Math.max(0, value(character.getStats().get(Stat.goldbank)));
  }

  public static boolean chargeGold(CharData character, int amount) {
    if (character == null || amount < 0 || !canSpend(character, amount)) return false;
    spend(character, amount);
    return true;
  }

  public static void grantGold(CharData character, int amount) {
    if (character != null && amount > 0) addGold(character, amount);
  }

  private static boolean canSpend(CharData character, int amount) {
    return amount >= 0 && availableGold(character) >= amount;
  }

  private static void spend(CharData character, int amount) {
    int carried = Math.max(0, value(character.getStats().get(Stat.gold)));
    int fromCarried = Math.min(carried, amount);
    setGold(character, carried - fromCarried,
        Math.max(0, value(character.getStats().get(Stat.goldbank))) - (amount - fromCarried));
  }

  private static void addGold(CharData character, int amount) {
    int carried = Math.max(0, value(character.getStats().get(Stat.gold)));
    setGold(character, Math.min(MAX_GOLD, carried + Math.max(0, amount)),
        Math.max(0, value(character.getStats().get(Stat.goldbank))));
  }

  private static void setGold(CharData character, int gold, int bank) {
    character.getStats().base().put(Stat.gold, gold);
    character.getStats().aggregate().put(Stat.gold, gold);
    character.getStats().base().put(Stat.goldbank, bank);
    character.getStats().aggregate().put(Stat.goldbank, bank);
  }

  private static int value(StatRef ref) {
    return ref == null ? 0 : ref.asInt();
  }
}
