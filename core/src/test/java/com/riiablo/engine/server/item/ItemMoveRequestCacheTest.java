package com.riiablo.engine.server.item;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.riiablo.net.packet.d2gs.ItemMoveOperation;

class ItemMoveRequestCacheTest {
  @Test
  void replaysOnlyTheSameIntentAndEvictsOldEntries() {
    ItemMoveRequestCache cache = new ItemMoveRequestCache(1);
    ItemMoveIntent first = new ItemMoveIntent(7, 0, ItemMoveOperation.STORE_TO_CURSOR,
        100, -1, -1, -1, -1, -1, false);
    ItemMoveIntent different = new ItemMoveIntent(7, 0, ItemMoveOperation.STORE_TO_CURSOR,
        101, -1, -1, -1, -1, -1, false);
    cache.put(3, first, new byte[] {1, 2, 3});
    ItemMoveRequestCache.Entry hit = cache.lookup(3, 7);
    assertNotNull(hit);
    assertTrue(hit.matches(first));
    assertFalse(hit.matches(different));
    assertArrayEquals(new byte[] {1, 2, 3}, hit.response);

    ItemMoveIntent second = new ItemMoveIntent(8, 0, ItemMoveOperation.CURSOR_TO_GROUND,
        -1, -1, -1, -1, -1, -1, false);
    cache.put(3, second, new byte[] {9});
    assertNull(cache.lookup(3, 7));
    assertNotNull(cache.lookup(3, 8));
  }
}
