package com.riiablo.engine.server.item;

import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;
import com.riiablo.net.packet.d2gs.ItemMoveFailure;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Weapons;

/** Pure validation rules for authoritative item moves. No state is mutated here. */
public final class ItemMoveValidator {
  private ItemMoveValidator() {}

  public static byte validate(CharData character, ItemMoveIntent intent) {
    if (character == null || character.getItems() == null) return ItemMoveFailure.PLAYER_NOT_FOUND;
    if (intent == null || intent.operation < ItemMoveOperation.GROUND_TO_CURSOR
        || intent.operation > ItemMoveOperation.SWAP_BELT_ITEM) return ItemMoveFailure.INVALID_OPERATION;
    if (intent.merc) return ItemMoveFailure.MERC_NOT_SUPPORTED;
    ItemData data = character.getItems();
    Item cursor = data.getCursor();
    switch (intent.operation) {
      case ItemMoveOperation.GROUND_TO_CURSOR:
        return cursor != null ? ItemMoveFailure.CURSOR_OCCUPIED : ItemMoveFailure.NONE;
      case ItemMoveOperation.CURSOR_TO_GROUND:
        return cursor == null ? ItemMoveFailure.CURSOR_EMPTY : ItemMoveFailure.NONE;
      case ItemMoveOperation.STORE_TO_CURSOR: {
        Item item = ownedByIdOrIndex(data, intent.itemId);
        if (item == null) return ItemMoveFailure.ITEM_NOT_OWNED;
        if (item.location != Location.STORED || item.storeLoc == StoreLoc.NONE)
          return ItemMoveFailure.ITEM_NOT_OWNED;
        return cursor != null ? ItemMoveFailure.CURSOR_OCCUPIED : ItemMoveFailure.NONE;
      }
      case ItemMoveOperation.CURSOR_TO_STORE:
        if (cursor == null) return ItemMoveFailure.CURSOR_EMPTY;
        StoreLoc store = StoreLoc.valueOf(intent.storeLoc);
        if (store == null || store == StoreLoc.NONE) return ItemMoveFailure.INVALID_STORE;
        return validateGrid(cursor, data, store, intent.x, intent.y, -1);
      case ItemMoveOperation.SWAP_STORE_ITEM: {
        if (cursor == null) return ItemMoveFailure.CURSOR_EMPTY;
        Item target = ownedByIdOrIndex(data, intent.itemId);
        if (target == null || target.location != Location.STORED)
          return ItemMoveFailure.ITEM_NOT_OWNED;
        StoreLoc targetStore = StoreLoc.valueOf(intent.storeLoc);
        if (targetStore == null || targetStore == StoreLoc.NONE) return ItemMoveFailure.INVALID_STORE;
        return validateGrid(cursor, data, targetStore, intent.x, intent.y, data.indexOf(target));
      }
      case ItemMoveOperation.BODY_TO_CURSOR: {
        BodyLoc body = body(intent.bodyLoc);
        if (body == null || body == BodyLoc.NONE) return ItemMoveFailure.INVALID_BODY_LOC;
        return data.getSlot(body) == null ? ItemMoveFailure.BODY_SLOT_EMPTY
            : (cursor != null ? ItemMoveFailure.CURSOR_OCCUPIED : ItemMoveFailure.NONE);
      }
      case ItemMoveOperation.CURSOR_TO_BODY: {
        BodyLoc body = body(intent.bodyLoc);
        if (body == null || body == BodyLoc.NONE) return ItemMoveFailure.INVALID_BODY_LOC;
        if (cursor == null) return ItemMoveFailure.CURSOR_EMPTY;
        return validateEquip(character, cursor, body, data.getSlot(body));
      }
      case ItemMoveOperation.SWAP_BODY_ITEM: {
        BodyLoc body = body(intent.bodyLoc);
        if (body == null || body == BodyLoc.NONE) return ItemMoveFailure.INVALID_BODY_LOC;
        if (cursor == null) return ItemMoveFailure.CURSOR_EMPTY;
        Item old = data.getSlot(body);
        if (old == null) return validateEquip(character, cursor, body, null);
        return validateEquip(character, cursor, body, old);
      }
      case ItemMoveOperation.BELT_TO_CURSOR: {
        Item item = ownedByIdOrIndex(data, intent.itemId);
        if (item == null || item.location != Location.BELT) return ItemMoveFailure.ITEM_NOT_OWNED;
        return cursor != null ? ItemMoveFailure.CURSOR_OCCUPIED : ItemMoveFailure.NONE;
      }
      case ItemMoveOperation.CURSOR_TO_BELT:
        if (cursor == null) return ItemMoveFailure.CURSOR_EMPTY;
        if (cursor.typeEntry == null || !cursor.typeEntry.Beltable) return ItemMoveFailure.ITEM_NOT_BELTABLE;
        if (intent.x < 0 || intent.x >= 4 || intent.y < 0 || intent.y >= 4)
          return ItemMoveFailure.INVALID_BELT_SLOT;
        for (Item other : data.getItems()) {
          if (other != null && other != cursor && other.location == Location.BELT
              && other.gridX == intent.x && other.gridY == intent.y) return ItemMoveFailure.BELT_SLOT_OCCUPIED;
        }
        return ItemMoveFailure.NONE;
      case ItemMoveOperation.SWAP_BELT_ITEM: {
        if (cursor == null) return ItemMoveFailure.CURSOR_EMPTY;
        if (cursor.typeEntry == null || !cursor.typeEntry.Beltable) return ItemMoveFailure.ITEM_NOT_BELTABLE;
        Item target = ownedByIdOrIndex(data, intent.itemId);
        return target == null || target.location != Location.BELT
            ? ItemMoveFailure.ITEM_NOT_OWNED : ItemMoveFailure.NONE;
      }
      default: return ItemMoveFailure.INVALID_OPERATION;
    }
  }

