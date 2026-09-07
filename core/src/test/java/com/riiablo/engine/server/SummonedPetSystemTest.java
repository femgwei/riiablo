package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.SummonedPet;
import org.junit.jupiter.api.Test;

class SummonedPetSystemTest {
  @Test
  void timedPetExpiresInNativeFrames() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new SummonedPetSystem()).build());
    try {
      int owner = world.create();
      world.getMapper(Player.class).create(owner);
      int pet = world.create();
      world.getMapper(SummonedPet.class).create(pet)
          .set(owner, "dopplezon", 28, 1, true, 25);
      world.setDelta(0.5f);
      world.process();
      assertTrue(world.getEntityManager().isActive(pet));
      world.process();
      assertFalse(world.getEntityManager().isActive(pet));
    } finally {
      world.dispose();
    }
  }

  @Test
  void petIsRemovedWhenOwnerNoLongerExists() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new SummonedPetSystem()).build());
    try {
      int pet = world.create();
      world.getMapper(SummonedPet.class).create(pet)
          .set(99, "valkyrie", 32, 1, false, 0);
      world.process();
      assertFalse(world.getEntityManager().isActive(pet));
    } finally {
      world.dispose();
    }
  }

  @Test
  void petIsRemovedWhenOwnerDies() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new SummonedPetSystem()).build());
    try {
      int owner = world.create();
      world.getMapper(Player.class).create(owner);
      // PlayerCorpse is the authoritative death marker produced by
      // ServerPlayerDeathSystem; no ItemStatCost/resource bootstrap is
      // required for this lifecycle test.
      world.getMapper(com.riiablo.engine.server.component.PlayerCorpse.class)
          .create(owner).playerId = owner;
      int pet = world.create();
      world.getMapper(SummonedPet.class).create(pet)
          .set(owner, "spiritwolf", 227, 1, false, 0);
      world.process();
      assertFalse(world.getEntityManager().isActive(pet));
    } finally {
      world.dispose();
    }
  }

  @Test
  void nativeDruidPetListsShareWolfAndSpiritFamilies() {
    assertTrue(com.riiablo.engine.server.pet.PetType.sameNativeList("spiritwolf", "fenris"));
    assertTrue(com.riiablo.engine.server.pet.PetType.sameNativeList("fenris", "grizzly"));
    assertTrue(com.riiablo.engine.server.pet.PetType.sameNativeList("oak sage", "heart of wolverine"));
    assertTrue(com.riiablo.engine.server.pet.PetType.sameNativeList("poison creeper", "solar creeper"));
    assertEquals("fenris", com.riiablo.engine.server.pet.PetType.canonical("Dire Wolf"));
    assertFalse(com.riiablo.engine.server.pet.PetType.sameNativeType("spiritwolf", "fenris"));
    assertTrue(com.riiablo.engine.server.pet.PetType.sameNativeType("Oak Sage", "totem"));
    assertTrue(com.riiablo.engine.server.pet.PetType.warpsWithOwner("spiritwolf"));
    assertTrue(com.riiablo.engine.server.pet.PetType.warpsWithOwner("vine"));
    assertFalse(com.riiablo.engine.server.pet.PetType.warpsWithOwner("raven"));
  }
}
