package com.riiablo.engine.server;

import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerCorpseSpawnCollisionTest extends RiiabloTest {
  @Test void authoritativeDeathCreatesCollisionAtDeathLocationBeforeNextWorldTick() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo",
        new ServerPlayerDeathSystem(), new com.riiablo.item.ItemGenerator())) {
      f.pos(f.target).set(180, 180);
      f.collisions.refreshNow();
      f.events.dispatch(com.riiablo.engine.server.event.DeathEvent.obtain(f.source, f.target));
      assertFalse(f.collisions.hasPresence(f.target));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x8000));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x1180));
      f.pos(f.target).set(190, 190);
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x8000));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 190, 190, 1, 0x8000));
    }
  }

  private static int corpse(DiabPrisonIntegrationTest.Fixture f, int x, int y) {
    int id = f.world.create();
    f.world.getMapper(Position.class).create(id).position.set(x, y);
    // Renderable client corpses retain Class.PLR but are not live Player units.
    f.world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type =
        com.riiablo.engine.server.component.Class.Type.PLR;
    f.world.getMapper(MapWrapper.class).create(id).set(f.map, f.zone);
    f.world.getMapper(PlayerCorpse.class).create(id).playerId = f.target;
    f.world.getMapper(Corpse.class).create(id).reset(PlayerCorpse.CORPSE_DURATION, false);
    f.collisions.reservePlayerCorpse(f.target, id);
    return id;
  }

  @Test void retainedCorpsePublishesBoxImmediatelyAndDoesNotFollowRespawnedOwner() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int id = corpse(f, 180, 180);
      for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
        assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180 + dx, 180 + dy, 1, 0x8000));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 182, 180, 1, 0x8000));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(id, f.zone, 180, 180, 1, 0x8000));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x1180));
      f.pos(f.target).set(190, 190);
      f.world.process();
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x8000));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 190, 190, 1, 0x8000));
      f.world.delete(id); f.world.process();
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x8000));
    }
  }

  @Test void removalAndOverlapKeepOnlySurvivingCorpseMarker() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int first = corpse(f, 180, 180), second = corpse(f, 180, 180);
      f.world.process();
      f.world.getMapper(Corpse.class).remove(first);
      f.world.process();
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x8000));
      f.world.getMapper(Corpse.class).remove(second);
      f.world.process();
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x8000));
    }
  }

  @Test void ownerDeathMarkerCannotBecomeASecondCorpseOrLivePresence() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      f.pos(f.target).set(180, 180);
      f.world.getMapper(PlayerCorpse.class).create(f.target);
      f.collisions.refreshNow();
      assertFalse(f.collisions.hasPresence(f.target));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x1180));
      f.world.getMapper(PlayerCorpse.class).remove(f.target);
      f.collisions.reservePlayerSpawn(f.target);
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x80));
    }
  }
}
