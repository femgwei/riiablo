package com.riiablo.engine.server.item;

import java.util.HashMap;
import java.util.Map;

import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import com.riiablo.net.packet.d2gs.ItemMoveFailure;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.item.VendorPricing;

/**
 * Small transaction boundary around CharData's legacy item methods. All
 * validation happens before mutation; failed operations leave the character
 * untouched and can be answered with its current snapshot by the transport.
 */
public final class AuthoritativeItemMoveService {
  public static final class Outcome {
    public final boolean success;
    public final byte failure;
    public final long revision;
    public Outcome(boolean success, byte failure, long revision) {
      this.success = success;
      this.failure = failure;
      this.revision = revision;
    }
  }

  private final Map<Integer, Long> revisions = new HashMap<>();

  public synchronized long revision(int playerEntityId) {
    Long value = revisions.get(playerEntityId);
    return value == null ? 0L : value;
  }

  public synchronized void reset(int playerEntityId) { revisions.remove(playerEntityId); }

  public synchronized Outcome apply(int playerEntityId, CharData character, ItemMoveIntent intent) {
    long current = revision(playerEntityId);
    if (character == null) return new Outcome(false, ItemMoveFailure.PLAYER_NOT_FOUND, current);
    if (intent == null || intent.operation < ItemMoveOperation.GROUND_TO_CURSOR
        || intent.operation > ItemMoveOperation.SWAP_BELT_ITEM)
      return new Outcome(false, ItemMoveFailure.INVALID_OPERATION, current);
    if (intent.revision != current)
      return new Outcome(false, ItemMoveFailure.STALE_INVENTORY, current);

    byte failure = ItemMoveValidator.validate(character, intent);
    if (failure != ItemMoveFailure.NONE) return new Outcome(false, failure, current);

    try {
      switch (intent.operation) {
        case ItemMoveOperation.STORE_TO_CURSOR:
          character.storeToCursor(indexOf(character, intent.itemId)); break;
        case ItemMoveOperation.CURSOR_TO_STORE:
          character.cursorToStore(com.riiablo.item.StoreLoc.valueOf(intent.storeLoc), intent.x, intent.y); break;
        case ItemMoveOperation.SWAP_STORE_ITEM:
          character.swapStoreItem(indexOf(character, intent.itemId),
              com.riiablo.item.StoreLoc.valueOf(intent.storeLoc), intent.x, intent.y); break;
        case ItemMoveOperation.BODY_TO_CURSOR:
          character.bodyToCursor(com.riiablo.item.BodyLoc.valueOf(intent.bodyLoc), false); break;
        case ItemMoveOperation.CURSOR_TO_BODY:
          character.cursorToBody(com.riiablo.item.BodyLoc.valueOf(intent.bodyLoc), false); break;
        case ItemMoveOperation.SWAP_BODY_ITEM:
          character.swapBodyItem(com.riiablo.item.BodyLoc.valueOf(intent.bodyLoc), false); break;
        case ItemMoveOperation.BELT_TO_CURSOR:
          character.beltToCursor(indexOf(character, intent.itemId)); break;
        case ItemMoveOperation.CURSOR_TO_BELT:
          character.cursorToBelt(intent.x, intent.y); break;
        case ItemMoveOperation.SWAP_BELT_ITEM:
          character.swapBeltItem(indexOf(character, intent.itemId)); break;
        case ItemMoveOperation.GROUND_TO_CURSOR:
        case ItemMoveOperation.CURSOR_TO_GROUND:
          // Ground entities are owned by ECS and use the overloads below.
          return new Outcome(false, ItemMoveFailure.GROUND_ITEM_NOT_FOUND, current);
        default:
          return new Outcome(false, ItemMoveFailure.INVALID_OPERATION, current);
      }
    } catch (Throwable t) {
      return new Outcome(false, ItemMoveFailure.MUTATION_FAILED, current);
    }
    long next = current + 1L;
    revisions.put(playerEntityId, next);
    return new Outcome(true, ItemMoveFailure.NONE, next);
  }

