package com.riiablo.net.packet.d2gs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.flatbuffers.FlatBufferBuilder;
import org.junit.jupiter.api.Test;

class EntitySyncClockTest {
  @Test
  void carriesAuthoritativeClock() {
    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    int types = EntitySync.createComponentTypeVector(builder, new byte[0]);
    int components = EntitySync.createComponentVector(builder, new int[0]);
    builder.finish(EntitySync.createEntitySync(
        builder, 7, 2, 0, types, components, 123L, 4_920L,
        81L, 79L, 79L, -1));

    EntitySync sync = EntitySync.getRootAsEntitySync(builder.dataBuffer());
    assertEquals(123L, sync.tick());
    assertEquals(4_920L, sync.serverTimeMillis());
    assertEquals(81L, sync.inputSequence());
    assertEquals(79L, sync.acknowledgedInputSequence());
    assertEquals(79L, sync.rejectedInputSequence());
  }

  @Test
  void legacyFrameDefaultsClockToZero() {
    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    EntitySync.startEntitySync(builder);
    EntitySync.addEntityId(builder, 7);
    builder.finish(EntitySync.endEntitySync(builder));

    EntitySync sync = EntitySync.getRootAsEntitySync(builder.dataBuffer());
    assertEquals(0L, sync.tick());
    assertEquals(0L, sync.serverTimeMillis());
    assertEquals(0L, sync.inputSequence());
    assertEquals(0L, sync.acknowledgedInputSequence());
    assertEquals(0L, sync.rejectedInputSequence());
  }
}
