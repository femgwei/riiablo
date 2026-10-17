package com.riiablo.engine.server.component.serializer;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.engine.server.component.Position;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.PositionP;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PositionSerializerTest {
  @Test void floatOnlyPacketCannotRetainNativePrecisionEvenForMatchingProjection() {
    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    builder.finish(PositionP.createPositionP(builder, 10, 20));
    PositionSerializer serializer = new PositionSerializer() {
      @Override public PositionP getTable(EntitySync sync, int j) {
        return PositionP.getRootAsPositionP(builder.dataBuffer(), table);
      }
    };
    Position p = new Position().initializeNativeCell(10, 20);
    assertSame(p, serializer.getData(null, 0, p));
    assertEquals(10f, p.position.x);
    assertEquals(20f, p.position.y);
    assertFalse(p.hasNativePrecision());
  }
}