  private static Item ownedByIdOrIndex(ItemData data, int idOrIndex) {
    for (int i = 0; i < data.getItems().size; i++) {
      Item item = data.getItems().get(i);
      if (item != null && item.id == idOrIndex) return item;
    }
    return idOrIndex >= 0 && idOrIndex < data.getItems().size ? data.getItems().get(idOrIndex) : null;
  }

  private static byte validateGrid(Item item, ItemData data, StoreLoc store,
                                                int x, int y, int ignoredIndex) {
    if (store != StoreLoc.INVENTORY && store != StoreLoc.CUBE && store != StoreLoc.STASH)
      return ItemMoveFailure.INVALID_STORE;
    if (x < 0 || y < 0 || item.base == null || item.base.invwidth <= 0 || item.base.invheight <= 0)
      return ItemMoveFailure.INVALID_POSITION;
    int width = store == StoreLoc.INVENTORY ? 10 : 10;
    int height = store == StoreLoc.INVENTORY ? 4 : 4;
    if (x + item.base.invwidth > width || y + item.base.invheight > height)
      return ItemMoveFailure.INVALID_POSITION;
    for (int i = 0; i < data.getItems().size; i++) {
      Item other = data.getItems().get(i);
      if (other == null || other == item || other.location != Location.STORED || other.storeLoc != store
          || other.base == null) continue;
      if (x < other.gridX + other.base.invwidth && x + item.base.invwidth > other.gridX
          && y < other.gridY + other.base.invheight && y + item.base.invheight > other.gridY)
        return ItemMoveFailure.INVENTORY_OCCUPIED;
    }
    return ItemMoveFailure.NONE;
  }

  private static BodyLoc body(int ordinal) {
    return ordinal < 0 || ordinal >= BodyLoc.values().length ? null : BodyLoc.valueOf(ordinal);
  }

  private static byte validateEquip(CharData character, Item item, BodyLoc body, Item replacing) {
    if (item == null || item.typeEntry == null || item.typeEntry.BodyLoc == null) return ItemMoveFailure.BODY_LOC_MISMATCH;
    boolean allowed = false;
    String code = body.name().toLowerCase();
    for (String loc : item.typeEntry.BodyLoc) if (loc != null && code.equalsIgnoreCase(loc)) { allowed = true; break; }
    if (!allowed) return ItemMoveFailure.BODY_LOC_MISMATCH;
    if (replacing != null && replacing == item) return ItemMoveFailure.BODY_SLOT_OCCUPIED;
    if (item.classOnly != Item.NO_CLASS_ONLY) {
      int classId = character.charClass & 0xFF;
      if (item.classOnly != classId && (item.classOnly & (1 << classId)) == 0)
        return ItemMoveFailure.REQUIREMENTS_NOT_MET;
    }
    if (item.base instanceof Weapons.Entry && ((Weapons.Entry) item.base)._2handed
        && BodyLoc.isWeaponLoc(body)) {
      BodyLoc opposite = body == BodyLoc.RARM ? BodyLoc.LARM : BodyLoc.RARM;
      if (character.getItems().getSlot(opposite) != null && character.getItems().getSlot(opposite) != replacing)
        return ItemMoveFailure.BODY_SLOT_OCCUPIED;
    }
    if (item.attrs != null) {
      int level = value(character.getStats().get(Stat.level));
      int reqLevel = value(item.attrs.base().get(Stat.item_levelreq));
      int reqStr = value(item.attrs.base().get(Stat.reqstr));
      int reqDex = value(item.attrs.base().get(Stat.reqdex));
      if (level < reqLevel || value(character.getStats().get(Stat.strength)) < reqStr
          || value(character.getStats().get(Stat.dexterity)) < reqDex) return ItemMoveFailure.REQUIREMENTS_NOT_MET;
    }
    return ItemMoveFailure.NONE;
  }

  private static int value(com.riiablo.attributes.StatRef stat) { return stat == null ? 0 : stat.asInt(); }
}
