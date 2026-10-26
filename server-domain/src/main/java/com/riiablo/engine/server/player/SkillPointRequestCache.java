package com.riiablo.engine.server.player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import com.riiablo.engine.server.RequestCacheSnapshot;

/** Bounded replay cache so a retried request cannot spend a second skill point. */
public final class SkillPointRequestCache {
  public static final class Entry {
    public final int skillId;
    private final byte[] response;

    Entry(int skillId, byte[] response) {
      this.skillId = skillId;
      this.response = response.clone();
    }

    public byte[] response() { return response.clone(); }
  }

  private final int capacity;
  private final LinkedHashMap<Long, Entry> entries;

  public SkillPointRequestCache() { this(128); }

  public SkillPointRequestCache(final int capacity) {
    if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
    this.capacity = capacity;
    entries = new LinkedHashMap<Long, Entry>(capacity + 1, .75f, true) {
      @Override protected boolean removeEldestEntry(Map.Entry<Long, Entry> eldest) {
        return size() > SkillPointRequestCache.this.capacity;
      }
    };
  }

  private static long key(int connectionId, long requestId) {
    return (requestId << 32) ^ (connectionId & 0xFFFF_FFFFL);
  }

  public synchronized Entry lookup(int connectionId, long requestId) {
    return entries.get(key(connectionId, requestId));
  }

  public synchronized void put(int connectionId, long requestId, int skillId, byte[] response) {
    entries.put(key(connectionId, requestId), new Entry(skillId, response));
  }

  public synchronized void clearConnection(int connectionId) {
    long id = connectionId & 0xFFFF_FFFFL;
    entries.entrySet().removeIf(entry -> (entry.getKey() & 0xFFFF_FFFFL) == id);
  }
  public synchronized void clearAll() { entries.clear(); }
  public synchronized List<RequestCacheSnapshot> snapshot() {
    List<RequestCacheSnapshot> result = new ArrayList<>();
    for (Map.Entry<Long, Entry> entry : entries.entrySet()) {
      result.add(new RequestCacheSnapshot((int) (entry.getKey() & 0xFFFF_FFFFL),
          entry.getKey() >> 32, "skill", Integer.toString(entry.getValue().skillId),
          entry.getValue().response()));
    }
    return result;
  }
  public synchronized void restore(RequestCacheSnapshot snapshot) {
    put(snapshot.connectionId, snapshot.requestId, Integer.parseInt(snapshot.intent), snapshot.response());
  }
}
