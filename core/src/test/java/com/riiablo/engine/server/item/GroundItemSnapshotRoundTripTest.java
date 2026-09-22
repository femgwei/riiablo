package com.riiablo.engine.server.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.serializer.ItemSerializer;
import com.riiablo.io.ByteInput;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.ItemReader;
import com.riiablo.item.Quality;
import com.riiablo.net.packet.d2gs.ItemP;
import com.riiablo.util.BufferUtils;
import org.junit.jupiter.api.Test;

/** Verifies that a ground-item network snapshot preserves the D2S item payload. */
class GroundItemSnapshotRoundTripTest extends RiiabloTest {
  @Test
  void preservesQualityIdentityAndOwnershipMetadata() {
    Item source = new ItemGenerator().generateLootItem(
        "cap", 35, Quality.MAGIC, 0x13572468, Riiablo.NORMAL);
    source.id = 901;

    com.riiablo.engine.server.component.Item component =
        new com.riiablo.engine.server.component.Item().set(source);
    GroundDropOwnership.applyMetadata(component, 41, 7, 10_000L, 10_000L, false);

    FlatBufferBuilder builder = new FlatBufferBuilder(1024);
    int root = new ItemSerializer().putData(builder, component);
    builder.finish(root);
    ItemP wire = ItemP.getRootAsItemP(builder.dataBuffer());

    assertEquals(41, wire.dropOwnerId());
    assertEquals(7, wire.dropPartyId());
    assertNotNull(wire.dataAsByteBuffer());

    Item restored = new ItemReader().readItem(
        ByteInput.wrap(BufferUtils.readRemaining(wire.dataAsByteBuffer())));
    assertEquals(source.id, restored.id);
    assertEquals(source.code, restored.code);
    assertEquals(source.quality, restored.quality);
    assertEquals(source.qualityId, restored.qualityId);
    assertEquals(source.flags, restored.flags);
    assertEquals(source.attrs.base().get(com.riiablo.attributes.Stat.armorclass).asInt(),
        restored.attrs.base().get(com.riiablo.attributes.Stat.armorclass).asInt());
  }
}
