package com.riiablo.attributes;

import java.util.Arrays;

/** Immutable committed equipment view consumed by combat and progression. */
public final class EquipmentSnapshot {
  private final long revision;
  private final ItemStatProjection stats;
  private final short[] setIds;
  private final byte[] setCounts;

  private EquipmentSnapshot(long revision, ItemStatProjection stats, short[] setIds, byte[] setCounts) {
    this.revision = revision;
    this.stats = stats;
    this.setIds = Arrays.copyOf(setIds, setIds.length);
    this.setCounts = Arrays.copyOf(setCounts, setCounts.length);
  }

  public static EquipmentSnapshot commit(long revision, ItemStatProjection stats,
      short[] setIds, byte[] setCounts) {
    if (revision < 0) throw new IllegalArgumentException("revision");
    if (stats == null || setIds == null || setCounts == null) throw new NullPointerException();
    if (setIds.length != setCounts.length) throw new IllegalArgumentException("set count length");
    for (int i = 1; i < setIds.length; i++) {
      if (setIds[i] <= setIds[i - 1]) throw new IllegalArgumentException("set ids must be sorted");
    }
    return new EquipmentSnapshot(revision, stats, setIds, setCounts);
  }

  public long revision() { return revision; }
  public ItemStatProjection stats() { return stats; }
  public int setCount(short setId) {
    int index = Arrays.binarySearch(setIds, setId);
    return index < 0 ? 0 : setCounts[index] & 0xff;
  }
  public short[] setIds() { return Arrays.copyOf(setIds, setIds.length); }
}
