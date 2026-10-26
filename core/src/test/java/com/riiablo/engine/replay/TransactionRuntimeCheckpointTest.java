package com.riiablo.engine.replay;

import com.riiablo.engine.AuthoritativeSessionRegistry;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransactionRuntimeCheckpointTest {
  @Test void bytesRoundTripPreservesPortableRequestMetadata() {
    TransactionRuntimeCheckpoint.Entry entry = TransactionRuntimeCheckpoint.Entry.of(
        7, 3L, 42, 11L, "item_move", 99, 100L, 101L, "from=1;to=2", new byte[] {1, 2, 3});
    TransactionRuntimeCheckpoint image = TransactionRuntimeCheckpoint.capture(12L,
        Collections.singletonList(entry));
    TransactionRuntimeCheckpoint decoded = TransactionRuntimeCheckpoint.fromBytes(image.bytes());
    assertArrayEquals(image.bytes(), decoded.bytes());
    assertEquals(image.canonical(), decoded.canonical());
    assertEquals(3, decoded.entries().get(0).response()[2]);
  }

  @Test void rejectsTamperDuplicateAndOutOfGenerationRequests() {
    TransactionRuntimeCheckpoint.Entry entry = TransactionRuntimeCheckpoint.Entry.of(
        7, 3L, 42, 11L, "quest", -1, 100L, 100L, "op=1", null);
    TransactionRuntimeCheckpoint image = TransactionRuntimeCheckpoint.capture(12L,
        Collections.singletonList(entry));
    byte[] tampered = image.bytes();
    tampered[tampered.length - 1] ^= 1;
    assertThrows(IllegalArgumentException.class, () -> TransactionRuntimeCheckpoint.fromBytes(tampered));
    assertThrows(IllegalArgumentException.class, () -> TransactionRuntimeCheckpoint.capture(12L,
        Arrays.asList(entry, entry)));

    AuthoritativeSessionRegistry sessions = new AuthoritativeSessionRegistry();
    sessions.connect(7, "player", 42, 1L);
    assertThrows(IllegalArgumentException.class, () -> image.validateSessions(sessions.snapshot()));
    AuthoritativeSessionRegistry rebound = new AuthoritativeSessionRegistry();
    rebound.connect(7, "player", 42, 1L);
    rebound.rebind(8, "player", 42, 2L);
    assertThrows(IllegalArgumentException.class, () -> image.validateSessions(rebound.snapshot()));
  }

  @Test void canonicalWitnessIncludesCompletedResponseBytes() {
    TransactionRuntimeCheckpoint.Entry first = TransactionRuntimeCheckpoint.Entry.of(
        1, 1L, 10, 1L, "skill", -1, 2L, 2L, "42", new byte[] {1});
    TransactionRuntimeCheckpoint.Entry second = TransactionRuntimeCheckpoint.Entry.of(
        1, 1L, 10, 2L, "skill", -1, 2L, 2L, "42", new byte[] {2});
    assertNotEquals(TransactionRuntimeCheckpoint.capture(3L,
        Collections.singletonList(first)).canonical(),
        TransactionRuntimeCheckpoint.capture(3L,
            Collections.singletonList(second)).canonical());
  }
}
