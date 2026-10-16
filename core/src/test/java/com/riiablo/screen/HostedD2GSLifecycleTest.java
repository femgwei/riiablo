package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HostedD2GSLifecycleTest {
  @BeforeEach void requireNoExistingHost() throws Exception {
    assertNull(NetworkedGameScreenLifecycleTest.hostProcess());
  }

  @AfterEach void clearFixture() throws Exception {
    NetworkedGameScreenLifecycleTest.installHost(null);
    Thread.interrupted();
  }

  @Test void noProcessStopAndCapturedEmptyStopAreHarmless() {
    Runnable stop = HostedD2GS.stopAction();
    HostedD2GS.stop();
    stop.run();
    stop.run();
  }

  @Test void deadProcessIsForgottenWithoutTermination() throws Exception {
    Process process = install(false);
    HostedD2GS.stop();
    assertNull(NetworkedGameScreenLifecycleTest.hostProcess());
    verify(process, never()).destroy();
    verify(process, never()).destroyForcibly();
    verify(process, never()).waitFor(anyLong(), any());
  }

  @Test void normalTerminationWaitsForExitAndDoesNotForceOrRetry() throws Exception {
    Process process = install(true);
    AtomicBoolean alive = new AtomicBoolean(true);
    when(process.isAlive()).thenAnswer(call -> alive.get());
    when(process.waitFor(1000, TimeUnit.MILLISECONDS)).thenAnswer(call -> { alive.set(false); return true; });
    Runnable stop = HostedD2GS.stopAction();
    stop.run();
    stop.run();
    HostedD2GS.stop();
    org.mockito.InOrder order = inOrder(process);
    order.verify(process).destroy();
    order.verify(process).waitFor(1000, TimeUnit.MILLISECONDS);
    verify(process, never()).destroyForcibly();
    assertNull(NetworkedGameScreenLifecycleTest.hostProcess());
  }

  @Test void gracefulTimeoutForcesThenConfirmsExit() throws Exception {
    Process process = install(true);
    AtomicBoolean alive = new AtomicBoolean(true);
    when(process.isAlive()).thenAnswer(call -> alive.get());
    when(process.waitFor(1000, TimeUnit.MILLISECONDS)).thenAnswer(call -> false)
        .thenAnswer(call -> { alive.set(false); return true; });
    HostedD2GS.stop();
    org.mockito.InOrder order = inOrder(process);
    order.verify(process).destroy();
    order.verify(process).waitFor(1000, TimeUnit.MILLISECONDS);
    order.verify(process).destroyForcibly();
    order.verify(process).waitFor(1000, TimeUnit.MILLISECONDS);
    assertNull(NetworkedGameScreenLifecycleTest.hostProcess());
  }

  @Test void liveProcessAfterBothTimeoutsIsReportedAndRetainedForLaterStop() throws Exception {
    Process process = install(true);
    assertThrows(IllegalStateException.class, HostedD2GS::stop);
    assertSame(process, NetworkedGameScreenLifecycleTest.hostProcess());
    verify(process).destroy();
    verify(process).destroyForcibly();
    verify(process, times(2)).waitFor(1000, TimeUnit.MILLISECONDS);
    HostedD2GS.start(); // a failed stop cannot allow a duplicate host launch
    assertSame(process, NetworkedGameScreenLifecycleTest.hostProcess());
    when(process.isAlive()).thenReturn(false);
    HostedD2GS.stop();
    assertNull(NetworkedGameScreenLifecycleTest.hostProcess());
  }

  @Test void destroyFailureStillForcesAndPreservesFirstFailure() throws Exception {
    Process process = install(true);
    AtomicBoolean alive = new AtomicBoolean(true);
    when(process.isAlive()).thenAnswer(call -> alive.get());
    RuntimeException failure = new RuntimeException("destroy");
    doThrow(failure).when(process).destroy();
    when(process.destroyForcibly()).thenAnswer(call -> { alive.set(false); return process; });
    assertSame(failure, assertThrows(RuntimeException.class, HostedD2GS::stop));
    verify(process).destroyForcibly();
    assertNull(NetworkedGameScreenLifecycleTest.hostProcess());
  }

  @Test void forceFailureRetainsHandleAndSuppressedExitFailure() throws Exception {
    Process process = install(true);
    Error failure = new AssertionError("force");
    when(process.destroyForcibly()).thenThrow(failure);
    assertSame(failure, assertThrows(AssertionError.class, HostedD2GS::stop));
    assertEquals(1, failure.getSuppressed().length);
    assertTrue(failure.getSuppressed()[0] instanceof IllegalStateException);
    assertSame(process, NetworkedGameScreenLifecycleTest.hostProcess());
  }

  @Test void interruptedWaitStillForcesConfirmsAndRestoresInterrupt() throws Exception {
    Process process = install(true);
    AtomicBoolean alive = new AtomicBoolean(true);
    when(process.isAlive()).thenAnswer(call -> alive.get());
    when(process.waitFor(1000, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException("cancelled"))
        .thenAnswer(call -> { alive.set(false); return true; });
    HostedD2GS.stop();
    assertTrue(Thread.currentThread().isInterrupted());
    verify(process).destroyForcibly();
    assertNull(NetworkedGameScreenLifecycleTest.hostProcess());
  }

  @Test void staleOrEmptyCapturedStopCannotKillReplacementHost() throws Exception {
    Runnable empty = HostedD2GS.stopAction();
    Process old = install(false);
    Runnable stale = HostedD2GS.stopAction();
    HostedD2GS.stop();
    Process replacement = install(true);
    stale.run();
    empty.run();
    assertSame(replacement, NetworkedGameScreenLifecycleTest.hostProcess());
    verify(old, never()).destroy();
    verifyNoInteractions(replacement);
  }

  private static Process install(boolean alive) throws Exception {
    Process process = mock(Process.class);
    when(process.isAlive()).thenReturn(alive);
    NetworkedGameScreenLifecycleTest.installHost(process);
    return process;
  }
}
