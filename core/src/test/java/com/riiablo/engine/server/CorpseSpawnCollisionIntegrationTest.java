package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.DeathEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CorpseSpawnCollisionIntegrationTest extends RiiabloTest {
  @Test void resurrectedWraithUsesSizePresenceInsteadOfAllocationOverride() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int id = f.factory.createMonster(Riiablo.files.monstats.get("wraith1").hcIdx, 180, 180);
      f.world.process();
      assertFalse(f.collisions.hasPresence(id));
      prepareCorpse(f, id);
      assertTrue(f.factory.resurrectMonster(id, f.source));
      assertTrue(f.collisions.hasPresence(id));
      assertTrue(f.world.getMapper(Monster.class).get(id).nativeRestoredCollisionPattern);
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x1000));
      f.world.process(); f.collisions.refreshNow();
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x1000));
      assertTrue(f.factory.reinitializeMonster(id, Riiablo.files.monstats.get("wraith1").hcIdx,
          com.riiablo.engine.Engine.Monster.MODE_NU));
      assertFalse(f.collisions.hasPresence(id));
      assertFalse(f.world.getMapper(Monster.class).get(id).nativeRestoredCollisionPattern);
    }
  }

  @Test void resurrectionDiscardsHiddenFrogPatternAndRestoresRuntimeSize() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int id = f.factory.createMonster(Riiablo.files.monstats.get("frogdemon1").hcIdx, 180, 180);
      f.world.process();
      f.world.getMapper(Size.class).get(id).size = 3;
      assertTrue(f.factory.enterFrogHidden(id));
      prepareCorpse(f, id);
      assertTrue(f.factory.resurrectMonster(id, f.source));
      Monster monster = f.world.getMapper(Monster.class).get(id);
      assertFalse(monster.frogNoPresence);
      assertFalse(monster.frogSmallPresence);
      assertTrue(f.collisions.hasPresence(id));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x100));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 180, 1, 0x1000));
    }
  }

  private static void prepareCorpse(DiabPrisonIntegrationTest.Fixture f, int id) {
    f.attrs(id).get(com.riiablo.attributes.Stat.hitpoints).set(0);
    f.events.dispatch(DeathEvent.obtain(f.source, id));
    f.world.process();
    f.world.getMapper(Corpse.class).create(id).reset(Corpse.DEFAULT_DURATION, true);
  }

  @Test void resurrectionRestoresLiveFootprintBeforeSubscriptionInsertion() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int id = f.factory.createMonster(Riiablo.files.monstats.get("fallen1").hcIdx, 180, 180);
      f.world.process();
      f.attrs(id).get(com.riiablo.attributes.Stat.hitpoints).set(0);
      f.events.dispatch(DeathEvent.obtain(f.source, id));
      f.world.process();
      f.world.getMapper(Corpse.class).create(id).reset(Corpse.DEFAULT_DURATION, true);
      assertFalse(f.world.getMapper(Velocity.class).has(id));
      assertTrue(f.factory.resurrectMonster(id, f.source));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x100));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x1000));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x8000));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(id, f.zone, 180, 180, 1, 0x100));
      assertFalse(f.factory.resurrectMonster(id, f.source));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x100));
    }
  }

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
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x100));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x1000));
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
