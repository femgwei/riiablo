package com.riiablo.engine.server.player;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SkillPointRequestCacheTest {
  @Test
  void requestReplayIsScopedToConnectionAndReturnsDefensiveCopy() {
    SkillPointRequestCache cache = new SkillPointRequestCache(4);
    byte[] response = {1, 2, 3};
    cache.put(2, 77, 6, response);
    response[0] = 9;
    SkillPointRequestCache.Entry entry = cache.lookup(2, 77);
    assertEquals(6, entry.skillId);
    assertArrayEquals(new byte[] {1, 2, 3}, entry.response());
    assertNull(cache.lookup(3, 77));
    cache.clearConnection(2);
    assertNull(cache.lookup(2, 77));
  }
}
