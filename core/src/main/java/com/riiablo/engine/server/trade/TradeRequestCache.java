package com.riiablo.engine.server.trade;

import java.util.LinkedHashMap;
import java.util.Map;

import com.badlogic.gdx.utils.IntMap;

/** Bounded per-connection cache for idempotent player trade responses. */
public final class TradeRequestCache {
  public static final int DEFAULT_CAPACITY = 128;

  public static final class Intent {
    public final byte operation;
    public final int targetEntityId;
    public final int sessionId;
    public final int itemId;
    public final int x;
    public final int y;
    public final long gold;

    private Intent(byte operation, int targetEntityId, int sessionId, int itemId,
                   int x, int y, long gold) {
      this.operation = operation;
      this.targetEntityId = targetEntityId;
      this.sessionId = sessionId;
      this.itemId = itemId;
      this.x = x;
      this.y = y;
      this.gold = gold;
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Intent)) return false;
      Intent other = (Intent) obj;
      return operation == other.operation && targetEntityId == other.targetEntityId
          && sessionId == other.sessionId && itemId == other.itemId
          && x == other.x && y == other.y && gold == other.gold;
    }

    @Override public int hashCode() {
      int hash = 17;
      hash = 31 * hash + operation;
      hash = 31 * hash + targetEntityId;
      hash = 31 * hash + sessionId;
      hash = 31 * hash + itemId;
      hash = 31 * hash + x;
      hash = 31 * hash + y;
      hash = 31 * hash + (int) (gold ^ (gold >>> 32));
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

    public boolean matches(Intent value) { return intent.equals(value); }
    public byte[] response() { return response.clone(); }
  }

  private final int capacity;
  private final IntMap<LinkedHashMap<Long, Entry>> clients = new IntMap<>();

  public TradeRequestCache() { this(DEFAULT_CAPACITY); }

  public TradeRequestCache(int capacity) {
    if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
    this.capacity = capacity;
  }

  public synchronized Entry lookup(int clientId, long requestId) {
    LinkedHashMap<Long, Entry> entries = clients.get(clientId);
    return entries == null ? null : entries.get(requestId);
  }

  public synchronized void put(int clientId, long requestId, Intent intent, byte[] response) {
    if (requestId == 0) return;
    if (intent == null) throw new NullPointerException("intent");
    if (response == null) throw new NullPointerException("response");
    LinkedHashMap<Long, Entry> entries = clients.get(clientId);
    if (entries == null) {
      entries = new LinkedHashMap<Long, Entry>(capacity + 1, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Entry> eldest) {
          return size() > TradeRequestCache.this.capacity;
        }
      };
      clients.put(clientId, entries);
    }
    entries.put(requestId, new Entry(intent, response));
  }

  public synchronized void clear(int clientId) { clients.remove(clientId); }
  public synchronized void clearAll() { clients.clear(); }

  public static Intent intent(byte operation, int targetEntityId, int sessionId,
                              int itemId, int x, int y, long gold) {
    return new Intent(operation, targetEntityId, sessionId, itemId, x, y, gold);
  }
}
