package com.riiablo.engine.server.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WellInteractionEventTest {
  @Test
  void requestsAllNativeCleanseAndPetEffects() {
    WellInteractionEvent event = WellInteractionEvent.obtain(1, 2, 84, 64, 0);

    assertTrue(event.cleansePoison);
    assertTrue(event.cleanseFreeze);
    assertTrue(event.cleanseCurses);
    assertTrue(event.healAndCleansePets);
    assertFalse(event.applied());

    event.appliedByConsumer = true;
    assertTrue(event.applied());
  }

  @Test
  void localAttributeRestorationCountsAsApplied() {
    WellInteractionEvent event = WellInteractionEvent.obtain(1, 2, 84, 64,
        WellInteractionEvent.RESTORED_STAMINA);
    assertTrue(event.applied());
  }
}
