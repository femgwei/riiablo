package com.riiablo.engine.server.component.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.pet.PetType;
import com.riiablo.net.packet.d2gs.SummonedPetP;
import org.junit.jupiter.api.Test;

/** Reconnect wire contract for player-owned Necromancer summons. */
class SummonedPetSerializerTest extends RiiabloTest {
  @Test
  void snapshotPreservesNecromancerOwnerTypeSkillAndUnsummonCapability() {
    SummonedPet source = new SummonedPet().set(17, "golem", 94, 12, false, 0);
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int offset = new SummonedPetSerializer().putData(builder, source);
    builder.finish(offset);

    SummonedPetP snapshot = SummonedPetP.getRootAsSummonedPetP(builder.dataBuffer());
    assertEquals(17, snapshot.ownerId());
    assertEquals("golem", snapshot.petType());
    assertEquals(94, snapshot.skillId());
    assertTrue(snapshot.unsummonable());
    assertTrue(PetType.canBeUnsummoned(snapshot.petType()));
  }
}
