package com.riiablo.engine.server.party;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PartyRequestCacheTest {
  @Test
  void cachesMatchingIntentAndRejectsMutationOfResponse() {
    PartyRequestCache cache = new PartyRequestCache(2);
    PartyRequestCache.Intent invite = new PartyRequestCache.Intent((byte) 0, 20);
    byte[] response = {1, 2, 3};
    cache.put(7, 1, invite, response);
    response[0] = 9;
    PartyRequestCache.Entry entry = cache.lookup(7, 1);
    assertTrue(entry.matches(new PartyRequestCache.Intent((byte) 0, 20)));
    assertFalse(entry.matches(new PartyRequestCache.Intent((byte) 1, 20)));
    assertArrayEquals(new byte[] {1, 2, 3}, entry.response());

    cache.put(7, 2, new PartyRequestCache.Intent((byte) 0, 21), new byte[] {2});
    cache.put(7, 3, new PartyRequestCache.Intent((byte) 0, 22), new byte[] {3});
    assertNull(cache.lookup(7, 1));
    cache.clear(7);
    assertNull(cache.lookup(7, 3));
  }
}
