package com.riiablo.attributes;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InventoryTransactionTest {
  @Test void commitAdvancesRevisionAndCopiesState() {
    long[] before = {10, 20};
    long[] after = {20, 30};
    InventoryTransaction tx = InventoryTransaction.prepare(4, 99, before, after);
    InventoryTransaction.Commit result = tx.commit(4, 98);
    assertEquals(InventoryTransaction.CommitStatus.APPLIED, result.status);
    assertEquals(5, result.revision);
    assertArrayEquals(after, result.state());
    after[0] = 700;
    assertArrayEquals(new long[] {20, 30}, result.state());
  }

  @Test void rejectsConflictAndDuplicateWithoutMutation() {
    InventoryTransaction tx = InventoryTransaction.prepare(2, 7, new long[] {1}, new long[] {2});
    assertEquals(InventoryTransaction.CommitStatus.CONFLICT, tx.commit(1, 0).status);
    assertEquals(InventoryTransaction.CommitStatus.DUPLICATE, tx.commit(2, 7).status);
    assertArrayEquals(new long[] {1}, tx.rollback());
  }
}
