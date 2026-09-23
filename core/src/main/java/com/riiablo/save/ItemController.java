package com.riiablo.save;

import com.riiablo.item.BodyLoc;
import com.riiablo.item.StoreLoc;

public interface ItemController {
  void groundToCursor(int entityId);
  void cursorToGround();
  void storeToCursor(int i);
  /** Moves an inventory potion directly into the first available belt slot. */
  boolean inventoryToBelt(int i);
  void cursorToStore(StoreLoc storeLoc, int x, int y);
  void swapStoreItem(int i, StoreLoc storeLoc, int x, int y);
  void bodyToCursor(BodyLoc bodyLoc, boolean merc);
  void cursorToBody(BodyLoc bodyLoc, boolean merc);
  void swapBodyItem(BodyLoc bodyLoc, boolean merc);
  void beltToCursor(int i);
  void cursorToBelt(int x, int y);
  void swapBeltItem(int i);
  void useBeltSlot(int column);
  /** Uses a consumable directly from the character inventory. */
  void useInventoryItem(com.riiablo.item.Item item);
  /** Drops carried gold at the player's position. */
  void dropGold(int amount);
  /** Moves carried gold into the personal stash. */
  void depositGold(int amount);
  /** Moves stash gold into the carried wallet. */
  void withdrawGold(int amount);
}
