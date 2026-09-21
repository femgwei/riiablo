package com.riiablo.engine.server.trade;

import com.badlogic.gdx.utils.Array;
import com.riiablo.item.Item;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.item.VendorPricing;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;

/**
 * Concrete two-phase authority for player inventories and carried gold.
 * Production network code supplies the authenticated character resolver.
 */
public final class ItemDataTradeAuthority implements TradeManager.TradeAuthority {
  public interface PlayerResolver {
    CharData resolve(int playerId);
  }

  private final PlayerResolver players;

  public ItemDataTradeAuthority(PlayerResolver players) {
    if (players == null) throw new NullPointerException("players");
    this.players = players;
  }

  @Override
  public int validate(TradeSession session) {
    if (session == null || session.getState() != TradeState.CONFIRMED
        || !session.isBothConfirmed()) return TradeState.RESULT_ERROR;
    CharData first = players.resolve(session.getPlayer1Id());
    CharData second = players.resolve(session.getPlayer2Id());
    if (first == null || second == null) return TradeState.RESULT_ERROR;
    Array<Item> firstItems = ownedItems(first.getItems(), session.getPlayerItems(session.getPlayer1Id()));
    Array<Item> secondItems = ownedItems(second.getItems(), session.getPlayerItems(session.getPlayer2Id()));
    if (firstItems == null || secondItems == null) return TradeState.RESULT_ERROR;
    if (!canReceive(second.getItems(), firstItems, secondItems)
        || !canReceive(first.getItems(), secondItems, firstItems)) {
      return TradeState.RESULT_NO_SPACE;
    }
    int firstGold = session.getGold(session.getPlayer1Id());
    int secondGold = session.getGold(session.getPlayer2Id());
    if (firstGold < 0 || secondGold < 0
        || VendorPricing.carriedGold(first) < firstGold
        || VendorPricing.carriedGold(second) < secondGold
        || VendorPricing.carriedGold(first) > VendorPricing.MAX_CARRIED_GOLD - secondGold
        || VendorPricing.carriedGold(second) > VendorPricing.MAX_CARRIED_GOLD - firstGold) {
      return TradeState.RESULT_NO_GOLD;
    }
    return TradeState.RESULT_SUCCESS;
  }

  @Override
  public boolean commit(TradeSession session) {
    if (validate(session) != TradeState.RESULT_SUCCESS) return false;
    CharData first = players.resolve(session.getPlayer1Id());
    CharData second = players.resolve(session.getPlayer2Id());
    Array<Item> firstItems = ownedItems(first.getItems(), session.getPlayerItems(session.getPlayer1Id()));
    Array<Item> secondItems = ownedItems(second.getItems(), session.getPlayerItems(session.getPlayer2Id()));
    for (Item item : firstItems) if (!first.getItems().removeInventoryItem(item)) return false;
    for (Item item : secondItems) if (!second.getItems().removeInventoryItem(item)) return false;
    for (Item item : firstItems) if (!second.getItems().addToInventory(item)) return false;
    for (Item item : secondItems) if (!first.getItems().addToInventory(item)) return false;
    int firstGold = session.getGold(session.getPlayer1Id());
    int secondGold = session.getGold(session.getPlayer2Id());
    if (!VendorPricing.transferGold(first, second, firstGold)
        || !VendorPricing.transferGold(second, first, secondGold)) return false;
    return true;
  }

  private static Array<Item> ownedItems(ItemData data, Array<TradeSlot> slots) {
    if (data == null || slots == null) return null;
    Array<Item> result = new Array<>(false, slots.size, Item.class);
    for (TradeSlot slot : slots) {
      if (slot == null || !slot.isTradable()) return null;
      Item item = data.findItemById(slot.itemEntityId);
      if (item == null || item.location != Location.STORED
          || item.storeLoc != StoreLoc.INVENTORY || item.base == null) return null;
      if (result.contains(item, true)) return null;
      result.add(item);
    }
    return result;
  }

  private static boolean canReceive(ItemData target, Array<Item> incoming,
                                    Array<Item> outgoing) {
    boolean[][] occupied = new boolean[4][10];
    for (int i = 0; i < target.getStore(StoreLoc.INVENTORY).size; i++) {
      Item item = target.getItem(target.getStore(StoreLoc.INVENTORY).get(i));
      if (item == null || containsIdentity(outgoing, item)) continue;
      if (!mark(occupied, item, false)) return false;
    }
    for (Item item : incoming) if (!mark(occupied, item, true)) return false;
    return true;
  }

  private static boolean containsIdentity(Array<Item> items, Item value) {
    return items != null && items.contains(value, true);
  }

  private static boolean mark(boolean[][] occupied, Item item, boolean place) {
    int width = item.base.invwidth;
    int height = item.base.invheight;
    if (width <= 0 || height <= 0 || width > 10 || height > 4) return false;
    if (!place) {
      if (item.gridX < 0 || item.gridY < 0 || item.gridX + width > 10
          || item.gridY + height > 4) return false;
      for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
        occupied[item.gridY + y][item.gridX + x] = true;
      return true;
    }
    for (int y = 0; y <= 4 - height; y++) for (int x = 0; x <= 10 - width; x++) {
      boolean free = true;
      for (int dy = 0; dy < height && free; dy++) for (int dx = 0; dx < width; dx++)
        if (occupied[y + dy][x + dx]) free = false;
      if (!free) continue;
      for (int dy = 0; dy < height; dy++) for (int dx = 0; dx < width; dx++)
        occupied[y + dy][x + dx] = true;
      return true;
    }
    return false;
  }
}
