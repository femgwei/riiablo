package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.Riiablo;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.save.CharData;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.ItemMoveRequest;
import com.badlogic.gdx.net.Socket;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import com.riiablo.net.packet.d2gs.ItemMoveFailure;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.net.packet.d2gs.ItemMoveResult;
import org.junit.jupiter.api.Test;

class NetworkedClientItemManagerTest extends RiiabloTest {
  @Test
  void identifySendsBothStableIdsWithoutOptimisticallyMutatingInventory() throws Exception {
    CharData previous = Riiablo.charData;
    try {
      CharData character = CharData.obtain().clear().set(
          Riiablo.NORMAL, false, "NetworkIdentify", Riiablo.AMAZON);
      Riiablo.charData = character;
      Item scroll = new ItemGenerator().generate("isc");
      scroll.id = -1001;
      Item target = new ItemGenerator().generateLootItem(
          "cap", 20, Quality.MAGIC, 1002, Riiablo.NORMAL);
      target.id = -1002;
      assertTrue(character.getItems().addToInventory(scroll));
      assertTrue(character.getItems().addToInventory(target));
      NetworkedClientItemManager manager = new NetworkedClientItemManager();
      manager.socket = org.mockito.Mockito.mock(Socket.class);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      org.mockito.Mockito.when(manager.socket.getOutputStream()).thenReturn(output);
      manager.resetInventoryRevision(5);

      assertTrue(manager.identifyItem(scroll, target));

      ByteBuffer bytes = ByteBuffer.wrap(output.toByteArray());
      bytes.position(4); // Skip the D2GS size prefix.
      D2GS packet = D2GS.getRootAsD2GS(bytes);
      assertEquals(D2GSData.ItemMoveRequest, packet.dataType());
      ItemMoveRequest request = (ItemMoveRequest) packet.data(new ItemMoveRequest());
      assertEquals(ItemMoveOperation.IDENTIFY_ITEM, request.operation());
      assertEquals(scroll.id, request.itemId());
      assertEquals(target.id, request.x());
      assertEquals(5, request.revision());
      assertFalse(target.isIdentified());
      assertTrue(character.getItems().contains(scroll));
    } finally {
      Riiablo.charData = previous;
    }
  }

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
