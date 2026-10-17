package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.DeathEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpawnPatternSizeIntegrationTest extends RiiabloTest {
  @Test void zeroHasNoPresenceAndValidSizesKeepMonsterAndNoPathShapesSeparate() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int id = f.factory.createMonster(Riiablo.files.monstats.get("fallen1").hcIdx, 180, 180);
      f.world.process();
      f.world.getMapper(Size.class).get(id).size = 0;
      f.collisions.refreshNow();
      assertFalse(f.collisions.hasPresence(id));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 3, 0x3100));
      assertTrue(f.collisions.isFreeForPath(f.source, -1, 180, 180, 1));
      f.world.getMapper(Size.class).get(id).size = 2;
      f.collisions.refreshNow();
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 180, 1, 0x100));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 180, 1, 0x1000));
      f.world.getMapper(Size.class).get(id).size = 3;
      f.collisions.refreshNow();
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x100));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x1000));
    }
  }

  @Test void invalidSizesUseSmallMonsterCrossNotLargeBox() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int id = f.factory.createMonster(Riiablo.files.monstats.get("fallen1").hcIdx, 180, 180);
      f.world.process();
      for (int size : new int[] {Integer.MIN_VALUE, -1, 4, Integer.MAX_VALUE}) {
        f.world.getMapper(Size.class).get(id).size = size;
        f.collisions.refreshNow();
        assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 180, 1, 0x100));
        assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x100));
        assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x1000));
        assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 180, 1, 0x1000));
      }
    }
  }

  @Test void zeroSizedWraithAllocationOverrideDoesNotSurviveGenericResurrection() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int id = f.factory.createMonster(Riiablo.files.monstats.get("wraith1").hcIdx, 180, 180);
      f.world.process();
      f.world.getMapper(Size.class).get(id).size = 0;
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 180, 1, 0x100));
      f.attrs(id).get(com.riiablo.attributes.Stat.hitpoints).set(0);
      f.events.dispatch(DeathEvent.obtain(f.source, id));
      f.world.process();
      f.world.getMapper(Corpse.class).create(id).reset(Corpse.DEFAULT_DURATION, true);
      assertTrue(f.factory.resurrectMonster(id, f.source));
      assertFalse(f.collisions.hasPresence(id));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 3, 0x3100));
    }
  }
}
