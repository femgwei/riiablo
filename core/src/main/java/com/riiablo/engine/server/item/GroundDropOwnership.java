package com.riiablo.engine.server.item;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Thread-safe short-lived ownership window for multiplayer ground drops. */
public final class GroundDropOwnership {
  private static final Map<Integer, Entry> DROPS = new HashMap<>();
  /** Entity ids already claimed by a successful/in-flight pickup. */
  private static final java.util.HashSet<Integer> CLAIMED = new java.util.HashSet<>();
  private GroundDropOwnership() {}

  /** Resets stale state when Artemis recycles an entity id for a newly-created drop. */
  public static synchronized void created(int entityId) {
    DROPS.remove(entityId);
    CLAIMED.remove(entityId);
  }

  public static synchronized void register(int entityId, int ownerId, long durationMillis) {
    if (entityId < 0 || ownerId < 0) return;
    purge();
    created(entityId);
    DROPS.put(entityId, new Entry(ownerId, System.currentTimeMillis() + Math.max(0L, durationMillis)));
  }

  public static synchronized boolean canPickup(int entityId, int playerId) {
    purge();
    if (CLAIMED.contains(entityId)) return false;
    Entry entry = DROPS.get(entityId);
    return entry == null || entry.ownerId == playerId || System.currentTimeMillis() >= entry.untilMillis;
  }

  /** Atomically authorizes and claims a ground entity for one pickup request. */
  public static synchronized boolean claim(int entityId, int playerId) {
    purge();
    if (entityId < 0 || playerId < 0 || CLAIMED.contains(entityId)) return false;
    Entry entry = DROPS.get(entityId);
    if (entry != null && entry.ownerId != playerId && System.currentTimeMillis() < entry.untilMillis) return false;
    CLAIMED.add(entityId);
    return true;
  }

  /** Releases an in-flight claim when the item mutation fails or only partially applies. */
  public static synchronized void release(int entityId) { CLAIMED.remove(entityId); }

  /** Clears ownership after a successful full pickup; the claim remains consumed. */
  public static synchronized void clear(int entityId) { DROPS.remove(entityId); }

  private static void purge() {
    long now = System.currentTimeMillis();
    Iterator<Map.Entry<Integer, Entry>> iterator = DROPS.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Integer, Entry> entry = iterator.next();
      if (entry.getValue().untilMillis < now) iterator.remove();
    }
    if (DROPS.size() > 4096) {
      iterator = DROPS.entrySet().iterator();
      while (iterator.hasNext() && DROPS.size() > 4096) {
        iterator.next();
        iterator.remove();
      }
    }
  }

  private static final class Entry {
    final int ownerId; final long untilMillis;
    Entry(int ownerId, long untilMillis) { this.ownerId = ownerId; this.untilMillis = untilMillis; }
  }
}
