package com.riiablo.save;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.AttributesUpdater;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.attributes.UpdateSequence;
import com.riiablo.codec.excel.CharStats;
import com.riiablo.codec.excel.SetItems;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.Location;
import com.riiablo.item.Quality;
import com.riiablo.item.StoreLoc;
import com.riiablo.item.Type;
import com.riiablo.util.EnumIntMap;

public class ItemData {
  public static final int INVALID_ITEM = -1;

  final Attributes stats;
  CharStats.Entry charStats;

  final Array<Item> itemData = new Array<>(Item.class);

  int cursor = INVALID_ITEM;

  int alternate = D2S.PRIMARY;
  final Array<AlternateListener> alternateListeners = new Array<>(false, 16);

  final Array<StoreListener> storeListeners = new Array<>(false, 16);
  final Array<LocationListener> locationListeners = new Array<>(false, 16);

  final EnumIntMap<BodyLoc>  equipped = new EnumIntMap<>(BodyLoc.class, INVALID_ITEM);
  final Array<EquipListener> equipListeners = new Array<>(false, 16);

  final IntIntMap equippedSets = new IntIntMap(); // Indexed using set id
  final IntIntMap setItemsOwned = new IntIntMap(); // Indexed using set item id

  final Array<UpdateListener> updateListeners = new Array<>(false, 16);

  private static final AttributesUpdater updater = new AttributesUpdater(); // TODO: inject

  ItemData(Attributes stats, CharStats.Entry charStats) {
    this.stats = stats;
    this.charStats = charStats;
  }

  public void clear() {
    cursor = INVALID_ITEM;
    charStats = null;
    alternate = D2S.PRIMARY;
    alternateListeners.clear();
    itemData.clear();
    equipped.clear();
    equipListeners.clear();
    equippedSets.clear();
    setItemsOwned.clear();
    updateListeners.clear();
  }

  public void load() {
    Item[] items = itemData.items;
    for (int i = 0, s = itemData.size; i < s; i++) {
      items[i].load();
    }
  }

  void preprocessItems() {
    cursor = INVALID_ITEM;
    Item[] items = itemData.items;
    for (int i = 0, s = itemData.size; i < s; i++) {
      Item item = items[i];
      if (item.quality == Quality.SET) {
        setItemsOwned.getAndIncrement(item.qualityId, 0, 1);
        if (item.location == Location.EQUIPPED && isActive(item)) {
          updateSet(item, 1);
        }
      }
      switch (item.location) {
        case EQUIPPED:
          equipped.put(item.bodyLoc, i);
          break;
        case BELT:
          item.gridY = (byte) -(item.gridX >>> 2);
          item.gridX &= 0x3;
          break;
        case CURSOR:
          assert cursor == INVALID_ITEM : "Only one item should be marked as cursor";
          cursor = i;
          break;
        case STORED:
        case GROUND:
        case UNK5:
        case SOCKET:
        default:
      }
    }

    updateStats();
  }

  public Array<Item> getItems() {
    return itemData;
  }

  /** Replaces this ownership set from a server-authoritative snapshot. */
  public void replaceFromAuthoritativeSnapshot(Array<Item> snapshot) {
    itemData.clear();
    equipped.clear();
    equippedSets.clear();
    setItemsOwned.clear();
    cursor = INVALID_ITEM;
    if (snapshot != null) {
      for (Item item : snapshot) {
        if (item == null) continue;
        int index = itemData.size;
        itemData.add(item);
        if (item.location == null) item.location = Location.STORED;
        switch (item.location) {
          case EQUIPPED:
            if (item.bodyLoc != null && item.bodyLoc != BodyLoc.NONE) {
              equipped.put(item.bodyLoc, index);
              if (item.quality == Quality.SET && isActive(item)) updateSet(item, 1);
            }
            break;
          case CURSOR:
            if (cursor == INVALID_ITEM) cursor = index;
            else item.location = Location.STORED;
            break;
          default:
            break;
        }
        if (item.quality == Quality.SET) setItemsOwned.getAndIncrement(item.qualityId, 0, 1);
      }
    }
    updateStats();
    for (BodyLoc bodyLoc : BodyLoc.values()) {
      int index = equipped.get(bodyLoc);
      if (index != INVALID_ITEM) notifyEquip(bodyLoc, itemData.get(index));
    }
    notifyUpdated();
  }

  public int indexOf(Item item) {
    return itemData.indexOf(item, true);
  }

  public boolean contains(Item item) {
    return itemData.contains(item, true);
  }

