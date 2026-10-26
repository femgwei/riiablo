package com.riiablo.server.d2gs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Guards the D2GS client ingress boundary against reintroducing direct movement writes. */
public class D2GSIngressBoundaryTest {
  private static final Path SOURCE = Path.of("..", "..",
      "server/d2gs/src/main/java/com/riiablo/server/d2gs/D2GS.java");

  @Test
  public void entitySyncIsSnapshotOnlyAndMovementUsesFixedTickIngress() throws Exception {
    String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

    assertTrue(source.contains(
        "submitD2gsHandler(packet, \"entity_sync\", entitySyncTarget(packet))"));
    assertTrue(source.contains(
        "reason=absolute_entity_sync_removed_use_movement_intent"));
    assertFalse(source.contains("allowLegacyEntitySync"));
    assertFalse(source.contains("MovementInputSequenceTracker"));
    assertFalse(source.contains("sync.sync(entityId, packet.data)"));
  }
}
