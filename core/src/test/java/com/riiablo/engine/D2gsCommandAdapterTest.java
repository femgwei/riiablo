package com.riiablo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class D2gsCommandAdapterTest {
  @Test
  void sessionIdentityAndCommandAdmissionShareOneBoundary() {
    AuthoritativeSessionRegistry sessions = new AuthoritativeSessionRegistry();
    D2gsCommandAdapter adapter = new D2gsCommandAdapter(sessions, "d2gs",
        (command, target) -> target == 42);

    assertTrue(adapter.connect(1, "hero", 7, 0).accepted);
    assertEquals(AuthoritativeSessionRegistry.Rejection.STABLE_KEY_ACTIVE,
        adapter.connect(2, "hero", 8, 0).rejection);

    D2gsCommandAdapter.Result accepted = adapter.submit(1, "cast", "skill=36", 1, 100, 42);
    assertTrue(accepted.accepted);
    assertEquals(7, accepted.command.actorId);
    assertEquals(SimulationCommandEnvelope.Authority.NETWORK_INPUT, accepted.command.authority);
    assertEquals(SimulationCommandAdmission.Rejection.DUPLICATE_REQUEST,
        adapter.submit(1, "cast", "skill=36", 1, 100, 42).admissionRejection);
    assertEquals(SimulationCommandAdmission.Rejection.UNAUTHORIZED_TARGET,
        adapter.submit(1, "cast", "skill=36", 1, 101, 99).admissionRejection);

    assertTrue(adapter.disconnect(1, 1).accepted);
    assertEquals(0, adapter.pendingCount());
    assertEquals(D2gsCommandAdapter.Rejection.SESSION_NOT_ACTIVE,
        adapter.submit(1, "cast", "skill=36", 2, 102, 42).rejection);

    assertTrue(adapter.connect(2, "hero", 9, 2).accepted);
    assertEquals(AuthoritativeSessionRegistry.State.ACTIVE, sessions.stableKey("hero").state);
    assertTrue(adapter.submit(2, "cast", "skill=36", 3, 103, 42).accepted);
  }

  @Test
  void disconnectedCommandsCannotDrainIntoTheAuthoritativeWorld() {
    D2gsCommandAdapter adapter = new D2gsCommandAdapter(
        new AuthoritativeSessionRegistry(), "d2gs", (command, target) -> true);
    assertTrue(adapter.connect(1, "hero", 7, 0).accepted);
    assertTrue(adapter.submit(1, "move", "east", 1, 1, -1).accepted);
    assertTrue(adapter.disconnect(1, 1).accepted);

    List<SimulationCommandEnvelope> drained = new ArrayList<>();
    assertEquals(0, adapter.drain(1, drained::add));
    assertTrue(drained.isEmpty());
    assertFalse(adapter.submit(1, "move", "east", 2, 2, -1).accepted);
  }

  @Test
  void checkpointAuthorityCanRebindAndRejectOldGeneration() {
    AuthoritativeSessionRegistry sessions = new AuthoritativeSessionRegistry();
    D2gsCommandAdapter adapter = new D2gsCommandAdapter(sessions, "d2gs",
        (command, target) -> true);
    assertTrue(adapter.connect(3, "hero", 7, 0).accepted);
    long firstGeneration = sessions.stableKey("hero").generation;
    AuthoritativeSessionRegistry.Snapshot snapshot = sessions.snapshot();

    AuthoritativeSessionRegistry restored = new AuthoritativeSessionRegistry();
    restored.restore(snapshot);
    D2gsCommandAdapter resumed = new D2gsCommandAdapter(restored, "d2gs",
        (command, target) -> true);
    assertTrue(resumed.rebind(8, "hero", 42, 10, firstGeneration).accepted);
    assertEquals(AuthoritativeSessionRegistry.State.DISCONNECTED,
        restored.connection(3).state);
    assertEquals(42, restored.activeConnection(8).actorId);
    assertEquals(D2gsCommandAdapter.Rejection.SESSION_NOT_ACTIVE,
        resumed.submit(3, "move", "late", 11, 1, -1).rejection);
    assertEquals(D2gsCommandAdapter.Rejection.STALE_SESSION,
        resumed.submit(8, firstGeneration, "move", "late", 11, 2, -1).rejection);
    assertTrue(resumed.submit(8, restored.activeConnection(8).generation,
        "move", "east", 11, 3, -1).accepted);
  }

  @Test
  void restoringInvalidAuthoritySnapshotIsAtomic() {
    AuthoritativeSessionRegistry sessions = new AuthoritativeSessionRegistry();
    assertTrue(sessions.connect(1, "hero", 7, 0).accepted);
    AuthoritativeSessionRegistry.Snapshot valid = sessions.snapshot();
    assertThrows(NullPointerException.class, () -> sessions.restore(null));
    assertEquals(1, sessions.activeCount());
    assertEquals(valid.canonical(), sessions.snapshot().canonical());
  }
}
