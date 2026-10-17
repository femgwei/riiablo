package com.riiablo.engine.server.npc;

import java.util.LinkedHashMap;
import java.util.Map;

import com.badlogic.gdx.utils.IntMap;

/** Bounded per-connection cache of completed NPC service responses. */
public final class NpcServiceRequestCache {
  public static final int DEFAULT_CAPACITY = 128;

  public static final class Intent {
    public final int npcEntityId;
    public final byte service;
    public final byte operation;
    public final int itemId;
    public final int itemIndex;
    public final long stockRevision;

    private Intent(int npcEntityId, byte service, byte operation,
                   int itemId, int itemIndex, long stockRevision) {
      this.npcEntityId = npcEntityId;
      this.service = service;
      this.operation = operation;
      this.itemId = itemId;
      this.itemIndex = itemIndex;
      this.stockRevision = stockRevision;
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Intent)) return false;
      Intent other = (Intent) obj;
      return npcEntityId == other.npcEntityId && service == other.service
          && operation == other.operation && itemId == other.itemId
          && itemIndex == other.itemIndex && stockRevision == other.stockRevision;
    }

    @Override public int hashCode() {
      int hash = 17;
      hash = 31 * hash + npcEntityId;
      hash = 31 * hash + service;
      hash = 31 * hash + operation;
      hash = 31 * hash + itemId;
      hash = 31 * hash + itemIndex;
      hash = 31 * hash + (int) (stockRevision ^ (stockRevision >>> 32));
      return hash;
    }
  }

  public static final class Entry {
    private final Intent intent;
    private final byte[] response;

    private Entry(Intent intent, byte[] response) {
      this.intent = intent;
      this.response = response.clone();
    }

    public boolean matches(Intent intent) { return this.intent.equals(intent); }
    public byte[] response() { return response.clone(); }
  }

  private final int capacity;
  private final IntMap<LinkedHashMap<Long, Entry>> clients = new IntMap<>();

  public NpcServiceRequestCache() { this(DEFAULT_CAPACITY); }

  public NpcServiceRequestCache(int capacity) {
    if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
    this.capacity = capacity;
  }

  public synchronized Entry lookup(int clientId, long requestId) {
    LinkedHashMap<Long, Entry> entries = clients.get(clientId);
    return entries == null ? null : entries.get(requestId);
  }

  public synchronized void put(int clientId, long requestId, Intent intent, byte[] response) {
    if (intent == null) throw new NullPointerException("intent");
    if (response == null) throw new NullPointerException("response");
    LinkedHashMap<Long, Entry> entries = clients.get(clientId);
    if (entries == null) {
      entries = new LinkedHashMap<Long, Entry>(capacity + 1, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Entry> eldest) {
          return size() > NpcServiceRequestCache.this.capacity;
        }
      };
      clients.put(clientId, entries);
    }
    entries.put(requestId, new Entry(intent, response));
  }

  public synchronized int size(int clientId) {
    LinkedHashMap<Long, Entry> entries = clients.get(clientId);
    return entries == null ? 0 : entries.size();
  }

  public synchronized void clear(int clientId) { clients.remove(clientId); }
  public synchronized void clearAll() { clients.clear(); }

  public static Intent intent(int npcEntityId, byte service, byte operation,
                              int itemId, int itemIndex, long stockRevision) {
    return new Intent(npcEntityId, service, operation, itemId, itemIndex, stockRevision);
  }
}
