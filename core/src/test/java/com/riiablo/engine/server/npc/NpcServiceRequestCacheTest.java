package com.riiablo.engine.server.npc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NpcServiceRequestCacheTest {
  @Test void repeatsReplayOriginalResponseWithoutSharingMutableBytes() {
    NpcServiceRequestCache cache = new NpcServiceRequestCache(2);
    NpcServiceRequestCache.Intent intent = NpcServiceRequestCache.intent(42, (byte) 0, (byte) 1, 7, -1, 3);
    byte[] response = {1, 2, 3};
    cache.put(0, 9, intent, response);
    response[0] = 99;

    NpcServiceRequestCache.Entry entry = cache.lookup(0, 9);
    assertNotNull(entry);
    assertTrue(entry.matches(intent));
    assertArrayEquals(new byte[] {1, 2, 3}, entry.response());
    byte[] replay = entry.response();
    replay[1] = 88;
    assertArrayEquals(new byte[] {1, 2, 3}, entry.response());
  }

  @Test void detectsRequestIdReuseWithDifferentIntent() {
    NpcServiceRequestCache cache = new NpcServiceRequestCache();
    cache.put(2, 11, NpcServiceRequestCache.intent(1, (byte) 0, (byte) 1, 4, -1, 2), new byte[] {1});
    assertFalse(cache.lookup(2, 11).matches(
        NpcServiceRequestCache.intent(1, (byte) 0, (byte) 2, 4, -1, 2)));
    assertNull(cache.lookup(3, 11));
  }

  @Test void evictsLeastRecentlyUsedEntryAndClearsOnDisconnect() {
    NpcServiceRequestCache cache = new NpcServiceRequestCache(2);
    NpcServiceRequestCache.Intent intent = NpcServiceRequestCache.intent(1, (byte) 0, (byte) 0, 0, -1, 0);
    cache.put(1, 1, intent, new byte[] {1});
    cache.put(1, 2, intent, new byte[] {2});
    assertNotNull(cache.lookup(1, 1));
    cache.put(1, 3, intent, new byte[] {3});
    assertNotNull(cache.lookup(1, 1));
    assertNull(cache.lookup(1, 2));
    assertEquals(2, cache.size(1));
    cache.clear(1);
    assertEquals(0, cache.size(1));
  }
}
