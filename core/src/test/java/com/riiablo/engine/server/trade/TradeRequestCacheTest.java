package com.riiablo.engine.server.trade;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TradeRequestCacheTest {
  @Test public void replaysOnlyTheSameIntent() {
    TradeRequestCache cache = new TradeRequestCache(2);
    TradeRequestCache.Intent intent = TradeRequestCache.intent((byte) 0, 9, 3, 7, 1, 2, 4);
    cache.put(1, 10, intent, new byte[] {1, 2, 3});

    TradeRequestCache.Entry entry = cache.lookup(1, 10);
    assertTrue(entry.matches(intent));
    assertArrayEquals(new byte[] {1, 2, 3}, entry.response());
    assertFalse(entry.matches(TradeRequestCache.intent((byte) 0, 9, 3, 8, 1, 2, 4)));
    assertNull(cache.lookup(2, 10));
  }

  @Test public void evictsOldestAndDoesNotStoreZeroRequestId() {
    TradeRequestCache cache = new TradeRequestCache(2);
    TradeRequestCache.Intent intent = TradeRequestCache.intent((byte) 1, 2, 3, 4, 0, 0, 0);
    cache.put(1, 0, intent, new byte[] {0});
    assertNull(cache.lookup(1, 0));
    cache.put(1, 1, intent, new byte[] {1});
    cache.put(1, 2, intent, new byte[] {2});
    cache.put(1, 3, intent, new byte[] {3});
    assertNull(cache.lookup(1, 1));
    assertArrayEquals(new byte[] {3}, cache.lookup(1, 3).response());
  }
}
