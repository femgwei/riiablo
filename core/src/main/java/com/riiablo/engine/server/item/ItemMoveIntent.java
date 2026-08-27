package com.riiablo.engine.server.item;

/** Immutable, connection-independent description of an item mutation request. */
public final class ItemMoveIntent {
  public final long requestId;
  public final long revision;
  public final byte operation;
  public final int itemId;
  public final int groundEntityId;
  public final int storeLoc;
  public final int x;
  public final int y;
  public final int bodyLoc;
  public final boolean merc;

  public ItemMoveIntent(long requestId, long revision, byte operation,
                        int itemId, int groundEntityId, int storeLoc, int x, int y,
                        int bodyLoc, boolean merc) {
    this.requestId = requestId;
    this.revision = revision;
    this.operation = operation;
    this.itemId = itemId;
    this.groundEntityId = groundEntityId;
    this.storeLoc = storeLoc;
    this.x = x;
    this.y = y;
    this.bodyLoc = bodyLoc;
    this.merc = merc;
  }

  /** Stable equality used by the idempotency cache; requestId is deliberately excluded. */
  public boolean sameOperation(ItemMoveIntent other) {
    if (other == null) return false;
    return revision == other.revision && operation == other.operation
        && itemId == other.itemId && groundEntityId == other.groundEntityId
        && storeLoc == other.storeLoc && x == other.x && y == other.y
        && bodyLoc == other.bodyLoc && merc == other.merc;
  }
}
