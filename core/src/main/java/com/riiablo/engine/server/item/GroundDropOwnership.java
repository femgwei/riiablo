package com.riiablo.engine.server.item;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thread-safe short-lived ownership window for multiplayer ground drops. */
public final class GroundDropOwnership {
  private static final Map<Integer, Entry> DROPS = new LinkedHashMap<>();
  /** Entity ids already claimed by a successful/in-flight pickup. */
  private static final java.util.HashSet<Integer> CLAIMED = new java.util.HashSet<>();
  private GroundDropOwnership() {}

  /** Resets stale state when Artemis recycles an entity id for a newly-created drop. */
  public static synchronized void created(int entityId) {
    DROPS.remove(entityId);
    CLAIMED.remove(entityId);
  }

  public static synchronized void register(int entityId, int ownerId, long durationMillis) {
    register(entityId, ownerId, -1, durationMillis, 0L, false);
  }

  /** Registers a drop with owner -> party -> public pickup windows. */
  public static synchronized void register(int entityId, int ownerId, int partyId,
                                           long ownerDurationMillis,
                                           long partyDurationMillis) {
    register(entityId, ownerId, partyId, ownerDurationMillis, partyDurationMillis, false);
  }

  /**
   * Registers ownership and whether a gold pile came from monster treasure.
   * Player-dropped gold has a player owner in D2MOO and must not be party-shared.
   */
  public static synchronized void register(int entityId, int ownerId, int partyId,
                                           long ownerDurationMillis,
                                           long partyDurationMillis,
                                           boolean partyShareGold) {
    if (entityId < 0 || ownerId < 0) return;
    purge();
    created(entityId);
    long ownerUntil = System.currentTimeMillis() + Math.max(0L, ownerDurationMillis);
    long partyUntil = ownerUntil + Math.max(0L, partyDurationMillis);
    DROPS.put(entityId, new Entry(ownerId, partyId, ownerUntil, partyUntil, partyShareGold));
  }

  public static synchronized boolean isPartyShareGold(int entityId) {
    purge();
    Entry entry = DROPS.get(entityId);
    return entry != null && entry.partyShareGold;
  }

  public static synchronized boolean canPickup(int entityId, int playerId) {
    return canPickup(entityId, playerId, -1);
  }

  public static synchronized boolean canPickup(int entityId, int playerId, int playerPartyId) {
    purge();
    if (CLAIMED.contains(entityId)) return false;
    Entry entry = DROPS.get(entityId);
    if (entry == null || entry.ownerId == playerId) return true;
    long now = System.currentTimeMillis();
    if (now < entry.ownerUntilMillis) return false;
    return now >= entry.partyUntilMillis || entry.partyId < 0 || entry.partyId == playerPartyId;
  }

  /** Atomically authorizes and claims a ground entity for one pickup request. */
  public static synchronized boolean claim(int entityId, int playerId) {
    return claim(entityId, playerId, -1);
  }

  public static synchronized boolean claim(int entityId, int playerId, int playerPartyId) {
    purge();
    if (entityId < 0 || playerId < 0 || CLAIMED.contains(entityId)) return false;
    Entry entry = DROPS.get(entityId);
    if (entry != null && entry.ownerId != playerId && !canPickup(entityId, playerId, playerPartyId)) return false;
    CLAIMED.add(entityId);
    return true;
  }

  /** Releases an in-flight claim when the item mutation fails or only partially applies. */
  public static synchronized void release(int entityId) { CLAIMED.remove(entityId); }

  /** Clears ownership after a successful full pickup; the claim remains consumed. */
  public static synchronized void clear(int entityId) { DROPS.remove(entityId); }

  private static void purge() {
    Iterator<Map.Entry<Integer, Entry>> iterator;
    if (DROPS.size() > 4096) {
      iterator = DROPS.entrySet().iterator();
      while (iterator.hasNext() && DROPS.size() > 4096) {
        iterator.next();
        iterator.remove();
      }
    }
  }

  private static final class Entry {
    final int ownerId;
    final int partyId;
    final long ownerUntilMillis;
    final long partyUntilMillis;
    final boolean partyShareGold;
    Entry(int ownerId, int partyId, long ownerUntilMillis, long partyUntilMillis,
          boolean partyShareGold) {
      this.ownerId = ownerId;
      this.partyId = partyId;
      this.ownerUntilMillis = ownerUntilMillis;
      this.partyUntilMillis = partyUntilMillis;
      this.partyShareGold = partyShareGold;
    }
  }
}
