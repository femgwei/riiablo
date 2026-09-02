package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
