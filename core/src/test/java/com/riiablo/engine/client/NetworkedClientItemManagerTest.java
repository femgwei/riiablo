package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.net.packet.d2gs.ItemMoveFailure;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.net.packet.d2gs.ItemMoveResult;
import org.junit.jupiter.api.Test;

class NetworkedClientItemManagerTest extends RiiabloTest {
  @Test
  void delayedResultCannotRollBackInventoryRevision() {
    NetworkedClientItemManager manager = new NetworkedClientItemManager();
    manager.resetInventoryRevision(4L);

    assertTrue(manager.onAuthoritativeResult(result(2L, 5L, true)));
    assertEquals(5L, manager.inventoryRevision());
    assertFalse(manager.onAuthoritativeResult(result(1L, 4L, true)));
    assertEquals(5L, manager.inventoryRevision());
  }

  @Test
  void sameRevisionCorrectionRemainsApplicable() {
    NetworkedClientItemManager manager = new NetworkedClientItemManager();
    manager.resetInventoryRevision(7L);

    assertTrue(manager.onAuthoritativeResult(result(9L, 7L, false)));
    assertEquals(7L, manager.inventoryRevision());
  }

  private static ItemMoveResult result(long requestId, long revision, boolean success) {
    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    int snapshot = ItemMoveResult.createSnapshotVector(builder, new int[0]);
    int root = ItemMoveResult.createItemMoveResult(builder, requestId, success,
        success ? ItemMoveFailure.NONE : ItemMoveFailure.STALE_INVENTORY,
        revision, ItemMoveOperation.GROUND_TO_CURSOR, snapshot,
        -1, 0, 0f, 0f, -1, 0L, -1, 0L, false);
    builder.finish(root);
    return ItemMoveResult.getRootAsItemMoveResult(builder.dataBuffer());
  }
}
