package com.riiablo.engine.server.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

public class StateListTest {
  @Test
  public void snapshotReplacesStaleStatesAndPreservesPayload() {
    StateList states = new StateList(42);
    states.addState(StateId.POISON, 90, 2, 7);
    states.addState(StateId.COLD, 20, 1, 8);

    states.replaceFromSnapshot(
        new int[] {StateId.FREEZE},
        new int[] {12},
        new int[] {3});

    assertEquals(1, states.size());
    assertFalse(states.hasState(StateId.POISON));
    assertFalse(states.hasState(StateId.COLD));
    assertEquals(12, states.getStateDuration(StateId.FREEZE));
    assertEquals(3, states.getStateLevel(StateId.FREEZE));
  }
}
