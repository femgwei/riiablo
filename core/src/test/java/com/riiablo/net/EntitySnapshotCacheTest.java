package com.riiablo.net;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EntitySnapshotCacheTest {
  @Test
  void onlyReportsNewOrChangedSnapshots() {
    EntitySnapshotCache cache = new EntitySnapshotCache();
    assertTrue(cache.update(10, new byte[] {1, 2}));
    assertFalse(cache.update(10, new byte[] {1, 2}));
    assertTrue(cache.update(10, new byte[] {1, 3}));
    assertTrue(cache.update(11, new byte[] {1, 3}));
  }

  @Test
  void removedEntityCanBeSentAgain() {
    EntitySnapshotCache cache = new EntitySnapshotCache();
    assertTrue(cache.update(20, new byte[] {4}));
    cache.remove(20);
    assertTrue(cache.update(20, new byte[] {4}));
  }
}
