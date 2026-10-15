package com.riiablo.engine.server.component.serializer;

import static org.junit.jupiter.api.Assertions.*;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.MercenaryP;
import org.junit.jupiter.api.Test;

class MercenarySerializerTest {
  @Test void preservesOwnerSkillVariantUnsignedExperienceAndDeathFlag() {
    Mercenary source = new Mercenary().set(17, 1, 80, -123, 20);
    source.hirelingId = 11;
    source.experience = 0xF1234567L;
    source.flags = 1;
    MercenarySerializer serializer = new MercenarySerializer();
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    builder.finish(serializer.putData(builder, source));
    MercenaryP snapshot = MercenaryP.getRootAsMercenaryP(builder.dataBuffer());
    assertEquals(ComponentP.MercenaryP, serializer.getDataType());
    assertEquals(17, snapshot.ownerId());
    assertEquals(1, snapshot.mercType());
    assertEquals(11, snapshot.hirelingId());
    assertEquals(80, snapshot.level());
    assertEquals(-123, snapshot.seed());
    assertEquals(20, snapshot.nameId());
    assertEquals(0xF1234567L, snapshot.experience());
    assertEquals(1, snapshot.flags());
  }
}
