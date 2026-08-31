package com.riiablo.engine.server.party;

import java.util.LinkedHashMap;
import java.util.Map;
import com.badlogic.gdx.utils.IntMap;

/** Bounded per-connection cache for idempotent party request responses. */
public final class PartyRequestCache {
  private final int capacity;
  private final IntMap<LinkedHashMap<Long, Entry>> clients = new IntMap<>();

  public PartyRequestCache() { this(128); }
  public PartyRequestCache(int capacity) {
    if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
    this.capacity = capacity;
  }

  public static final class Intent {
    public final byte operation;
    public final int targetEntityId;
    public Intent(byte operation, int targetEntityId) {
      this.operation = operation;
      this.targetEntityId = targetEntityId;
    }
    @Override public boolean equals(Object o) {
      if (!(o instanceof Intent)) return false;
      Intent i = (Intent) o;
      return operation == i.operation && targetEntityId == i.targetEntityId;
    }
    @Override public int hashCode() { return 31 * operation + targetEntityId; }
  }

  public static final class Entry {
    private final Intent intent;
    private final byte[] response;
    Entry(Intent intent, byte[] response) { this.intent = intent; this.response = response.clone(); }
    public boolean matches(Intent value) { return intent.equals(value); }
    public byte[] response() { return response.clone(); }
  }

  public synchronized Entry lookup(int clientId, long requestId) {
    LinkedHashMap<Long, Entry> entries = clients.get(clientId);
    return entries == null ? null : entries.get(requestId);
  }
  public synchronized void put(int clientId, long requestId, Intent intent, byte[] response) {
    LinkedHashMap<Long, Entry> entries = clients.get(clientId);
    if (entries == null) {
      entries = new LinkedHashMap<Long, Entry>(capacity + 1, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Entry> eldest) {
          return size() > PartyRequestCache.this.capacity;
        }
      };
      clients.put(clientId, entries);
    }
    entries.put(requestId, new Entry(intent, response));
  }
  public synchronized void clear(int clientId) { clients.remove(clientId); }
}
