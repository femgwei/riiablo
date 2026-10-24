package com.riiablo.attributes;

/** Rejects stale or mixed-revision progression snapshots during reconnect. */
public final class ProgressionSnapshotGate {
  private long revision = -1;
  private long inventoryRevision = -1;
  private long equipmentRevision = -1;

  public boolean accept(ProgressionSnapshot progression, long inventoryRevision) {
    if (progression == null || inventoryRevision < 0) return false;
    if (revision >= 0 && progression.revision < revision) return false;
    if (this.inventoryRevision >= 0 && inventoryRevision < this.inventoryRevision) return false;
    if (this.equipmentRevision >= 0 && progression.equipmentRevision < this.equipmentRevision) return false;
    revision = progression.revision;
    this.inventoryRevision = inventoryRevision;
    this.equipmentRevision = progression.equipmentRevision;
    return true;
  }

  public long revision() { return revision; }
  public long inventoryRevision() { return inventoryRevision; }
  public long equipmentRevision() { return equipmentRevision; }
  public void clear() { revision = inventoryRevision = equipmentRevision = -1; }
}
