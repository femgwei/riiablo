package com.riiablo.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DisposalTest {
  @Test
  void runsInOrderWithoutDeduplicatingAndIgnoresNullResources() {
    List<Integer> order = new ArrayList<>();
    try (Disposal cleanup = new Disposal()) {
      cleanup.dispose(null);
      cleanup.run(() -> order.add(1));
      cleanup.dispose(() -> order.add(2));
      cleanup.dispose(() -> order.add(2));
    }
    assertEquals(Arrays.asList(1, 2, 2), order);
  }

  @Test
  void attemptsRemainingActionsAndPropagatesFirstWithSuppressedInOrder() {
    Disposal cleanup = new Disposal();
    RuntimeException first = new RuntimeException("first");
    Error second = new AssertionError("second");
    RuntimeException third = new RuntimeException("third");
    List<Integer> order = new ArrayList<>();
    cleanup.run(() -> { throw first; });
    cleanup.run(() -> { order.add(1); throw second; });
    cleanup.run(() -> { order.add(2); throw third; });
    cleanup.run(() -> order.add(3));
    assertEquals(Arrays.asList(1, 2, 3), order);
    assertSame(first, assertThrows(RuntimeException.class, cleanup::close));
    assertArrayEquals(new Throwable[] {second, third}, first.getSuppressed());
    assertDoesNotThrow(cleanup::close);
  }

  @Test
  void preservesErrorTypeAndDoesNotSuppressAnExceptionOnItself() {
    Disposal cleanup = new Disposal();
    Error failure = new AssertionError("shared");
    cleanup.run(() -> { throw failure; });
    cleanup.run(() -> { throw failure; });
    assertSame(failure, assertThrows(AssertionError.class, cleanup::close));
    assertEquals(0, failure.getSuppressed().length);
  }

  @Test
  void closeIsIdempotentAndRejectsNewActions() {
    Disposal cleanup = new Disposal();
    cleanup.close();
    cleanup.close();
    assertThrows(IllegalStateException.class, () -> cleanup.run(() -> fail("closed")));
  }

  @Test
  void rejectsNullActionsBeforeCleanupStarts() {
    Disposal cleanup = new Disposal();
    assertThrows(NullPointerException.class, () -> cleanup.run(null));
    cleanup.close();
  }
}
