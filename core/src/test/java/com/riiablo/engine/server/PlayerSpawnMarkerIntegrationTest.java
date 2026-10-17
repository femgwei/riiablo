package com.riiablo.engine.server;

import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerSpawnMarkerIntegrationTest extends RiiabloTest {
  @Test void reservationPublishesNewPlayerBeforeSubscriptionInsertion() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int id = f.world.create();
      f.world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type =
          com.riiablo.engine.server.component.Class.Type.PLR;
      f.world.getMapper(Position.class).create(id).position.set(180, 180);
      f.world.getMapper(Size.class).create(id).size = 2;
      f.world.getMapper(Velocity.class).create(id);
      f.world.getMapper(MapWrapper.class).create(id).set(f.map, f.zone);
      f.collisions.reservePlayerSpawn(id);
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 180, 1, 0x80));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x1000));
    }
  }

  @Test void smallPlayerMarkerIncludesArmsButNoPathDoesNot() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      f.pos(f.target).set(180, 180);
      f.world.getMapper(Size.class).get(f.target).size = 2;
      f.collisions.reservePlayerSpawn(f.target);
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 180, 1, 0x80));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x80));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 180, 1, 0x1000));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x100));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(f.target, f.zone, 181, 180, 1, 0x80));
      f.pos(f.target).set(185, 185);
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 180, 1, 0x80));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 186, 185, 1, 0x80));
      f.world.delete(f.target); f.world.process();
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 186, 185, 1, 0x80));
    }
  }

  @Test void largeZeroAndInvalidPlayerSizesFollowNativePatternSelection() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      f.pos(f.target).set(180, 180);
      f.world.getMapper(Size.class).get(f.target).size = 3;
      f.collisions.reservePlayerSpawn(f.target);
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x80));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x1000));
      for (int size : new int[] {0, -1, 4, Integer.MAX_VALUE}) {
        f.world.getMapper(Size.class).get(f.target).size = size;
        f.collisions.refreshNow();
        assertEquals(size == 0, f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 180, 1, 0x80));
        assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x80));
      }
    }
  }
}
