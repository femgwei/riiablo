package com.riiablo.engine.client;

import com.riiablo.net.packet.d2gs.PlayerP;

/** Tick-stamped wallet/ammo projection, separate from mutable character data. */
public final class AuthoritativePlayerItemState {
  private AuthoritativeSnapshotTimeline timeline = new AuthoritativeSnapshotTimeline();
  private Snapshot snapshot;
  public Snapshot snapshot() { return snapshot; }
  boolean commit(long tick, long time, PlayerP data) {
    if (!timeline.accept(tick, time)) return false;
    snapshot = new Snapshot(tick, time, data);
    return true;
  }
  void clear() { snapshot = null; timeline = new AuthoritativeSnapshotTimeline(); }
  public static final class Snapshot {
    public final long tick, serverTimeMillis, gold, goldBank;
    public final boolean walletPresent, ammoPresent;
    public final int ammoItemId, ammoQuantity;
    private Snapshot(long tick, long time, PlayerP data) {
      this.tick = tick; serverTimeMillis = time;
      walletPresent = data.walletPresent(); gold = data.gold(); goldBank = data.goldBank();
      ammoPresent = data.ammoPresent(); ammoItemId = data.ammoItemId(); ammoQuantity = data.ammoQuantity();
    }
  }
}