  /** Returns whether a non-ground item with the supplied native code exists. */
  public boolean containsItemCode(String code) {
    if (code == null) return false;
    for (int i = 0; i < itemData.size; i++) {
      Item item = itemData.get(i);
      if (item != null && code.equalsIgnoreCase(item.code)
          && item.location != Location.GROUND) return true;
    }
    return false;
  }

  /** Removes one non-ground quest item and keeps cursor/store bookkeeping valid. */
  public boolean removeItemCode(String code) {
    if (code == null) return false;
    for (int i = itemData.size - 1; i >= 0; i--) {
      Item item = itemData.get(i);
      if (item == null || !code.equalsIgnoreCase(item.code)
          || item.location == Location.GROUND) continue;
      if (item.location == Location.STORED) notifyStoreRemoved(item);
      if (cursor == i) cursor = INVALID_ITEM;
      else if (cursor > i) cursor--;
      setLocation(item, null);
      remove(i);
      return true;
    }
    return false;
  }

  /**
   * Atomically replaces one owned item while preserving its inventory/cursor
   * placement. The source remains untouched when no match or replacement is
   * supplied, which prevents a failed quest-item generation from losing the
   * original item.
   */
  public boolean replaceItemCode(String code, Item replacement) {
    if (code == null || replacement == null) return false;
    for (int i = 0; i < itemData.size; i++) {
      Item source = itemData.get(i);
      if (source == null || !code.equalsIgnoreCase(source.code)
          || source.location == Location.GROUND) continue;

      Location location = source.location;
      replacement.location = location;
      replacement.bodyLoc = source.bodyLoc;
      replacement.storeLoc = source.storeLoc;
      replacement.gridX = source.gridX;
      replacement.gridY = source.gridY;
      if (location == Location.STORED) notifyStoreRemoved(source);
      itemData.set(i, replacement);
      if (location == Location.STORED) notifyStoreAdded(replacement);
      return true;
    }
    return false;
  }

  /** Atomically replaces an exact owned item while preserving its placement. */
  public boolean replaceItem(Item source, Item replacement) {
    if (source == null || replacement == null) return false;
    int index = itemData.indexOf(source, true);
    if (index < 0 || source.location == Location.GROUND) return false;
    Location location = source.location;
    replacement.location = location;
    replacement.bodyLoc = source.bodyLoc;
    replacement.storeLoc = source.storeLoc;
    replacement.gridX = source.gridX;
    replacement.gridY = source.gridY;
    if (location == Location.STORED) notifyStoreRemoved(source);
    itemData.set(index, replacement);
    if (location == Location.STORED) notifyStoreAdded(replacement);
    return true;
  }

  public Item getItem(int i) {
    return itemData.get(i);
  }

  public Item getCursor() {
    return cursor == INVALID_ITEM ? null : getItem(cursor);
  }

  /** Detaches the mouse item without deleting ownership so a corpse can hold it. */
  public Item detachCursorItem() {
    if (cursor == INVALID_ITEM) return null;
    Item item = getItem(cursor);
    cursor = INVALID_ITEM;
    setLocation(item, null);
    return item;
  }

  /** Restores a corpse-owned item to the mouse when the cursor is free. */
  public boolean restoreCursorItem(Item item) {
    int index = indexOf(item);
    if (item == null || index == INVALID_ITEM || cursor != INVALID_ITEM) return false;
    cursor = index;
    setLocation(item, Location.CURSOR);
    return true;
  }

  public Item getSlot(BodyLoc bodyLoc) {
    int i = equipped.get(bodyLoc);
    return i == INVALID_ITEM ? null : getItem(i);
  }

  public Item getEquipped(BodyLoc bodyLoc) {
    return getEquipped(bodyLoc, alternate);
  }

  public Item getEquipped(BodyLoc bodyLoc, int alternate) {
    return getSlot(BodyLoc.getAlternate(bodyLoc, alternate));
  }

  /** Returns whether an item can be used by the explicit Throw skills. */
  public static boolean isThrowableWeapon(Item item) {
    return item != null && item.type != null
        && (item.type.is(Type.JAVE) || item.type.is(Type.TKNI) || item.type.is(Type.TAXE));
  }

  /**
   * Finds the active throwable weapon in either hand. The right hand is not
   * preferred blindly: a melee weapon may occupy RARM while the throwable
   * weapon is in LARM (especially after weapon-set switching).
   */
  public Item getEquippedThrowableWeapon() {
    Item right = getEquipped(BodyLoc.RARM);
    if (isThrowableWeapon(right)) return right;
    Item left = getEquipped(BodyLoc.LARM);
    return isThrowableWeapon(left) ? left : null;
  }

