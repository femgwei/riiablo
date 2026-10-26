package com.riiablo.engine.server.item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import com.riiablo.engine.server.RequestCacheSnapshot;

/** Bounded per-connection cache preventing duplicate item mutations on retries. */
public final class ItemMoveRequestCache {
  public static final class Entry {
    public final ItemMoveIntent intent;
    public final byte[] response;
    Entry(ItemMoveIntent intent, byte[] response) {
      this.intent = intent;
      this.response = response;
    }
    public boolean matches(ItemMoveIntent candidate) { return intent.sameOperation(candidate); }
  }

  private final int maxEntries;
  private final LinkedHashMap<Long, Entry> entries;

  public ItemMoveRequestCache() { this(256); }

  public ItemMoveRequestCache(final int maxEntries) {
    if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
    this.maxEntries = maxEntries;
    entries = new LinkedHashMap<Long, Entry>(maxEntries + 1, .75f, true) {
      @Override protected boolean removeEldestEntry(Map.Entry<Long, Entry> eldest) {
        return size() > ItemMoveRequestCache.this.maxEntries;
      }
    };
  }

  private static long key(int connectionId, long requestId) {
    return (requestId << 32) ^ (connectionId & 0xFFFFFFFFL);
  }

  public synchronized Entry lookup(int connectionId, long requestId) {
    return entries.get(key(connectionId, requestId));
  }

  public synchronized void put(int connectionId, ItemMoveIntent intent, byte[] response) {
    if (intent == null || response == null) return;
    entries.put(key(connectionId, intent.requestId),
        new Entry(intent, response.clone()));
  }

  public synchronized void clear() { entries.clear(); }
  public synchronized void clearConnection(int connectionId) {
    long mask = connectionId & 0xFFFFFFFFL;
    entries.entrySet().removeIf(entry -> (entry.getKey() & 0xFFFFFFFFL) == mask);
  }
  public synchronized int size() { return entries.size(); }
  public synchronized List<RequestCacheSnapshot> snapshot() {
    List<RequestCacheSnapshot> result = new ArrayList<>();
    for (Map.Entry<Long, Entry> entry : entries.entrySet()) {
      ItemMoveIntent i = entry.getValue().intent;
      result.add(new RequestCacheSnapshot((int) (entry.getKey() & 0xFFFF_FFFFL),
          entry.getKey() >> 32, "item", i.revision + "," + i.operation + "," + i.itemId
              + "," + i.groundEntityId + "," + i.storeLoc + "," + i.x + "," + i.y
              + "," + i.bodyLoc + "," + i.merc + "," + i.pickupToCursor,
          entry.getValue().response));
    }
    return result;
  }
  public synchronized void restore(RequestCacheSnapshot snapshot) {
    String[] f = snapshot.intent.split(",", -1);
    if (f.length != 10) throw new IllegalArgumentException("item intent");
    put(snapshot.connectionId, new ItemMoveIntent(snapshot.requestId, Long.parseLong(f[0]),
        Byte.parseByte(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
        Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]),
        Integer.parseInt(f[7]), Boolean.parseBoolean(f[8]), Boolean.parseBoolean(f[9])),
        snapshot.response());
  }
}
