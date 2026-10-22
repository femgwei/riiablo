package com.riiablo.engine.client;

import com.badlogic.gdx.utils.IntMap;
import com.riiablo.net.packet.d2gs.ItemP;

/** Detached ground item bytes and pickup windows, indexed by the local entity. */
public final class AuthoritativeGroundItemState {
  private final IntMap<Snapshot> snapshots = new IntMap<>();

  public Snapshot get(int entity) { return snapshots.get(entity); }
  boolean accepts(int entity, long tick, long time) {
    Snapshot old = get(entity);
    return old == null || tick == 0 || tick >= old.tick && time >= old.serverTimeMillis;
  }
  void commit(int entity, Snapshot snapshot) { snapshots.put(entity, snapshot); }
  void remove(int entity) { snapshots.remove(entity); }
  void clear() { snapshots.clear(); }

  static Snapshot capture(int serverEntity, long tick, long time, ItemP data) {
    return capture(serverEntity, tick, time, data, null);
  }
  static Snapshot capture(int serverEntity, long tick, long time, ItemP data, Snapshot previous) {
    return new Snapshot(serverEntity, tick, time, data, previous);
  }
  static Snapshot correction(AuthoritativeInventoryState.Snapshot data, Snapshot previous) {
    return new Snapshot(data, previous);
  }

  public static final class Snapshot {
    public final int serverEntityId, ownerId, partyId;
    public final long tick, serverTimeMillis, ownerUntilMillis, partyUntilMillis;
    public final long inventoryRevision;
    public final boolean partyShareGold;
    private final byte[] itemData;

    private Snapshot(int serverEntity, long tick, long time, ItemP data, Snapshot previous) {
      inventoryRevision = -1;
      serverEntityId = serverEntity; this.tick = tick; serverTimeMillis = time;
      ownerId = data.dropOwnerId(); ownerUntilMillis = data.dropOwnerUntilMillis();
      partyId = data.dropPartyId(); partyUntilMillis = data.dropPartyUntilMillis();
      partyShareGold = data.partyShareGold();
      if (data.dataLength() == 0 && previous != null && previous.serverEntityId == serverEntity) {
        itemData = previous.itemData.clone();
      } else {
        itemData = new byte[data.dataLength()];
        for (int i = 0; i < itemData.length; i++) itemData[i] = (byte) data.data(i);
      }
    }
    private Snapshot(AuthoritativeInventoryState.Snapshot data, Snapshot previous) {
      serverEntityId = data.groundEntityId; inventoryRevision = data.revision;
      tick = previous == null ? 0L : previous.tick;
      serverTimeMillis = previous == null ? 0L : previous.serverTimeMillis;
      ownerId = data.groundOwnerId; ownerUntilMillis = data.groundOwnerUntilMillis;
      partyId = data.groundPartyId; partyUntilMillis = data.groundPartyUntilMillis;
      partyShareGold = data.groundPartyShareGold; itemData = data.groundItemData();
    }
    public byte[] itemData() { return itemData.clone(); }
    public boolean hasItemData() { return itemData.length > 0; }
  }
}