  /** Returns the active bow/crossbow, independent of which hand owns it. */
  public Item getEquippedRangedWeapon() {
    Item right = getEquipped(BodyLoc.RARM);
    if (isRangedWeapon(right)) return right;
    Item left = getEquipped(BodyLoc.LARM);
    return isRangedWeapon(left) ? left : null;
  }

  /** Returns whether the item is a bow or crossbow using ItemTypes ancestry. */
  public static boolean isRangedWeapon(Item item) {
    return item != null && item.type != null
        && (item.type.is(Type.BOW) || item.type.is(Type.XBOW));
  }

  /**
   * Finds the active quiver paired with {@code weapon}. D2MOO resolves the
   * weapon ItemTypes.Shoots code and requires the opposite hand to contain
   * that ammunition type; a bow therefore cannot consume crossbow bolts.
   */
  public Item getEquippedAmmo(Item weapon) {
    if (!isRangedWeapon(weapon)) return null;
    Item right = getEquipped(BodyLoc.RARM);
    Item left = getEquipped(BodyLoc.LARM);
    Item candidate = weapon == right ? left : weapon == left ? right : null;
    if (candidate == null || candidate.type == null) return null;

    String shoots = weapon.typeEntry != null ? weapon.typeEntry.Shoots : null;
    if (shoots != null && !shoots.isEmpty()) {
      int ammoType = Riiablo.files.ItemTypes.index(shoots);
      return ammoType >= 0 && candidate.type.is(ammoType) ? candidate : null;
    }
    if (weapon.type.is(Type.BOW)) return candidate.type.is(Type.BOWQ) ? candidate : null;
    if (weapon.type.is(Type.XBOW)) return candidate.type.is(Type.XBOQ) ? candidate : null;
    return null;
  }

  public Item getEquippedRangedAmmo() {
    return getEquippedAmmo(getEquippedRangedWeapon());
  }

  /** Finds an owned item by its stable D2 item id. */
  public Item findItemById(int id) {
    for (int i = 0; i < itemData.size; i++) {
      Item item = itemData.get(i);
      if (item != null && item.id == id) return item;
    }
    return null;
  }

  public boolean isActive(Item item) {
    if (item == null) return false;
    return item.bodyLoc == BodyLoc.getAlternate(item.bodyLoc, alternate);
  }

  public int getAlternate() {
    return alternate;
  }

  public void setAlternate(int alternate) {
    if (this.alternate != alternate) {
      this.alternate = alternate;
      updateStats();
      Item LH = getEquipped(BodyLoc.LARM);
      Item RH = getEquipped(BodyLoc.RARM);
      
      // Log weapon quantity when switching weapons
      com.riiablo.logger.Logger log = com.riiablo.logger.LogManager.getLogger(ItemData.class);
      log.info("[WEAPON_SWITCH] Switching to alternate: {}", alternate);
      if (RH != null && RH.base != null) {
        boolean isRanged = RH.type.is(com.riiablo.item.Type.BOW) || RH.type.is(com.riiablo.item.Type.XBOW);
        boolean isThrowable = RH.type.is(com.riiablo.item.Type.JAVE) || 
                             RH.type.is(com.riiablo.item.Type.TKNI) || 
                             RH.type.is(com.riiablo.item.Type.TAXE);
        if (isRanged || isThrowable) {
          com.riiablo.attributes.StatRef quantity = RH.attrs.base().get(com.riiablo.attributes.Stat.quantity);
          int qty = quantity != null ? quantity.asInt() : 0;
          log.info("[WEAPON_SWITCH] Right hand {} weapon: {} (code: {}), quantity: {}", 
              isThrowable ? "throwable" : "ranged", RH.base.name, RH.code, qty);
        }
      }
      if (LH != null && LH.base != null) {
        boolean isRanged = LH.type.is(com.riiablo.item.Type.BOW) || LH.type.is(com.riiablo.item.Type.XBOW);
        boolean isThrowable = LH.type.is(com.riiablo.item.Type.JAVE) || 
                             LH.type.is(com.riiablo.item.Type.TKNI) || 
                             LH.type.is(com.riiablo.item.Type.TAXE);
        if (isRanged || isThrowable) {
          com.riiablo.attributes.StatRef quantity = LH.attrs.base().get(com.riiablo.attributes.Stat.quantity);
          int qty = quantity != null ? quantity.asInt() : 0;
          log.info("[WEAPON_SWITCH] Left hand {} weapon: {} (code: {}), quantity: {}", 
              isThrowable ? "throwable" : "ranged", LH.base.name, LH.code, qty);
        }
      }
      
      notifyAlternated(alternate, LH, RH);
    }
  }

