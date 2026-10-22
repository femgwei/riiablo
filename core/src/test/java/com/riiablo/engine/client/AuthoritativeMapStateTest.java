package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AuthoritativeMapStateTest {
  @Test void levelContextIsTickAndTimeOrdered() {
    AuthoritativeMapState state = new AuthoritativeMapState();
    assertTrue(state.accepts(4, 10, 1000));
    state.commit(4, AuthoritativeMapState.capture(8, 10, 1000));
    assertFalse(state.accepts(4, 9, 1100));
    assertFalse(state.accepts(4, 11, 999));
    assertTrue(state.accepts(4, 11, 1001));
    assertEquals(8, state.get(4).levelId);
  }

  @Test void snapshotsAreImmutableAndCanBeCleared() {
    AuthoritativeMapState state = new AuthoritativeMapState();
    AuthoritativeMapState.Snapshot snapshot =
        AuthoritativeMapState.capture(12, 20, 2000);
    state.commit(4, snapshot);
    assertSame(snapshot, state.get(4));
    assertEquals(20, state.get(4).tick);
    state.clear();
    assertNull(state.get(4));
  }
}
