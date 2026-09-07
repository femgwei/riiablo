package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthoritativeSnapshotTimelineTest {
  @Test
  void acceptsSameTickBatchesAndNewerFrames() {
    AuthoritativeSnapshotTimeline timeline = new AuthoritativeSnapshotTimeline();

    assertTrue(timeline.accept(10L, 1_000L));
    assertTrue(timeline.accept(10L, 1_000L));
    assertTrue(timeline.accept(11L, 1_040L));
    assertEquals(11L, timeline.tick());
    assertEquals(1_040L, timeline.serverTimeMillis());
  }

  @Test
  void rejectsRollbackButAllowsLegacyFrames() {
    AuthoritativeSnapshotTimeline timeline = new AuthoritativeSnapshotTimeline();

    assertTrue(timeline.accept(20L, 2_000L));
    assertFalse(timeline.accept(19L, 1_960L));
    assertFalse(timeline.accept(21L, 1_999L));
    assertTrue(timeline.accept(0L, 0L));
    assertEquals(20L, timeline.tick());
    assertEquals(2_000L, timeline.serverTimeMillis());
  }
}
