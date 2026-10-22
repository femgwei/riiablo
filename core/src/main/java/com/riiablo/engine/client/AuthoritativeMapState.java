package com.riiablo.engine.client;

import com.badlogic.gdx.utils.IntMap;

/** Authoritative level context for replicated entities, independent of Map objects. */
public final class AuthoritativeMapState {
  private final IntMap<Snapshot> snapshots = new IntMap<>();

  public Snapshot get(int entityId) { return snapshots.get(entityId); }
  boolean accepts(int entityId, long tick, long serverTimeMillis) {
    Snapshot previous = snapshots.get(entityId);
    return previous == null || tick == 0L
        || tick >= previous.tick && serverTimeMillis >= previous.serverTimeMillis;
  }
  void commit(int entityId, Snapshot snapshot) { snapshots.put(entityId, snapshot); }
  void remove(int entityId) { snapshots.remove(entityId); }
  void clear() { snapshots.clear(); }

  static Snapshot capture(int levelId, long tick, long serverTimeMillis) {
    return new Snapshot(levelId, tick, serverTimeMillis);
  }

  public static final class Snapshot {
    public final int levelId;
    public final long tick, serverTimeMillis;
    private Snapshot(int levelId, long tick, long serverTimeMillis) {
      this.levelId = levelId;
      this.tick = tick;
      this.serverTimeMillis = serverTimeMillis;
    }
  }
}
