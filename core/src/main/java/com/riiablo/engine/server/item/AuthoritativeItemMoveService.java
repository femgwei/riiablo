package com.riiablo.engine.server.item;

import java.util.HashMap;
import java.util.Map;

import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import com.riiablo.net.packet.d2gs.ItemMoveFailure;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.item.VendorPricing;
import com.badlogic.gdx.utils.Array;
import com.riiablo.engine.server.party.PartyGoldShareService;

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
    /** Whether the transport should remove the ground entity after this result. */
    public final boolean consumeGroundEntity;
    /** Remaining quantity for a partially picked stack (zero for normal items). */
    public final int groundQuantityRemaining;
    public Outcome(boolean success, byte failure, long revision) {
      this(success, failure, revision, true, 0);
    }
    public Outcome(boolean success, byte failure, long revision,
                   boolean consumeGroundEntity, int groundQuantityRemaining) {
      this.success = success;
      this.failure = failure;
      this.revision = revision;
      this.consumeGroundEntity = consumeGroundEntity;
      this.groundQuantityRemaining = Math.max(0, groundQuantityRemaining);
    }
  }

  private final Map<Integer, Long> revisions = new HashMap<>();

  public synchronized long revision(int playerEntityId) {
    Long value = revisions.get(playerEntityId);
    return value == null ? 0L : value;
  }

  public synchronized void reset(int playerEntityId) { revisions.remove(playerEntityId); }

  /** Advances the inventory revision for a mutation performed outside an item packet. */
  public synchronized long markExternalMutation(int playerEntityId) {
    long next = revision(playerEntityId) + 1L;
    revisions.put(playerEntityId, next);
    return next;
  }

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
    return pickup(playerEntityId, -1, character, intent, groundItem);
  }

  /** Applies a pickup using the player's authoritative Party id. */
  public synchronized Outcome pickup(int playerEntityId, int playerPartyId,
                                     CharData character, ItemMoveIntent intent,
                                     Item groundItem) {
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
    if (!GroundDropOwnership.claim(intent.groundEntityId, playerEntityId, playerPartyId))
      return new Outcome(false, ItemMoveFailure.GROUND_ITEM_NOT_OWNED, current);
    if ("gld".equalsIgnoreCase(groundItem.code)) {
      int amount = groundItem.attrs == null || groundItem.attrs.base().get(com.riiablo.attributes.Stat.quantity) == null
          ? 0 : groundItem.attrs.base().get(com.riiablo.attributes.Stat.quantity).asInt();
      if (amount <= 0) {
        GroundDropOwnership.release(intent.groundEntityId);
        return new Outcome(false, ItemMoveFailure.GROUND_ITEM_CHANGED, current);
      }
      VendorPricing.GoldGrant grant = VendorPricing.grantCarriedGold(character, amount);
      if (grant.credited <= 0) {
        GroundDropOwnership.release(intent.groundEntityId);
        return new Outcome(false, ItemMoveFailure.GOLD_LIMIT_REACHED, current, false, amount);
      }
      if (grant.remaining > 0) {
        groundItem.attrs.base().put(com.riiablo.attributes.Stat.quantity, grant.remaining);
        groundItem.attrs.aggregate().put(com.riiablo.attributes.Stat.quantity, grant.remaining);
        GroundDropOwnership.release(intent.groundEntityId);
      } else {
        GroundDropOwnership.clear(intent.groundEntityId);
      }
      long next = current + 1L;
      revisions.put(playerEntityId, next);
      return new Outcome(true, ItemMoveFailure.NONE, next, grant.remaining == 0, grant.remaining);
    }
    // Ground clicks in the native game attempt an automatic inventory
    // placement.  The old path only marked the item CURSOR, leaving it
    // visually attached to the mouse and making a normal pickup appear to
    // fail in multiplayer.  Pack it into the first authoritative free
    // inventory rectangle; the explicit cursor operations remain available
    // for items moved from panels.
    boolean stored;
    try {
      stored = character.getItems().addToInventory(groundItem);
    } catch (Throwable t) {
      GroundDropOwnership.release(intent.groundEntityId);
      return new Outcome(false, ItemMoveFailure.MUTATION_FAILED, current);
    }
    if (!stored) {
      GroundDropOwnership.release(intent.groundEntityId);
      return new Outcome(false, ItemMoveFailure.INVENTORY_OCCUPIED, current,
          false, 0);
    }
    long next = current + 1L;
    revisions.put(playerEntityId, next);
    if (intent.operation == ItemMoveOperation.GROUND_TO_CURSOR) GroundDropOwnership.clear(intent.groundEntityId);
    return new Outcome(true, ItemMoveFailure.NONE, next);
  }

  /** Atomically picks up a gold pile and distributes it to same-level party members. */
  public synchronized Outcome pickupSharedGold(int playerEntityId, int playerPartyId,
                                               CharData character, ItemMoveIntent intent,
                                               Item groundItem,
                                               Array<PartyGoldShareService.Recipient> recipients) {
    long current = revision(playerEntityId);
    if (character == null) return new Outcome(false, ItemMoveFailure.PLAYER_NOT_FOUND, current);
    if (intent == null || intent.operation != ItemMoveOperation.GROUND_TO_CURSOR)
      return new Outcome(false, ItemMoveFailure.INVALID_OPERATION, current);
    if (intent.revision != current) return new Outcome(false, ItemMoveFailure.STALE_INVENTORY, current);
    byte failure = ItemMoveValidator.validate(character, intent);
    if (failure != ItemMoveFailure.NONE) return new Outcome(false, failure, current);
    if (groundItem == null || !"gld".equalsIgnoreCase(groundItem.code))
      return pickup(playerEntityId, playerPartyId, character, intent, groundItem);
    if (intent.itemId >= 0 && groundItem.id != intent.itemId)
      return new Outcome(false, ItemMoveFailure.GROUND_ITEM_CHANGED, current);
    int amount = groundItem.attrs == null
        || groundItem.attrs.base().get(com.riiablo.attributes.Stat.quantity) == null
        ? 0 : groundItem.attrs.base().get(com.riiablo.attributes.Stat.quantity).asInt();
    if (amount <= 0) return new Outcome(false, ItemMoveFailure.GROUND_ITEM_CHANGED, current);
    if (!GroundDropOwnership.claim(intent.groundEntityId, playerEntityId, playerPartyId))
      return new Outcome(false, ItemMoveFailure.GROUND_ITEM_NOT_OWNED, current);

    PartyGoldShareService.Result share = PartyGoldShareService.distribute(
        groundItem, playerEntityId, recipients);
    int totalCredited = 0;
    for (PartyGoldShareService.Grant grant : share.grants) {
      totalCredited += Math.max(0, grant.credited);
    }
    if (totalCredited <= 0) {
      GroundDropOwnership.release(intent.groundEntityId);
      return new Outcome(false, ItemMoveFailure.GOLD_LIMIT_REACHED, current, false, amount);
    }
    int remaining = share.remaining;
    groundItem.attrs.base().put(com.riiablo.attributes.Stat.quantity, remaining);
    groundItem.attrs.aggregate().put(com.riiablo.attributes.Stat.quantity, remaining);
    if (remaining == 0) GroundDropOwnership.clear(intent.groundEntityId);
    else GroundDropOwnership.release(intent.groundEntityId);

    for (PartyGoldShareService.Grant grant : share.grants) {
      if (grant.entityId != playerEntityId && grant.credited > 0) {
        revisions.put(grant.entityId, revision(grant.entityId) + 1L);
      }
    }
    long next = current + 1L;
    revisions.put(playerEntityId, next);
    return new Outcome(true, ItemMoveFailure.NONE, next, remaining == 0, remaining);
  }

  /** Applies a validated drop and rolls ownership back unless ECS creation succeeds. */
  public synchronized Outcome drop(int playerEntityId, CharData character,
                                   ItemMoveIntent intent,
                                   java.util.function.Function<Item, Boolean> createGround) {
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
      boolean created = createGround != null && Boolean.TRUE.equals(createGround.apply(item));
      if (!created) {
        character.groundToCursor(item);
        return new Outcome(false, ItemMoveFailure.MUTATION_FAILED, current);
      }
    } catch (Throwable t) {
      if (character.getItems().getCursor() == null
          && !character.getItems().contains(item)) character.groundToCursor(item);
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
    return com.riiablo.save.ItemData.INVALID_ITEM;
  }
}
