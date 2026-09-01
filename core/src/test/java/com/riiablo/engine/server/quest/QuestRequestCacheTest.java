package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.net.packet.d2gs.QuestOperation;
import org.junit.jupiter.api.Test;

class QuestRequestCacheTest {
  @Test
  void replaysMatchingIntentAndRejectsRequestIdReuse() {
    QuestRequestCache cache = new QuestRequestCache(2);
    QuestRequestCache.Intent intent = new QuestRequestCache.Intent(
        (byte) QuestOperation.NPC_MESSAGE, 17, 3);
    cache.put(5, 91L, intent, new byte[] {1, 2, 3});

    QuestRequestCache.Entry cached = cache.lookup(5, 91L);
    assertTrue(cached.matches(new QuestRequestCache.Intent(
        (byte) QuestOperation.NPC_MESSAGE, 17, 3)));
    assertFalse(cached.matches(new QuestRequestCache.Intent(
        (byte) QuestOperation.OBJECT_INTERACTION, 17, 3)));
    assertArrayEquals(new byte[] {1, 2, 3}, cached.response());
  }

  @Test
  void boundsEachConnectionAndClearsOnDisconnect() {
    QuestRequestCache cache = new QuestRequestCache(2);
    QuestRequestCache.Intent intent = new QuestRequestCache.Intent(
        (byte) QuestOperation.SNAPSHOT, -1, -1);
    cache.put(7, 1L, intent, new byte[] {1});
    cache.put(7, 2L, intent, new byte[] {2});
    cache.put(7, 3L, intent, new byte[] {3});

    assertNull(cache.lookup(7, 1L));
    assertTrue(cache.lookup(7, 3L).matches(intent));
    cache.clear(7);
    assertNull(cache.lookup(7, 3L));
  }
}
