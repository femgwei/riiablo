package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClientPredictionBufferTest {
  @Test
  void acknowledgesAndReplaysOnlyPendingInputs() {
    ClientPredictionBuffer buffer = new ClientPredictionBuffer();
    buffer.reset(10f, 20f);
    assertTrue(buffer.recordSent(1L, 10.2f, 20f));
    assertTrue(buffer.recordSent(2L, 10.4f, 20.1f));
    assertTrue(buffer.recordSent(3L, 10.7f, 20.1f));

    ClientPredictionBuffer.Reconciliation result =
        buffer.reconcile(2L, 10.35f, 20.05f, 10.7f, 20.1f, false);

    assertEquals(1, buffer.pendingCount());
    assertEquals(10.65f, result.x, 0.0001f);
    assertEquals(20.05f, result.y, 0.0001f);
    assertFalse(result.hard);
  }

  @Test
  void staleAcknowledgementCannotRollPredictionBack() {
    ClientPredictionBuffer buffer = new ClientPredictionBuffer();
    buffer.reset(0f, 0f);
    buffer.recordSent(1L, 1f, 0f);
    buffer.reconcile(1L, 1f, 0f, 1f, 0f, false);

    ClientPredictionBuffer.Reconciliation stale =
        buffer.reconcile(0L, -10f, -10f, 1f, 0f, false);
    assertTrue(stale.stale);
    assertEquals(1f, stale.x, 0f);
  }

  @Test
  void rejectedOrWarpedInputHardSnapsAndClearsQueue() {
    ClientPredictionBuffer buffer = new ClientPredictionBuffer();
    buffer.reset(0f, 0f);
    buffer.recordSent(1L, 0.2f, 0f);
    buffer.recordSent(2L, 0.4f, 0f);

    ClientPredictionBuffer.Reconciliation result =
        buffer.reconcile(1L, 5f, 6f, 0.4f, 0f, true);
    assertTrue(result.hard);
    assertEquals(5f, result.x, 0f);
    assertEquals(6f, result.y, 0f);
    assertEquals(0, buffer.pendingCount());
  }

  @Test
  void mediumErrorKeepsSimulationAuthoritativeAndSmoothsRender() {
    ClientPredictionBuffer buffer = new ClientPredictionBuffer();
    buffer.reset(0f, 0f);
    buffer.recordSent(1L, 1f, 0f);

    ClientPredictionBuffer.Reconciliation result =
        buffer.reconcile(1L, 0.5f, 0f, 1f, 0f, false);
    assertFalse(result.hard);
    assertTrue(result.smooth);
    assertEquals(0.5f, result.x, 0f);
    assertEquals(0.5f, result.renderOffsetX, 0f);
  }

  @Test
  void overflowAndReconnectHaveExplicitHardBoundaries() {
    ClientPredictionBuffer buffer = new ClientPredictionBuffer(2);
    buffer.reset(0f, 0f);
    buffer.recordSent(1L, 1f, 0f);
    buffer.recordSent(2L, 2f, 0f);
    buffer.recordSent(3L, 3f, 0f);
    assertTrue(buffer.reconcile(1L, 1f, 0f, 3f, 0f, false).hard);

    buffer.reset(9f, 4f);
    assertEquals(0, buffer.pendingCount());
    assertEquals(0L, buffer.lastAcknowledgedSequence());
    assertTrue(buffer.recordSent(1L, 9.25f, 4f));
  }

  @Test
  void legacyRebaseDoesNotRestartSentSequence() {
    ClientPredictionBuffer buffer = new ClientPredictionBuffer();
    buffer.reset(0f, 0f);
    assertTrue(buffer.recordSent(1L, 0.1f, 0f));
    buffer.rebaseLegacy(5f, 6f);
    assertFalse(buffer.recordSent(1L, 5.1f, 6f));
    assertTrue(buffer.recordSent(2L, 5.1f, 6f));
  }
}
