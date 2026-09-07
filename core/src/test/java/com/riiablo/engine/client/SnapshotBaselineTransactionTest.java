package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SnapshotBaselineTransactionTest {
  @Test
  void rejectsOutOfOrderEndAndRequiresEveryUniqueEntity() {
    SnapshotBaselineTransaction transaction = new SnapshotBaselineTransaction();

    assertEquals(SnapshotBaselineTransaction.EndResult.IGNORED,
        transaction.end(7L, 3L, 2L, true));
    assertTrue(transaction.begin(7L, 3L, 2L));
    assertTrue(transaction.acceptEntity(101));
    assertFalse(transaction.acceptEntity(101), "duplicate frame must not satisfy the count");
    assertEquals(SnapshotBaselineTransaction.EndResult.INCOMPLETE,
        transaction.end(7L, 3L, 2L, true));
    assertFalse(transaction.active());

    assertTrue(transaction.begin(7L, 3L, 2L));
    assertTrue(transaction.acceptEntity(101));
    assertTrue(transaction.acceptEntity(102));
    assertEquals(SnapshotBaselineTransaction.EndResult.COMPLETE,
        transaction.end(7L, 3L, 2L, true));
    assertEquals(3L, transaction.lastCompletedBaselineId());
    assertEquals(SnapshotBaselineTransaction.EndResult.IGNORED,
        transaction.end(7L, 3L, 2L, true));
  }

  @Test
  void ignoresDuplicateBeginAndStaleBaseline() {
    SnapshotBaselineTransaction transaction = new SnapshotBaselineTransaction();

    assertTrue(transaction.begin(11L, 9L, 0L));
    assertFalse(transaction.begin(11L, 9L, 0L));
    assertEquals(SnapshotBaselineTransaction.EndResult.COMPLETE,
        transaction.end(11L, 9L, 0L, true));
    assertFalse(transaction.begin(11L, 9L, 0L));
    assertFalse(transaction.begin(12L, 8L, 0L));
  }

  @Test
  void newerBeginReplacesAnInterruptedTransaction() {
    SnapshotBaselineTransaction transaction = new SnapshotBaselineTransaction();

    assertTrue(transaction.begin(1L, 2L, 1L));
    assertTrue(transaction.acceptEntity(42));
    assertFalse(transaction.begin(3L, 1L, 0L));
    assertFalse(transaction.begin(4L, 2L, 0L));
    assertTrue(transaction.begin(2L, 3L, 0L));
    assertEquals(SnapshotBaselineTransaction.EndResult.COMPLETE,
        transaction.end(2L, 3L, 0L, true));
    assertEquals(3L, transaction.lastCompletedBaselineId());
  }
}