  public int alternate() {
    int alt = alternate > D2S.PRIMARY ? D2S.PRIMARY : D2S.SECONDARY;
    setAlternate(alt);
    return alt;
  }

  public int add(Item item) {
    int i = itemData.size;
    itemData.add(item);
    if (item.quality == Quality.SET) setItemsOwned.getAndIncrement(item.qualityId, 0, 1);
    return i;
  }

  public Item remove(int i) {
    Item item = getItem(i);
    itemData.removeIndex(i);
    int[] vals = equipped.values();
    for (int j = 0, s = vals.length; j < s; j++) if (vals[j] > i) vals[j]--;
    if (item.quality == Quality.SET) setItemsOwned.getAndIncrement(item.qualityId, 0, -1);
    return item;
  }

  /** Removes an owned inventory item and emits the normal store lifecycle events. */
  public boolean removeOwnedItem(int i) {
    if (i < 0 || i >= itemData.size) return false;
    Item item = itemData.get(i);
    if (item == null || item.location != Location.STORED) return false;
    if (item.storeLoc != StoreLoc.INVENTORY) return false;
    notifyStoreRemoved(item);
    if (cursor == i) cursor = INVALID_ITEM;
    else if (cursor > i) cursor--;
    setLocation(item, null);
    remove(i);
    // Keep derived equipment/character stats and network listeners in sync
    // with authoritative inventory mutations (including gem shrines).
    // Headless/unit contexts may remove an item before the Excel tables are
    // loaded (Type's static table depends on Riiablo.files). The authoritative
    // server has files loaded, while tests still need bookkeeping to succeed.
    if (Riiablo.files != null) {
      updateStats();
      notifyUpdated();
    }
    return true;
  }

  /** Removes an exact inventory item without relying on a stale array index. */
  public boolean removeInventoryItem(Item item) {
    if (item == null) return false;
    int index = indexOf(item);
    return index != INVALID_ITEM && itemData.get(index) == item
        && removeOwnedItem(index);
  }

  /**
   * Atomically packs an item into the 10x4 character inventory.  The item is
   * not added or mutated when no rectangle is available.  This is the server
   * side equivalent of the native inventory placement used by quest rewards.
   */
  public boolean addToInventory(Item item) {
    if (item == null || contains(item) || item.base == null
        || item.base.invwidth <= 0 || item.base.invheight <= 0
        || item.base.invwidth > 10 || item.base.invheight > 4) return false;

    boolean[][] occupied = new boolean[4][10];
    IntArray inventory = getStore(StoreLoc.INVENTORY);
    for (int n = 0; n < inventory.size; n++) {
      Item stored = getItem(inventory.get(n));
      if (stored == null || stored.base == null) continue;
      int x = stored.gridX;
      int y = stored.gridY;
      int width = stored.base.invwidth;
      int height = stored.base.invheight;
      // Treat malformed existing coordinates as occupied/unsafe instead of
      // risking that a quest reward overwrites a persisted item.
      if (x < 0 || y < 0 || width <= 0 || height <= 0
          || x + width > 10 || y + height > 4) return false;
      for (int dy = 0; dy < height; dy++) {
        for (int dx = 0; dx < width; dx++) occupied[y + dy][x + dx] = true;
      }
    }

    int x = -1, y = -1;
    outer:
    for (int candidateY = 0; candidateY <= 4 - item.base.invheight; candidateY++) {
      for (int candidateX = 0; candidateX <= 10 - item.base.invwidth; candidateX++) {
        boolean free = true;
        for (int dy = 0; dy < item.base.invheight && free; dy++) {
          for (int dx = 0; dx < item.base.invwidth; dx++) {
            if (occupied[candidateY + dy][candidateX + dx]) {
              free = false;
              break;
            }
          }
        }
        if (free) {
          x = candidateX;
          y = candidateY;
          break outer;
        }
      }
    }
    if (x < 0) return false;

    int index = add(item);
    store(StoreLoc.INVENTORY, index, x, y);
    return true;
  }

