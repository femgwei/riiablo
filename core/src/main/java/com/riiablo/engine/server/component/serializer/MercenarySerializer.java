package com.riiablo.engine.server.component.serializer;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.MercenaryP;

public final class MercenarySerializer implements FlatBuffersSerializer<Mercenary, MercenaryP> {
  public byte getDataType() { return ComponentP.MercenaryP; }
  public int putData(FlatBufferBuilder builder, Mercenary merc) {
    return MercenaryP.createMercenaryP(builder, merc.ownerId, merc.mercType,
        merc.hirelingId(), merc.level, merc.seed, merc.nameId, merc.experience, merc.flags);
  }
  public MercenaryP getTable(EntitySync sync, int index) {
    return (MercenaryP) sync.component(new MercenaryP(), index);
  }
  public Mercenary getData(EntitySync sync, int index, Mercenary component) {
    throw new UnsupportedOperationException();
  }
}
