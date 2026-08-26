package com.riiablo.engine.server.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NativeCainQuestEventTest {
  @Test
  void consumerAcknowledgesObjectEffectCreation() {
    NativeCainQuestEvent event = NativeCainQuestEvent.obtain(
        1, 2, 30, NativeCainQuestEvent.INIFUSS_TREE);
    assertFalse(event.accepted);
    event.accept();
    assertTrue(event.accepted);
  }
}