  /** Packs an already-owned corpse item into the first free inventory rectangle. */
  public boolean moveOwnedToInventory(Item item) {
    int itemIndex = indexOf(item);
    if (item == null || itemIndex == INVALID_ITEM || item.base == null
        || item.base.invwidth <= 0 || item.base.invheight <= 0
        || item.base.invwidth > 10 || item.base.invheight > 4) return false;

    boolean[][] occupied = new boolean[4][10];
    IntArray inventory = getStore(StoreLoc.INVENTORY);
    for (int n = 0; n < inventory.size; n++) {
      int storedIndex = inventory.get(n);
      if (storedIndex == itemIndex) continue;
      Item stored = getItem(storedIndex);
      if (stored == null || stored.base == null) continue;
      int x = stored.gridX;
      int y = stored.gridY;
      int width = stored.base.invwidth;
      int height = stored.base.invheight;
      if (x < 0 || y < 0 || width <= 0 || height <= 0
          || x + width > 10 || y + height > 4) return false;
      for (int dy = 0; dy < height; dy++) {
        for (int dx = 0; dx < width; dx++) occupied[y + dy][x + dx] = true;
      }
    }

    for (int y = 0; y <= 4 - item.base.invheight; y++) {
      for (int x = 0; x <= 10 - item.base.invwidth; x++) {
        boolean free = true;
        for (int dy = 0; dy < item.base.invheight && free; dy++) {
          for (int dx = 0; dx < item.base.invwidth; dx++) {
            if (occupied[y + dy][x + dx]) {
              free = false;
              break;
            }
          }
        }
        if (!free) continue;
        store(StoreLoc.INVENTORY, itemIndex, x, y);
        return true;
      }
    }
    return false;
  }

  /**
   * Consumes one unit from the first matching stack on a stored inventory page.
   * The final unit removes the item and emits the normal store-removal event.
   */
  public boolean consumeStoredItemQuantity(StoreLoc storeLoc, int itemType) {
    IntArray stored = getStore(storeLoc);
    for (int n = 0; n < stored.size; n++) {
      int index = stored.get(n);
      Item item = getItem(index);
      if (item == null || item.type == null || !item.type.is(itemType)) continue;

      StatRef quantity = item.attrs == null ? null : item.attrs.base().get(Stat.quantity);
      int current = quantity == null ? 0 : quantity.asInt();
      if (current <= 1) {
        notifyStoreRemoved(item);
        remove(index);
      } else {
        item.attrs.base().put(Stat.quantity, current - 1);
        item.attrs.reset();
      }
      return true;
    }
    return false;
  }

  public void addAll(Array<? extends Item> items) {
    itemData.addAll(items);
  }

  public IntArray getLocation(Location location) {
    return getLocation(location, StoreLoc.NONE);
  }

  public IntArray getLocation(Location location, StoreLoc storeLoc) {
    Item[] items = itemData.items;
    IntArray copy = new IntArray(items.length);
    for (int i = 0, s = itemData.size; i < s; i++) {
      Item item = items[i];
      if (item.location != location) continue;
      if (location == Location.STORED && item.storeLoc != storeLoc) continue;
      copy.add(i);
    }

    return copy;
  }

  public IntArray getStore(StoreLoc storeLoc) {
    return getLocation(Location.STORED, storeLoc);
  }

  public Array<Item> toItemArray(IntArray items) {
    Array<Item> copy = new Array<>(false, items.size, Item.class);
    int[] cache = items.items;
    for (int i = 0, s = items.size, j; i < s; i++) {
      j = cache[i];
      copy.add(itemData.get(j));
    }
    return copy;
  }

  void pickup(Item item) {
    assert cursor == INVALID_ITEM;
    cursor = add(item);
    setLocation(item, Location.CURSOR);
  }

  // TODO: should item location change if the item is dropped? is this what UNK3 and UNK5 represent?
  void drop() {
    assert cursor != INVALID_ITEM;
    Item item = remove(cursor);
    cursor = INVALID_ITEM;
    setLocation(item, null);
  }

  void pickup(int i) {
    assert cursor == INVALID_ITEM;
    Item item = itemData.get(i);
    if (item.location == Location.STORED) notifyStoreRemoved(item);
    cursor = i;
    setLocation(item, Location.CURSOR);
  }

  void storeCursor(StoreLoc storeLoc, int x, int y) {
    assert cursor != INVALID_ITEM;
    store(storeLoc, cursor, x, y);
    cursor = INVALID_ITEM;
  }

  void store(StoreLoc storeLoc, int i, int x, int y) {
    Item item = itemData.get(i);
    setLocation(item, Location.STORED);
    item.storeLoc = storeLoc;
    item.gridX = (byte) x;
    item.gridY = (byte) y;
    notifyStoreAdded(item);
  }

