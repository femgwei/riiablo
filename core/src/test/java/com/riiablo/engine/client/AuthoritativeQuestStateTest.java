package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AuthoritativeQuestStateTest {
  @Test void hashOrderDoesNotOrderRecordsAndInvalidPayloadDoesNotAdvanceTick() {
    AuthoritativeQuestState state = new AuthoritativeQuestState();
    assertTrue(state.commit(10, 400, 0, Long.MAX_VALUE, 40, i -> 1));
    assertTrue(state.commit(11, 440, 0, Long.MIN_VALUE, 40, i -> 2));
    AuthoritativeQuestState.Snapshot snapshot = state.snapshot();
    assertFalse(state.commit(12, 480, 0, 0, 39, i -> 0));
    assertFalse(state.commit(12, 480, 0, 0, 40, i -> -1));
    assertSame(snapshot, state.snapshot());
    assertTrue(state.commit(12, 480, 0, 0, 40, i -> 3));
  }
  @Test void olderQuestResultsCannotRollBackCommittedRecords() {
    AuthoritativeQuestState state = new AuthoritativeQuestState();
    assertTrue(state.commit(4, 40, 0, 4, 40, i -> i + 10));
    assertEquals(4, state.snapshot().revision);
    assertArrayEquals(new int[] {10, 11, 12}, java.util.Arrays.copyOf(state.snapshot().records(), 3));
    assertFalse(state.commit(3, 30, 0, 3, 40, i -> 0));
    assertArrayEquals(new int[] {10, 11, 12}, java.util.Arrays.copyOf(state.snapshot().records(), 3));
    int[] copy = state.snapshot().records();
    copy[0] = 99;
    assertEquals(10, state.snapshot().record(0));
  }

  @Test void baselineResetStartsASeparateQuestIncarnation() {
    AuthoritativeQuestState state = new AuthoritativeQuestState();
    state.commit(20, 800, 0, 20, 40, i -> i);
    state.clear();
    assertNull(state.snapshot());
    assertTrue(state.commit(3, 120, 0, 3, 40, i -> 7));
    assertEquals(7, state.snapshot().record(0));
  }
}
