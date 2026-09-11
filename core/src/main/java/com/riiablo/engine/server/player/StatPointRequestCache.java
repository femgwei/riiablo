package com.riiablo.engine.server.player;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded replay cache so a retried attribute request cannot spend twice. */
public final class StatPointRequestCache {
  public static final class Entry {
    public final int statType;
    private final byte[] response;

    Entry(int statType, byte[] response) {
      this.statType = statType;
      this.response = response.clone();
    }

    public byte[] response() { return response.clone(); }
  }

  private final int capacity;
  private final LinkedHashMap<Long, Entry> entries;

  public StatPointRequestCache() { this(128); }

  public StatPointRequestCache(final int capacity) {
    if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
    this.capacity = capacity;
    entries = new LinkedHashMap<Long, Entry>(capacity + 1, .75f, true) {
      @Override protected boolean removeEldestEntry(Map.Entry<Long, Entry> eldest) {
        return size() > StatPointRequestCache.this.capacity;
      }
    };
  }

  private static long key(int connectionId, long requestId) {
    return (requestId << 32) ^ (connectionId & 0xFFFF_FFFFL);
  }

  public synchronized Entry lookup(int connectionId, long requestId) {
    return entries.get(key(connectionId, requestId));
  }

  public synchronized void put(int connectionId, long requestId, int statType, byte[] response) {
    entries.put(key(connectionId, requestId), new Entry(statType, response));
  }

  public synchronized void clearConnection(int connectionId) {
    long id = connectionId & 0xFFFF_FFFFL;
    entries.entrySet().removeIf(entry -> (entry.getKey() & 0xFFFF_FFFFL) == id);
  }
}