  void equip(BodyLoc bodyLoc, Item item) {
    assert !itemData.contains(item, true);
    equip(bodyLoc, add(item));
  }

  void equip(BodyLoc bodyLoc, int i) {
    Item item = itemData.get(i);
    setLocation(item, Location.EQUIPPED);
    item.bodyLoc = bodyLoc;
    int j = equipped.put(bodyLoc, i);
    assert j == INVALID_ITEM : "Item " + j + " should have been unequipped by this point.";
    updateStats(); // TODO: add support for appending to existing stats if this is an additional item
    updateSet(item, 1);
    
    // Log weapon quantity when equipping ranged/throwing weapons
    if (item != null && item.base != null && (bodyLoc == BodyLoc.RARM || bodyLoc == BodyLoc.LARM)) {
      boolean isRanged = item.type.is(com.riiablo.item.Type.BOW) || item.type.is(com.riiablo.item.Type.XBOW);
      boolean isThrowable = item.type.is(com.riiablo.item.Type.JAVE) || 
                           item.type.is(com.riiablo.item.Type.TKNI) || 
                           item.type.is(com.riiablo.item.Type.TAXE);
      if (isRanged || isThrowable) {
        com.riiablo.attributes.StatRef quantity = item.attrs.base().get(com.riiablo.attributes.Stat.quantity);
        int qty = quantity != null ? quantity.asInt() : 0;
        com.riiablo.logger.Logger log = com.riiablo.logger.LogManager.getLogger(ItemData.class);
        log.info("[WEAPON_EQUIP] Equipped {} weapon: {} (code: {}), quantity: {}", 
            isThrowable ? "throwable" : "ranged", item.base.name, item.code, qty);
      }
    }
    
    notifyEquip(bodyLoc, item);
  }

  int unequip(BodyLoc bodyLoc) {
    int i = equipped.remove(bodyLoc);
    if (i == INVALID_ITEM) {
      // No item equipped at this location, nothing to unequip
      return INVALID_ITEM;
    }
    Item item = itemData.get(i);
    updateStats();
    updateSet(item, -1);
    notifyUnequip(bodyLoc, item);
    return i;
  }

  /**
   * Public method to unequip an item from a body location.
   * Used by systems that need to remove equipment (e.g., player death).
   * 
   * @param bodyLoc The body location to unequip from
   * @return The item index that was unequipped, or INVALID_ITEM if nothing was equipped
   */
  public int unequipItem(BodyLoc bodyLoc) {
    int i = equipped.get(bodyLoc);
    if (i == INVALID_ITEM) {
      return INVALID_ITEM;
    }
    return unequip(bodyLoc);
  }

  /**
   * Public method to equip an item to a body location.
   * Used by systems that need to equip items (e.g., corpse retrieval).
   * 
   * @param bodyLoc The body location to equip to
   * @param itemIndex The index of the item in the itemData array
   */
  public void equipItem(BodyLoc bodyLoc, int itemIndex) {
    if (itemIndex < 0 || itemIndex >= itemData.size) {
      throw new IllegalArgumentException("Item index out of bounds: " + itemIndex);
    }
    // Check if slot is already occupied
    int existing = equipped.get(bodyLoc);
    if (existing != INVALID_ITEM) {
      throw new IllegalStateException("Body location " + bodyLoc + " is already occupied by item " + existing);
    }
    equip(bodyLoc, itemIndex);
  }

  /** Equips an existing item by identity so stored corpse data survives index shifts. */
  public void equipItem(BodyLoc bodyLoc, Item item) {
    int itemIndex = indexOf(item);
    if (itemIndex == INVALID_ITEM) {
      throw new IllegalArgumentException("Item is no longer owned by this character");
    }
    equipItem(bodyLoc, itemIndex);
  }

