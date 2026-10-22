package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.net.packet.d2gs.ItemMoveFailure;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.net.packet.d2gs.ItemMoveResult;
import org.junit.jupiter.api.Test;

class AuthoritativeInventoryStateTest {
  @Test void revisionIsMonotonicAndSnapshotIsDetached() {
    AuthoritativeInventoryState state = new AuthoritativeInventoryState();
    ItemMoveResult first = result(4, 7);
    AuthoritativeInventoryState.Snapshot captured = AuthoritativeInventoryState.capture(first);
    assertTrue(state.accepts(captured.revision, captured.requestId));
    state.commit(captured);
    assertEquals(7, state.revision());
    assertEquals(0, state.snapshot().entries.size());
    AuthoritativeInventoryState.Snapshot stale = AuthoritativeInventoryState.capture(result(3, 6));
    assertFalse(state.accepts(stale.revision, stale.requestId));
    state.baseline(12);
    assertFalse(state.accepts(11, 2));
    AuthoritativeInventoryState.Snapshot same = AuthoritativeInventoryState.capture(result(5, 12));
    assertTrue(state.accepts(same.revision, same.requestId));
  }

  private static ItemMoveResult result(long request, long revision) {
    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    int snapshot = ItemMoveResult.createSnapshotVector(builder, new int[0]);
    int root = ItemMoveResult.createItemMoveResult(builder, request, true,
        ItemMoveFailure.NONE, revision, ItemMoveOperation.GROUND_TO_CURSOR, snapshot,
        -1, 0, 0f, 0f, -1, 0L, -1, 0L, false);
    builder.finish(root);
    return ItemMoveResult.getRootAsItemMoveResult(builder.dataBuffer());
  }
}
