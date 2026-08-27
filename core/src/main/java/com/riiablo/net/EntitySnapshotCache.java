package com.riiablo.net;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Tracks the last serialized state sent for each authoritative entity. */
public final class EntitySnapshotCache {
  private final Map<Integer, byte[]> snapshots = new HashMap<>();

  /** Returns true and stores the bytes when the entity state changed. */
  public boolean update(int entityId, byte[] snapshot) {
    if (snapshot == null) throw new NullPointerException("snapshot");
    byte[] previous = snapshots.get(entityId);
    if (Arrays.equals(previous, snapshot)) return false;
    snapshots.put(entityId, snapshot);
    return true;
  }

  public void remove(int entityId) {
    snapshots.remove(entityId);
  }

  public int size() {
    return snapshots.size();
  }
}