  /** Rebuilds aggregate attributes from base stats and currently equipped items. */
  public void updateStats() {
    StatRef stat;
    int equippedArmorClass = 0;
    final UpdateSequence update = updater.update(stats, charStats);
    int[] cache = equipped.values();
    for (int i = 0, s = cache.length, j; i < s; i++) {
      j = cache[i];
      if (j == INVALID_ITEM) continue;
      Item item = itemData.get(j);
      // Native item records always carry a type.  Keep malformed/partially
      // constructed records out of the aggregate pass so a failed item
      // transaction cannot crash the authoritative server while rebuilding
      // stats (test fixtures and legacy saves may omit the type).
      if (isActive(item) && item.type != null) {
        item.update(updater, stats, charStats, equippedSets);
        // Item.update() has already folded the selected item property lists into
        // remaining(). Adding base() or aggregate() here duplicates native item
        // stats such as armorclass. D2MOO adds equipped armor defense once, then
        // UNITS_GetDefense adds dexterity / 4.
        update.add(item.attrs.remaining());
        
        // Directly add weapon damage from item base() to character aggregate()
        // This ensures weapon damage is properly aggregated even if not in character base()
        // Reference D2MOD: STAT_SECONDARY_MINDAMAGE/MAXDAMAGE is for two-handed weapons (WieldType == 2)
        // For one-handed weapons (including dual wielding), use mindamage/maxdamage
        // For two-handed weapons, use secondary_mindamage/maxdamage (from weapon._2handmindam/_2handmaxdam)
        // StatListRef.get(stat) returns a reusable tuple.  Keep independent
        // refs here; otherwise reading max damage overwrites the min-damage
        // ref and the aggregate receives max twice while min is lost.
        StatRef itemMinDmg = item.attrs.base().get(Stat.mindamage, StatRef.obtain());
        StatRef itemMaxDmg = item.attrs.base().get(Stat.maxdamage, StatRef.obtain());
        StatRef itemSecondaryMinDmg = item.attrs.base().get(Stat.secondary_mindamage, StatRef.obtain());
        StatRef itemSecondaryMaxDmg = item.attrs.base().get(Stat.secondary_maxdamage, StatRef.obtain());
        
        if (item.type.is(Type.WEAP)) {
          // Check if this is a two-handed weapon (has secondary damage values)
          if (itemSecondaryMinDmg != null && itemSecondaryMaxDmg != null && 
              itemSecondaryMinDmg.asInt() > 0 && itemSecondaryMaxDmg.asInt() > 0) {
            // Two-handed weapon: use secondary_mindamage/maxdamage
            stats.aggregate().put(Stat.secondary_mindamage, itemSecondaryMinDmg.asInt());
            stats.aggregate().put(Stat.secondary_maxdamage, itemSecondaryMaxDmg.asInt());
          } else if (itemMinDmg != null && itemMaxDmg != null) {
            // One-handed weapon (including dual wielding): use mindamage/maxdamage
            // For dual wielding, both hands use mindamage/maxdamage, attack sequence determines which weapon is used
            stats.aggregate().add(itemMinDmg);
            stats.aggregate().add(itemMaxDmg);
          }
        } else if (itemMinDmg != null && itemMaxDmg != null) {
          // Other items (shields, etc.): add to mindamage/maxdamage
          stats.aggregate().add(itemMinDmg);
          stats.aggregate().add(itemMaxDmg);
        }
        
        if ((stat = item.attrs.get(Stat.armorclass)) != null) {
          // Base armor defense is not part of remaining(), so carry the
          // item's fully-updated defense into the character once.
          equippedArmorClass += stat.asInt();
        }
      }
    }

    IntArray inventoryItems = getStore(StoreLoc.INVENTORY);
    cache = inventoryItems.items;
    for (int i = 0, s = inventoryItems.size, j; i < s; i++) {
      j = cache[i];
      if (j == INVALID_ITEM) continue;
      Item item = itemData.get(j);
      if (item == null || item.type == null) continue;
      if (item.type.is(Type.CHAR)) {
        item.update(updater, stats, charStats, equippedSets);
        update.add(item.attrs.remaining());
      } else if (item.type.is(Type.BOOK)) { // TODO: may not be needed since not stat -- calculate elsewhere?
        item.update(updater, stats, charStats, equippedSets);
      }
    }
    update.apply();

    if (equippedArmorClass != 0) {
      stats.aggregate().add(Stat.armorclass, equippedArmorClass);
    }
    
    // After update.apply(), add throwable weapon damage to aggregate
    // This must be done AFTER apply() because apply() resets and recalculates aggregate
    // Re-get equipped items cache since it may have been modified
    int[] equippedCache = equipped.values();
    for (int i = 0, s = equippedCache.length, j; i < s; i++) {
      j = equippedCache[i];
      if (j == INVALID_ITEM) continue;
      Item item = itemData.get(j);
      if (isActive(item) && item.type != null && item.type.is(Type.WEAP)) {
        // Check if this is a throwable weapon (javelin, throwing knife, throwing axe)
        boolean isThrowable = item.type.is(com.riiablo.item.Type.JAVE) || 
                             item.type.is(com.riiablo.item.Type.TKNI) || 
                             item.type.is(com.riiablo.item.Type.TAXE);
        
        if (isThrowable) {
          // Throwable weapon: sync item_throw_mindamage/maxdamage to player stats
          // Use add() to accumulate with other stats
          // Use independent refs for min/max; StatListRef.get(stat) reuses a
          // mutable tuple and would otherwise turn both variables into max.
          StatRef itemThrowMinDmg = item.attrs.base().get(Stat.item_throw_mindamage, StatRef.obtain());
          StatRef itemThrowMaxDmg = item.attrs.base().get(Stat.item_throw_maxdamage, StatRef.obtain());
          if (itemThrowMinDmg != null && itemThrowMaxDmg != null) {
            stats.aggregate().add(itemThrowMinDmg);
            stats.aggregate().add(itemThrowMaxDmg);
            com.riiablo.logger.Logger log = com.riiablo.logger.LogManager.getLogger(ItemData.class);
            log.info("[THROW_WEAPON_SYNC] Synced throw damage: item={}, minDamage={}, maxDamage={}, itemCode={}", 
                item, itemThrowMinDmg.asInt(), itemThrowMaxDmg.asInt(), item.code);
          } else {
            com.riiablo.logger.Logger log = com.riiablo.logger.LogManager.getLogger(ItemData.class);
            log.warn("[THROW_WEAPON_SYNC] Throwable weapon but no throw damage stats: item={}, itemCode={}, minDmgRef={}, maxDmgRef={}", 
                item, item.code, itemThrowMinDmg, itemThrowMaxDmg);
          }
        }
      }
    }
    
    notifyUpdated();
  }

