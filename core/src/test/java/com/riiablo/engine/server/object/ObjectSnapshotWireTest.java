package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.serializer.ObjectSerializer;
import com.riiablo.net.packet.d2gs.ObjectP;
import org.junit.jupiter.api.Test;

/** Wire-level coverage for the dynamic-object state appended to ObjectP. */
class ObjectSnapshotWireTest {
  @Test
  void serializesClassIdModeAndInteractionFlags() {
    Object component = new Object();
    component.base = new Objects.Entry();
    component.base.Id = 581;
    component.mode = 2;
    component.stateFlags = 0b111;

    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    int table = new ObjectSerializer().putData(builder, component);
    builder.finish(table);

    ObjectP snapshot = ObjectP.getRootAsObjectP(builder.dataBuffer());
    assertEquals(581, snapshot.objectId());
    assertEquals(2, snapshot.mode());
    assertEquals(0b111, snapshot.stateFlags());
  }

  @Test
  void legacyObjectSnapshotDefaultsAppendedFields() {
    FlatBufferBuilder builder = new FlatBufferBuilder(32);
    ObjectP.startObjectP(builder);
    ObjectP.addObjectId(builder, 580);
    builder.finish(ObjectP.endObjectP(builder));

    ObjectP snapshot = ObjectP.getRootAsObjectP(builder.dataBuffer());
    assertEquals(580, snapshot.objectId());
    assertEquals(0, snapshot.mode());
    assertEquals(0, snapshot.stateFlags());
  }
}
