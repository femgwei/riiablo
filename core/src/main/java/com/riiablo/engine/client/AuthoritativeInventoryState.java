package com.riiablo.engine.client;

import com.riiablo.net.packet.d2gs.ItemMoveResult;
import com.riiablo.net.packet.d2gs.ItemMoveSnapshotEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Server inventory values, independent of wire buffers, artwork and mutable items. */
public final class AuthoritativeInventoryState {
  private long revision;
  private Snapshot snapshot;
  private final Set<Long> appliedRequests = new HashSet<>();

  public long revision() { return revision; }
  public Snapshot snapshot() { return snapshot; }

  boolean accepts(long revision, long requestId) {
    return revision >= this.revision
        && (revision != this.revision || !appliedRequests.contains(requestId));
  }

  // Only commit after every item and ground correction has been decoded.
  void commit(Snapshot value) {
    if (value.revision != revision) appliedRequests.clear();
    revision = value.revision;
    appliedRequests.add(value.requestId);
    snapshot = value;
  }

  void baseline(long revision) {
    if (this.revision != Math.max(0L, revision)) appliedRequests.clear();
    this.revision = Math.max(0L, revision);
    if (snapshot != null && snapshot.revision != this.revision) snapshot = null;
  }

  void clear() { revision = 0; snapshot = null; appliedRequests.clear(); }

  static Snapshot capture(ItemMoveResult result) { return new Snapshot(result); }

  public static final class Snapshot {
    public final long requestId, revision;
    public final boolean success;
    public final byte operation, failure;
    public final List<Entry> entries;
    public final int groundEntityId, groundOwnerId, groundPartyId;
    public final long groundOwnerUntilMillis, groundPartyUntilMillis;
    public final boolean groundPartyShareGold;
    public final float groundX, groundY;
    private final byte[] groundItemData;

    private Snapshot(ItemMoveResult data) {
      requestId = data.requestId(); revision = data.revision(); success = data.success();
      operation = data.operation(); failure = data.failure();
      List<Entry> copy = new ArrayList<>(data.snapshotLength());
      for (int i = 0; i < data.snapshotLength(); i++) copy.add(new Entry(data.snapshot(i)));
      entries = Collections.unmodifiableList(copy);
      groundEntityId = data.groundEntityId();
      groundItemData = new byte[data.groundItemDataLength()];
      for (int i = 0; i < groundItemData.length; i++) groundItemData[i] = (byte) data.groundItemData(i);
      groundX = data.groundX(); groundY = data.groundY();
      groundOwnerId = data.groundOwnerId(); groundOwnerUntilMillis = data.groundOwnerUntilMillis();
      groundPartyId = data.groundPartyId(); groundPartyUntilMillis = data.groundPartyUntilMillis();
      groundPartyShareGold = data.groundPartyShareGold();
    }

    public byte[] groundItemData() { return groundItemData.clone(); }
    public boolean hasGroundItem() { return groundItemData.length > 0; }
  }

  public static final class Entry {
    public final int itemId, location, storeLoc, bodyLoc, x, y;
    public final boolean merc;
    private final byte[] itemData;

    private Entry(ItemMoveSnapshotEntry data) {
      itemId = data.itemId(); location = data.location(); storeLoc = data.storeLoc();
      bodyLoc = data.bodyLoc(); x = data.x(); y = data.y();
      merc = data.merc();
      itemData = new byte[data.itemDataLength()];
      for (int i = 0; i < itemData.length; i++) itemData[i] = (byte) data.itemData(i);
    }

    public byte[] itemData() { return itemData.clone(); }
  }
}
