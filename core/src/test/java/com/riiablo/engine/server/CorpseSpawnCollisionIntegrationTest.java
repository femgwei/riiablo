package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.DeathEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorpseSpawnCollisionIntegrationTest extends RiiabloTest {
  @Test void deadColKeepsLiveFootprintInsteadOfAddingCorpseMask() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int id = f.factory.createMonster(Riiablo.files.monstats.get("fallen1").hcIdx, 180, 180);
      f.world.process(); f.collisions.refreshNow();
      com.riiablo.codec.excel.MonStats2.Entry dimensions = new com.riiablo.codec.excel.MonStats2.Entry();
      dimensions.deadCol = true;
      f.world.getMapper(Monster.class).get(id).monstats2 = dimensions;
      f.events.dispatch(DeathEvent.obtain(f.source, id));
      assertFalse(f.world.getMapper(Monster.class).get(id).nativeCorpseCollision);
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x8000));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x100));
      assertTrue(f.world.getMapper(Velocity.class).get(id).stateMovementLocked);
    }
  }

  @Test void deathWritesBoxBeforeDdAndSurvivesVelocityRemovalUntilReinitialization() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int id = f.factory.createMonster(Riiablo.files.monstats.get("fallen1").hcIdx, 180, 180);
      f.world.process(); f.collisions.refreshNow();
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x8000));
      f.events.dispatch(DeathEvent.obtain(f.source, id));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x8000));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 182, 180, 1, 0x8000));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(id, f.zone, 180, 180, 1, 0x8000));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x1100));
      f.world.process(); f.collisions.refreshNow();
      assertFalse(f.world.getMapper(Velocity.class).has(id));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x8000));
      assertTrue(f.factory.reinitializeMonster(id, Riiablo.files.monstats.get("fallen1").hcIdx,
          com.riiablo.engine.Engine.Monster.MODE_NU));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x8000));
    }
  }

  @Test void overlapKeepsOtherCorpseAndDeletionClearsItsMarker() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int first = f.factory.createMonster(Riiablo.files.monstats.get("fallen1").hcIdx, 180, 180);
      int second = f.factory.createMonster(Riiablo.files.monstats.get("fallen1").hcIdx, 180, 180);
      f.collisions.markMonsterDeadCollision(first);
      f.collisions.markMonsterDeadCollision(second);
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(first, f.zone, 181, 181, 1, 0x8000));
      f.world.process(); f.world.delete(second); f.world.process();
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(first, f.zone, 181, 181, 1, 0x8000));
    }
  }
}
