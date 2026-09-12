package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act5BaalPortalStateTest {
  @Test
  void eachNativePortalTransitionIsIdempotent() {
    Act5BaalPortalState state = new Act5BaalPortalState();
    assertFalse(state.isWorldstoneChamberOpen());
    assertTrue(state.openWorldstoneChamber());
    assertFalse(state.openWorldstoneChamber());
    assertTrue(state.isWorldstoneChamberOpen());

    assertTrue(state.createLastPortal());
    assertFalse(state.createLastPortal());
    assertTrue(state.isLastPortalCreated());
  }
}
