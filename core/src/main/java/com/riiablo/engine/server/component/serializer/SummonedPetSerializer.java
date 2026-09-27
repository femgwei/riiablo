package com.riiablo.engine.server.component.serializer;

import com.google.flatbuffers.FlatBufferBuilder;

import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.pet.PetType;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.SummonedPetP;

/** Wire projection of native PlayerPets ownership and PetType flags. */
public class SummonedPetSerializer implements FlatBuffersSerializer<SummonedPet, SummonedPetP> {
  public static final SummonedPetP table = new SummonedPetP();

  @Override
  public byte getDataType() {
    return ComponentP.SummonedPetP;
  }

  @Override
  public int putData(FlatBufferBuilder builder, SummonedPet pet) {
    int type = pet.petType == null ? 0 : builder.createString(pet.petType);
    return SummonedPetP.createSummonedPetP(builder, pet.ownerId, type,
        Math.max(0, pet.skillId), PetType.canBeUnsummoned(pet.petType));
  }

  @Override
  public SummonedPetP getTable(EntitySync sync, int index) {
    sync.component(table, index);
    return table;
  }

  @Override
  public SummonedPet getData(EntitySync sync, int index, SummonedPet pet) {
    throw new UnsupportedOperationException("Not supported!");
  }
}
