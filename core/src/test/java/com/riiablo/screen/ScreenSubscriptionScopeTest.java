package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ScreenSubscriptionScopeTest {
  @Test
  void repeatedActivationAndDeactivationAreIdempotentAndReversible() {
    List<String> calls = new ArrayList<>();
    ScreenSubscriptionScope scope = new ScreenSubscriptionScope();
    scope.add(() -> calls.add("first+"), () -> calls.add("first-"));
    scope.add(() -> calls.add("second+"), () -> calls.add("second-"));
    scope.deactivate();
    scope.activate();
    scope.activate();
    scope.deactivate();
    scope.deactivate();
    scope.activate();
    scope.dispose();
    scope.dispose();
    assertEquals(Arrays.asList("first+", "second+", "second-", "first-",
        "first+", "second+", "second-", "first-"), calls);
    assertThrows(IllegalStateException.class, scope::activate);
  }

  @Test
  void directDisposalDetachesActiveSubscriptionsAndUnusedDisposalDoesNotDetach() {
    AtomicInteger live = new AtomicInteger();
    ScreenSubscriptionScope scope = new ScreenSubscriptionScope();
    scope.add(live::incrementAndGet, live::decrementAndGet);
    scope.activate();
    assertEquals(1, live.get());
    scope.dispose();
    assertEquals(0, live.get());
    ScreenSubscriptionScope unused = new ScreenSubscriptionScope();
    unused.add(live::incrementAndGet, live::decrementAndGet);
    unused.dispose();
    assertEquals(0, live.get());
  }

  @Test
  void failedInitialCallbackRollsBackIncludingItsOwnRegistrationAndCanRetry() {
    AtomicInteger live = new AtomicInteger();
    AtomicInteger attempts = new AtomicInteger();
    ScreenSubscriptionScope scope = new ScreenSubscriptionScope();
    scope.add(live::incrementAndGet, live::decrementAndGet);
    IllegalStateException expected = new IllegalStateException("initial callback");
    scope.add(() -> {
      live.incrementAndGet();
      if (attempts.getAndIncrement() == 0) throw expected;
    }, live::decrementAndGet);
    assertSame(expected, assertThrows(IllegalStateException.class, scope::activate));
    assertEquals(0, live.get());
    scope.activate();
    assertEquals(2, live.get());
    scope.dispose();
    assertEquals(0, live.get());
  }

  @Test
  void cleanupFailuresDoNotSkipOtherSubscriptionsAndRemainVisible() {
    List<String> calls = new ArrayList<>();
    ScreenSubscriptionScope scope = new ScreenSubscriptionScope();
    IllegalArgumentException first = new IllegalArgumentException("first cleanup");
    IllegalStateException second = new IllegalStateException("second cleanup");
    scope.add(() -> {}, () -> { calls.add("first"); throw first; });
    scope.add(() -> {}, () -> { calls.add("second"); throw second; });
    scope.activate();
    assertSame(second, assertThrows(IllegalStateException.class, scope::dispose));
    assertArrayEquals(new Throwable[] {first}, second.getSuppressed());
    assertEquals(Arrays.asList("second", "first"), calls);
    assertDoesNotThrow(scope::dispose);
    assertThrows(IllegalStateException.class, scope::activate);
  }

  @Test
  void rollbackRetainsOriginalFailureAndSuppressedCleanupFailure() {
    ScreenSubscriptionScope scope = new ScreenSubscriptionScope();
    Error original = new AssertionError("attach");
    Error cleanup = new AssertionError("detach");
    scope.add(() -> { throw original; }, () -> { throw cleanup; });
    assertSame(original, assertThrows(AssertionError.class, scope::activate));
    assertArrayEquals(new Throwable[] {cleanup}, original.getSuppressed());
    assertDoesNotThrow(scope::dispose);
  }

  @Test
  void invalidConfigurationDoesNotPartiallyRegisterOrAllowChangesWhileActive() {
    ScreenSubscriptionScope scope = new ScreenSubscriptionScope();
    assertThrows(NullPointerException.class, () -> scope.add(() -> {}, null));
    assertThrows(NullPointerException.class, () -> scope.add(null, () -> {}));
    scope.activate();
    assertThrows(IllegalStateException.class, () -> scope.add(() -> {}, () -> {}));
    assertDoesNotThrow(scope::dispose);
    assertThrows(IllegalStateException.class, () -> scope.add(() -> {}, () -> {}));
  }
}
