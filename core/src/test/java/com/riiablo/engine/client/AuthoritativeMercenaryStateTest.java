package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.net.packet.d2gs.MercenaryP;
import org.junit.jupiter.api.Test;

class AuthoritativeMercenaryStateTest {
  @Test void capturesScalarsAndRejectsStaleFrames() {
    AuthoritativeMercenaryState state = new AuthoritativeMercenaryState();
    MercenaryP wire = packet(7, 2, 11, 30, 123, 4, 900L, 5L);
    assertTrue(state.accepts(4, 10, 1000));
    AuthoritativeMercenaryState.Snapshot first =
        AuthoritativeMercenaryState.capture(7, 10, 1000, wire);
    state.commit(4, first);
    assertFalse(state.accepts(4, 9, 1100));
    assertFalse(state.accepts(4, 11, 999));
    assertEquals(123, state.get(4).seed);
    assertEquals(900L, state.get(4).experience);
    assertEquals(5, state.get(4).flags);
  }

  @Test void committedSnapshotDoesNotDependOnReusableFlatBufferView() {
    AuthoritativeMercenaryState state = new AuthoritativeMercenaryState();
    MercenaryP wire = packet(7, 2, 11, 30, 123, 4, 900L, 5L);
    state.commit(4, AuthoritativeMercenaryState.capture(7, 10, 1000, wire));
    java.nio.ByteBuffer buffer = wire.getByteBuffer();
    for (int i = buffer.position(); i < buffer.limit(); i++) buffer.put(i, (byte) 0);
    assertEquals(30, state.get(4).level);
    assertEquals(11, state.get(4).hirelingId);
    assertEquals(7, state.get(4).ownerId);
    state.remove(4);
    assertNull(state.get(4));
  }

  private static MercenaryP packet(int owner, int type, int hireling, int level,
      int seed, int name, long xp, long flags) {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    builder.finish(MercenaryP.createMercenaryP(builder, owner, type, hireling,
        level, seed, name, xp, flags, 0L, 0L));
    return MercenaryP.getRootAsMercenaryP(builder.dataBuffer());
  }
}
