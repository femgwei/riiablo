package com.riiablo.engine.client;

import com.badlogic.gdx.utils.IntMap;
import com.riiablo.net.packet.d2gs.MercenaryP;

/** Immutable, tick-ordered hireling metadata detached from the ECS replica. */
public final class AuthoritativeMercenaryState {
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

  static Snapshot capture(int ownerId, long tick, long serverTimeMillis, MercenaryP data) {
    return new Snapshot(ownerId, tick, serverTimeMillis, data);
  }

  public static final class Snapshot {
    public final int ownerId, mercType, hirelingId, level, seed, nameId, flags;
    public final long tick, serverTimeMillis, experience;

    private Snapshot(int ownerId, long tick, long serverTimeMillis, MercenaryP data) {
      this.ownerId = ownerId;
      this.tick = tick;
      this.serverTimeMillis = serverTimeMillis;
      mercType = data.mercType();
      hirelingId = data.hirelingId();
      level = data.level();
      seed = data.seed();
      nameId = data.nameId();
      experience = data.experience();
      flags = (int) data.flags();
    }
  }
}
