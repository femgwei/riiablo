package com.riiablo.engine.server.quest;


import com.badlogic.gdx.utils.IntMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import com.riiablo.engine.server.RequestCacheSnapshot;

/** Bounded per-connection idempotency cache for authoritative quest requests. */
public final class QuestRequestCache {
  public static final int DEFAULT_CAPACITY = 128;

  public static final class Intent {
    public final byte operation;
    public final int targetEntityId;
    public final int messageIndex;

    public Intent(byte operation, int targetEntityId, int messageIndex) {
      this.operation = operation;
      this.targetEntityId = targetEntityId;
      this.messageIndex = messageIndex;
    }

    @Override public boolean equals(java.lang.Object value) {
      if (this == value) return true;
      if (!(value instanceof Intent)) return false;
      Intent other = (Intent) value;
      return operation == other.operation && targetEntityId == other.targetEntityId
          && messageIndex == other.messageIndex;
    }

    @Override public int hashCode() {
      int hash = operation;
      hash = 31 * hash + targetEntityId;
      return 31 * hash + messageIndex;
    }
  }

  public static final class Entry {
    private final Intent intent;
    private final byte[] response;

    Entry(Intent intent, byte[] response) {
      this.intent = intent;
      this.response = response.clone();
    }

    public boolean matches(Intent intent) { return this.intent.equals(intent); }
    public byte[] response() { return response.clone(); }
  }

  private final int capacity;
  private final IntMap<LinkedHashMap<Long, Entry>> clients = new IntMap<>();

  public QuestRequestCache() { this(DEFAULT_CAPACITY); }

  public QuestRequestCache(int capacity) {
    if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
    this.capacity = capacity;
  }

  public synchronized Entry lookup(int connectionId, long requestId) {
    LinkedHashMap<Long, Entry> entries = clients.get(connectionId);
    return entries == null ? null : entries.get(requestId);
  }

  public synchronized void put(int connectionId, long requestId, Intent intent, byte[] response) {
    if (intent == null || response == null) throw new NullPointerException();
    LinkedHashMap<Long, Entry> entries = clients.get(connectionId);
    if (entries == null) {
      entries = new LinkedHashMap<Long, Entry>(capacity + 1, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Entry> eldest) {
          return size() > QuestRequestCache.this.capacity;
        }
      };
      clients.put(connectionId, entries);
    }
    entries.put(requestId, new Entry(intent, response));
  }

  public synchronized void clear(int connectionId) { clients.remove(connectionId); }
  public synchronized void clearAll() { clients.clear(); }
  public synchronized int size(int connectionId) {
    LinkedHashMap<Long, Entry> entries = clients.get(connectionId);
    return entries == null ? 0 : entries.size();
  }
  public synchronized List<RequestCacheSnapshot> snapshot() {
    List<RequestCacheSnapshot> result = new ArrayList<>();
    for (IntMap.Entry<LinkedHashMap<Long, Entry>> client : clients) {
      for (Map.Entry<Long, Entry> entry : client.value.entrySet()) {
        Intent i = entry.getValue().intent;
        result.add(new RequestCacheSnapshot(client.key, entry.getKey(), "quest",
            i.operation + "," + i.targetEntityId + "," + i.messageIndex,
            entry.getValue().response()));
      }
    }
    return result;
  }
  public synchronized void restore(RequestCacheSnapshot snapshot) {
    String[] f = snapshot.intent.split(",", -1);
    if (f.length != 3) throw new IllegalArgumentException("quest intent");
    put(snapshot.connectionId, snapshot.requestId,
        new Intent(Byte.parseByte(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2])),
        snapshot.response());
  }
}
