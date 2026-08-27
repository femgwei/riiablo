package com.riiablo.engine.server.npc;

import com.badlogic.gdx.utils.Array;

import com.riiablo.codec.excel.Npc;
import com.riiablo.engine.server.item.ItemDurabilityManager;
import com.riiablo.item.Item;
import com.riiablo.item.Location;
import com.riiablo.item.VendorPricing;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;

/** Server-authoritative atomic repair transactions. */
public final class NpcRepairService {
  public static final class Result {
    public final boolean success;
    public final String reason;
    public final int cost;
    public final int itemId;

    private Result(boolean success, String reason, int cost, int itemId) {
      this.success = success;
      this.reason = reason;
      this.cost = cost;
      this.itemId = itemId;
    }
  }

  private NpcRepairService() {}

  public static Result repairItem(CharData character, Npc.Entry npc,
                                  int itemIndex, int expectedItemId) {
    if (character == null) return rejected("PLAYER_NOT_FOUND");
    ItemData items = character.getItems();
    if (itemIndex < 0 || itemIndex >= items.getItems().size) {
      return rejected("ITEM_NOT_OWNED");
    }
    Item item = items.getItem(itemIndex);
    if (!isOwnedRepairTarget(item) || item.id != expectedItemId) {
      return rejected("ITEM_NOT_OWNED");
    }
    int cost = VendorPricing.repairPrice(item, npc, character);
    if (cost <= 0 || !ItemDurabilityManager.INSTANCE.isRepairable(item)) {
      return rejected("NOTHING_TO_REPAIR");
    }
    if (VendorPricing.availableGold(character) < cost) {
      return rejected("INSUFFICIENT_GOLD");
    }
    if (!VendorPricing.chargeGold(character, cost)) {
      return rejected("INSUFFICIENT_GOLD");
    }
    // All validation and the wallet mutation completed before durability is
    // changed, so a rejected request can never leave a partially repaired item.
    ItemDurabilityManager.INSTANCE.restoreDurability(item);
    return accepted(cost, item.id);
  }

  public static Result repairAll(CharData character, Npc.Entry npc) {
    if (character == null) return rejected("PLAYER_NOT_FOUND");
    Array<Item> repairable = new Array<>(false, 12, Item.class);
    long total = 0;
    for (Item item : character.getItems().getItems()) {
      if (item == null || item.location != Location.EQUIPPED
          || !ItemDurabilityManager.INSTANCE.isRepairable(item)) continue;
      int cost = VendorPricing.repairPrice(item, npc, character);
      if (cost <= 0) continue;
      repairable.add(item);
      total += cost;
      if (total > Integer.MAX_VALUE) return rejected("INSUFFICIENT_GOLD");
    }
    if (repairable.size == 0 || total <= 0) return rejected("NOTHING_TO_REPAIR");
    int cost = (int) total;
    if (VendorPricing.availableGold(character) < cost
        || !VendorPricing.chargeGold(character, cost)) {
      return rejected("INSUFFICIENT_GOLD");
    }
    for (Item item : repairable) ItemDurabilityManager.INSTANCE.restoreDurability(item);
    return accepted(cost, 0);
  }

  private static boolean isOwnedRepairTarget(Item item) {
    if (item == null || item.location == null) return false;
    switch (item.location) {
      case STORED:
      case EQUIPPED:
      case BELT:
        return true;
      default:
        return false;
    }
  }

  private static Result accepted(int cost, int itemId) {
    return new Result(true, null, cost, itemId);
  }

  private static Result rejected(String reason) {
    return new Result(false, reason, 0, 0);
  }
}
