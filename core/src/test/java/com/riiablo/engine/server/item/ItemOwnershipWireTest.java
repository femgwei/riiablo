package com.riiablo.engine.server.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.net.packet.d2gs.ItemP;

/** FlatBuffers compatibility check for the appended ItemP ownership fields. */
class ItemOwnershipWireTest {
  @Test
  void roundTripsPickupWindowMetadata() {
    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    int data = ItemP.createDataVector(builder, new byte[] {1, 2, 3});
    int root = ItemP.createItemP(builder, data, 41, 1234L, 7, 5678L, true);
    builder.finish(root);
    ByteBuffer encoded = builder.dataBuffer();
    ItemP decoded = ItemP.getRootAsItemP(encoded);

    assertEquals(3, decoded.dataLength());
    assertEquals(41, decoded.dropOwnerId());
    assertEquals(1234L, decoded.dropOwnerUntilMillis());
    assertEquals(7, decoded.dropPartyId());
    assertEquals(5678L, decoded.dropPartyUntilMillis());
    assertTrue(decoded.partyShareGold());
  }
}
