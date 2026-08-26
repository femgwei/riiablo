package com.riiablo.engine.server.component.serializer;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.engine.server.component.Object;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.ObjectP;

/** Serializes D2Game-created objects which do not have a DS1 wrapper. */
public class ObjectSerializer implements FlatBuffersSerializer<Object, ObjectP> {
  private static final ObjectP table = new ObjectP();

  @Override
  public byte getDataType() {
    return ComponentP.ObjectP;
  }

  @Override
  public int putData(FlatBufferBuilder builder, Object component) {
    return ObjectP.createObjectP(builder,
        component == null || component.base == null ? 0 : component.base.Id);
  }

  @Override
  public ObjectP getTable(EntitySync sync, int index) {
    sync.component(table, index);
    return table;
  }

  @Override
  public Object getData(EntitySync sync, int index, Object component) {
    throw new UnsupportedOperationException("Object creation is handled by ClientNetworkReceiver");
  }
}