  private void updateSet(Item item, int add) {
    if (item != null && item.quality == Quality.SET) {
      SetItems.Entry setItem = (SetItems.Entry) item.qualityData;
      int id = Riiablo.files.Sets.index(setItem.set);
      equippedSets.getAndIncrement(id, 0, add);
    }
  }

  public IntIntMap getEquippedSets() {
    return equippedSets;
  }

  public int getOwnedSetCount(int setId) {
    return setItemsOwned.get(setId, 0);
  }

  public boolean addEquipListener(EquipListener l) {
    equipListeners.add(l);
    return true;
  }

  private void notifyEquip(BodyLoc bodyLoc, Item item) {
    for (EquipListener l : equipListeners) l.onEquip(this, bodyLoc, item);
  }

  private void notifyUnequip(BodyLoc bodyLoc, Item item) {
    for (EquipListener l : equipListeners) l.onUnequip(this, bodyLoc, item);
  }

  public interface EquipListener {
    void onEquip(ItemData items, BodyLoc bodyLoc, Item item);
    void onUnequip(ItemData items, BodyLoc bodyLoc, Item item);
  }

  public boolean addAlternateListener(AlternateListener l) {
    alternateListeners.add(l);
    return true;
  }

  private void notifyAlternated(int alternate, Item LH, Item RH) {
    for (AlternateListener l : alternateListeners) l.onAlternated(this, alternate, LH, RH);
  }

  public interface AlternateListener {
    void onAlternated(ItemData items, int alternate, Item LH, Item RH);
  }

  public boolean addUpdateListener(UpdateListener l) {
    if (updateListeners.contains(l, true)) return false;
    updateListeners.add(l);
    return true;
  }

  private void notifyUpdated() {
    for (UpdateListener l : updateListeners) l.onUpdated(this);
  }

  public interface UpdateListener {
    void onUpdated(ItemData itemData);
  }

  public boolean addStoreListener(StoreListener l) {
    storeListeners.add(l);
    return true;
  }

  private void notifyStoreAdded(Item item) {
    for (StoreListener l : storeListeners) l.onAdded(this, item.storeLoc, item);
  }

  private void notifyStoreRemoved(Item item) {
    for (StoreListener l : storeListeners) l.onRemoved(this, item.storeLoc, item);
  }

  public interface StoreListener {
    void onAdded(ItemData items, StoreLoc storeLoc, Item item);
    void onRemoved(ItemData items, StoreLoc storeLoc, Item item);
  }

  void setLocation(Item item, Location location) {
    if (item.location != location) {
      Location oldLocation = item.location;
      item.location = location;
      notifyLocationChanged(item, oldLocation);
    }
  }

  public boolean addLocationListener(LocationListener l) {
    locationListeners.add(l);
    return true;
  }

  private void notifyLocationChanged(Item item, Location oldLocation) {
    for (LocationListener l : locationListeners) l.onChanged(this, oldLocation, item.location, item);
  }

  public interface LocationListener {
    void onChanged(ItemData items, Location oldLocation, Location location, Item item);
  }
}
