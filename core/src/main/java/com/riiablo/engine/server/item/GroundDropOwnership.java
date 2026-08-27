package com.riiablo.engine.server.item;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Thread-safe short-lived ownership window for multiplayer ground drops. */
public final class GroundDropOwnership {
  private static final Map<Integer, Entry> DROPS = new HashMap<>();
  private GroundDropOwnership() {}

  public static synchronized void register(int entityId, int ownerId, long durationMillis) {
    if (entityId < 0 || ownerId < 0) return;
    purge();
    DROPS.put(entityId, new Entry(ownerId, System.currentTimeMillis() + Math.max(0L, durationMillis)));
  }

  public static synchronized boolean canPickup(int entityId, int playerId) {
    purge();
    Entry entry = DROPS.get(entityId);
    return entry == null || entry.ownerId == playerId || System.currentTimeMillis() >= entry.untilMillis;
  }

  public static synchronized void clear(int entityId) { DROPS.remove(entityId); }

  private static void purge() {
    long now = System.currentTimeMillis();
    Iterator<Map.Entry<Integer, Entry>> iterator = DROPS.entrySet().iterator();
    while (iterator.hasNext() && DROPS.size() > 4096) {
      if (iterator.next().getValue().untilMillis < now) iterator.remove();
    }
  }

  private static final class Entry {
    final int ownerId; final long untilMillis;
    Entry(int ownerId, long untilMillis) { this.ownerId = ownerId; this.untilMillis = untilMillis; }
  }
}
