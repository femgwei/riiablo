package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.server.component.Corpse;
import org.junit.jupiter.api.Test;

class CorpseManagerTest {
  @Test
  void nativeMonsterCorpseDoesNotExpireOnLegacyTenSecondTimer() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new CorpseManager())
        .build());
    try {
      int entityId = world.create();
      Corpse corpse = world.getMapper(Corpse.class).create(entityId)
          .reset(Corpse.DEFAULT_DURATION, true);

      world.setDelta(60f);
      world.process();
      world.process();

      assertTrue(world.getEntityManager().isActive(entityId));
      assertTrue(world.getMapper(Corpse.class).has(entityId));
      assertTrue(corpse.usable);
      assertFalse(corpse.fading);
    } finally {
      world.dispose();
    }
  }

  @Test
  void explicitlyFiniteCorpseStillFadesAndIsRemoved() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new CorpseManager())
        .build());
    try {
      int entityId = world.create();
      world.getMapper(Corpse.class).create(entityId).reset(0.1f, true);

      world.setDelta(0.2f);
      world.process();
      assertTrue(world.getMapper(Corpse.class).get(entityId).fading);
      assertFalse(world.getMapper(Corpse.class).get(entityId).usable);

      world.setDelta(Corpse.FADE_DURATION + 0.1f);
      world.process();
      assertFalse(world.getEntityManager().isActive(entityId));
    } finally {
      world.dispose();
    }
  }
}