  /** Applies a validated pickup after the ECS has resolved the ground item. */
  public synchronized Outcome pickup(int playerEntityId, CharData character,
                                     ItemMoveIntent intent, Item groundItem) {
    long current = revision(playerEntityId);
    if (character == null) return new Outcome(false, ItemMoveFailure.PLAYER_NOT_FOUND, current);
    if (intent == null || intent.operation != ItemMoveOperation.GROUND_TO_CURSOR)
      return new Outcome(false, ItemMoveFailure.INVALID_OPERATION, current);
    if (intent.revision != current) return new Outcome(false, ItemMoveFailure.STALE_INVENTORY, current);
    byte failure = ItemMoveValidator.validate(character, intent);
    if (failure != ItemMoveFailure.NONE) return new Outcome(false, failure, current);
    if (groundItem == null) return new Outcome(false, ItemMoveFailure.GROUND_ITEM_NOT_FOUND, current);
    if (intent.itemId >= 0 && groundItem.id != intent.itemId)
      return new Outcome(false, ItemMoveFailure.GROUND_ITEM_CHANGED, current);
    if (!GroundDropOwnership.canPickup(intent.groundEntityId, playerEntityId))
      return new Outcome(false, ItemMoveFailure.GROUND_ITEM_NOT_OWNED, current);
    if ("gld".equalsIgnoreCase(groundItem.code)) {
      int amount = groundItem.attrs == null || groundItem.attrs.base().get(com.riiablo.attributes.Stat.quantity) == null
          ? 0 : groundItem.attrs.base().get(com.riiablo.attributes.Stat.quantity).asInt();
      if (amount <= 0) return new Outcome(false, ItemMoveFailure.GROUND_ITEM_CHANGED, current);
      VendorPricing.grantGold(character, amount);
      long next = current + 1L;
      revisions.put(playerEntityId, next);
      GroundDropOwnership.clear(intent.groundEntityId);
      return new Outcome(true, ItemMoveFailure.NONE, next);
    }
    try {
      character.groundToCursor(groundItem);
    } catch (Throwable t) {
      return new Outcome(false, ItemMoveFailure.MUTATION_FAILED, current);
    }
    long next = current + 1L;
    revisions.put(playerEntityId, next);
    if (intent.operation == ItemMoveOperation.GROUND_TO_CURSOR) GroundDropOwnership.clear(intent.groundEntityId);
    return new Outcome(true, ItemMoveFailure.NONE, next);
  }

  /** Applies a validated drop and invokes the ECS creator with the detached item. */
  public synchronized Outcome drop(int playerEntityId, CharData character,
                                   ItemMoveIntent intent, java.util.function.Consumer<Item> createGround) {
    long current = revision(playerEntityId);
    if (character == null) return new Outcome(false, ItemMoveFailure.PLAYER_NOT_FOUND, current);
    if (intent == null || intent.operation != ItemMoveOperation.CURSOR_TO_GROUND)
      return new Outcome(false, ItemMoveFailure.INVALID_OPERATION, current);
    if (intent.revision != current) return new Outcome(false, ItemMoveFailure.STALE_INVENTORY, current);
    byte failure = ItemMoveValidator.validate(character, intent);
    if (failure != ItemMoveFailure.NONE) return new Outcome(false, failure, current);
    Item item = character.getItems().getCursor();
    try {
      character.cursorToGround();
      if (createGround != null) createGround.accept(item);
    } catch (Throwable t) {
      return new Outcome(false, ItemMoveFailure.MUTATION_FAILED, current);
    }
    long next = current + 1L;
    revisions.put(playerEntityId, next);
    return new Outcome(true, ItemMoveFailure.NONE, next);
  }

  private static int indexOf(CharData character, int idOrIndex) {
    for (int i = 0; i < character.getItems().getItems().size; i++) {
      Item item = character.getItems().getItems().get(i);
      if (item != null && item.id == idOrIndex) return i;
    }
    return idOrIndex;
  }
}
