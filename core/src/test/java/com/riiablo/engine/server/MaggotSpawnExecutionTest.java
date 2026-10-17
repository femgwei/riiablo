package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.DT1;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaggotSpawnExecutionTest extends RiiabloTest {
  @Test void wraithBirthMarkersAreCrossShapedWithoutWalkingPresence() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int ghost = f.factory.createMonster(Riiablo.files.monstats.get("wraith1").hcIdx, 180, 180);
      // Exact child reservations must not turn no-presence units into obstacles.
      f.collisions.reserveMonsterSpawn(ghost, 180, 180, 3);
      assertFalse(f.collisions.hasPresence(ghost));
      assertTrue(f.collisions.isFreeForPath(f.source, -1, 180, 180, 1));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x100));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 180, 1, 0x100));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x100));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x3000));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(ghost, f.zone, 180, 180, 1, 0x100));
      f.world.process(); f.collisions.refreshNow();
      f.pos(ghost).set(185, 185);
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x100));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 185, 185, 1, 0x100));
      f.world.getMapper(UnitLifecycle.class).create(ghost).transition(UnitLifecycle.Phase.DEATH);
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 185, 185, 1, 0x100));
      f.world.getMapper(UnitLifecycle.class).get(ghost).revive();
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 185, 185, 1, 0x100));
      f.world.delete(ghost); f.world.process();
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 185, 185, 1, 0x100));
    }
  }

  @Test void overlappingWraithMarkersKeepOtherOwnerWhenSourceIsExcluded() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int first = f.factory.createMonster(Riiablo.files.monstats.get("wraith1").hcIdx, 180, 180);
      int second = f.factory.createMonster(Riiablo.files.monstats.get("wraith1").hcIdx, 180, 180);
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(first, f.zone, 180, 180, 1, 0x100));
      f.world.process();
      f.world.delete(second); f.world.process();
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(first, f.zone, 180, 180, 1, 0x100));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x100));
    }
  }

  @Test void missileSpawnMarkersFollowNativeCollisionSizeAndLiveLifecycle() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int id = f.world.create();
      Missile missile = f.world.getMapper(Missile.class).create(id);
      missile.missile = new com.riiablo.codec.excel.Missiles.Entry();
      missile.missile.Collision = true;
      missile.missile.Size = 2;
      Position position = f.world.getMapper(Position.class).create(id);
      position.position.set(180, 180);
      f.collisions.reserveMissileSpawn(id);
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x40));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 180, 1, 0x40));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x40));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(id, f.zone, 180, 180, 1, 0x40));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x1000));
      missile.missile.Size = 3;
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 181, 181, 1, 0x40));
      missile.missile.Collision = false;
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x40));
      missile.missile.Collision = true;
      missile.authoritative = false;
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x40));
      missile.authoritative = true;
      missile.presentationOnly = true;
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x40));
      missile.presentationOnly = false;
      f.world.process();
      position.position.set(185, 185);
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x40));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 185, 185, 1, 0x40));
      f.world.delete(id); f.world.process();
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 185, 185, 1, 0x40));
    }
  }

  @Test void realFactoryUsesNativeWaterFloorRecordsAndCommitsBirthModeImmediately() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      MonStats.Entry child = null;
      for (MonStats.Entry candidate : Riiablo.files.monstats) {
        com.riiablo.codec.excel.MonStats2.Entry dimensions = Riiablo.files.monstats2.get(candidate.MonStatsEx);
        if (candidate.enabled && dimensions != null
            && NativeMonsterSpawnSearch.usesSpecialFloor(candidate.hcIdx, dimensions.spawnCol)) {
          child = candidate; break;
        }
      }
      assertNotNull(child, "1.10F must provide a special-floor monster row");
      com.d2moo.common.drlg.D2ActiveRoom active = NativeMonsterSpawnSearchTest.waterRoom(3);
      active.setNTileXPos(20); active.setNTileYPos(20);
      active.getPRoomTiles().getPFloorTiles()[1].setNPosX(10);
      active.getPRoomTiles().getPFloorTiles()[1].setNPosY(10);
      active.setSeed(new com.d2moo.common.drlg.D2Seed(0, 666));
      com.d2moo.common.drlg.D2DrlgRoom raw = new com.d2moo.common.drlg.D2DrlgRoom();
      raw.setRoom(active);
      f.zone.addRoomEx(100, 100, 100, 100).setNativePopulationMetadata(raw);
      f.collisions.refreshNow();
      int id = f.factory.createMonsterChild(f.source, child.hcIdx, Engine.Monster.MODE_S1,
          1, 1, 0, false, NativeUnitFlags.NO_EXPERIENCE);
      assertTrue(id >= 0, child.Id);
      assertEquals(153f, f.pos(id).x); assertEquals(153f, f.pos(id).y);
      assertEquals(Engine.Monster.MODE_S1, f.world.getMapper(CofReference.class).get(id).mode);
      assertEquals(666, active.getSeed().getNLowSeed());
      assertTrue(f.world.getMapper(NativeUnitFlags.class).get(id).has(NativeUnitFlags.NO_EXPERIENCE));
    }
  }

  @Test void spawnMasksDistinguishTerrainObjectsItemsAndUnitMarkers() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      f.zone.or(170, 170, DT1.Tile.FLAG_BLOCK_WALK);
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 170, 170, 1, 0x3c01));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 170, 170, 1, 0));
      f.zone.adjustObjectCollision(175, 175, 1, 1, 1);
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 175, 175, 1, 0x400));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 175, 175, 1, 0x800));
      f.zone.adjustObjectCollision(178, 178, 1, 1, 1, true);
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 178, 178, 1, 0x800));
      int item = f.world.create();
      f.world.getMapper(Item.class).create(item);
      f.world.getMapper(Position.class).create(item).position.set(180, 180);
      f.world.process(); f.collisions.refreshNow();
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x200));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 180, 180, 1, 0x100));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 128, 128, 1, 0x80));
      assertTrue(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 128, 128, 1, 0x100));
      assertFalse(f.collisions.isFreeForMonsterSpawnMask(-1, f.zone, 128, 128, 1, 0x1000));
    }
  }

  @Test void queenStagesSpawnAndEnforcesSuccessfulSpawnLimit() throws Exception {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      MonStats.Entry row = Riiablo.files.monstats.get("maggotqueen1");
      assertNotNull(row);
      int queen = f.factory.createMonster(row.hcIdx, 160, 160);
      com.riiablo.engine.server.ai.AI ai = f.world.getMapper(AIWrapper.class).get(queen).ai;
      assertTrue(ai instanceof com.riiablo.engine.server.ai.SandMaggotQueen);
      java.lang.reflect.Field params = com.riiablo.engine.server.ai.AI.class.getDeclaredField("params");
      params.setAccessible(true);
      ((int[]) params.get(ai))[0] = 1;
      java.lang.reflect.Method think = ai.getClass().getDeclaredMethod("think", int.class, float.class, boolean.class);
      think.setAccessible(true);
      int childRow = Riiablo.files.monstats.get("sandmaggot1").hcIdx;
      f.collisions.refreshNow();
      think.invoke(ai, f.target, 10f, false);
      f.world.process();
      assertEquals(0, count(f, childRow));
      think.invoke(ai, f.target, 10f, false);
      f.world.process();
      assertEquals(1, count(f, childRow));
      int child = first(f, childRow);
      assertEquals(3f, Math.max(Math.abs(f.pos(child).x - 168), Math.abs(f.pos(child).y - 160)),
          "native queen search starts on radius three, never at the center");
      assertTrue(f.world.getMapper(NativeUnitFlags.class).get(child).has(NativeUnitFlags.NO_EXPERIENCE));
      think.invoke(ai, f.target, 10f, false);
      think.invoke(ai, f.target, 10f, false);
      f.world.process();
      assertEquals(1, count(f, childRow));
    }
  }

  @Test void layKeyframeCreatesConfiguredEggWithNoExperienceAndRejectsBlockedCell() throws Exception {
    for (boolean blocked : new boolean[] {false, true}) {
      try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
        MonStats.Entry row = Riiablo.files.monstats.get("sandmaggot1");
        int mother = f.factory.createMonster(row.hcIdx, 160, 160);
        int target = f.factory.createMonster(Riiablo.files.monstats.get("fallen1").hcIdx, 170, 160);
        MonStats.Entry egg = Riiablo.files.monstats.get(row.spawn);
        assertNotNull(egg);
        int direction = Actioneer.maggotDirectionIndex(10, 0);
        java.lang.reflect.Field xs = Actioneer.class.getDeclaredField("MAGGOT_OFFSET_X");
        java.lang.reflect.Field ys = Actioneer.class.getDeclaredField("MAGGOT_OFFSET_Y");
        xs.setAccessible(true); ys.setAccessible(true);
        int x = 160 + ((int[]) xs.get(null))[direction];
        int y = 160 + ((int[]) ys.get(null))[direction];
        if (blocked) f.zone.or(x, y, DT1.Tile.FLAG_BLOCK_WALK);
        f.collisions.refreshNow();
        invoke(f, mother, 87, target);
        f.world.process();
        assertEquals(blocked ? 0 : 1, count(f, egg.hcIdx));
        if (!blocked) {
          int id = first(f, egg.hcIdx);
          assertEquals(x, f.pos(id).x);
          assertEquals(y, f.pos(id).y);
          assertTrue(f.world.getMapper(NativeUnitFlags.class).get(id).has(NativeUnitFlags.NO_EXPERIENCE));
        }
      }
    }
  }

  @Test void hatchKeyframeCreatesLarvaeOnceAndConsumesEgg() throws Exception {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      MonStats.Entry mother = Riiablo.files.monstats.get("sandmaggot1");
      MonStats.Entry row = Riiablo.files.monstats.get(mother.spawn);
      MonStats.Entry larva = Riiablo.files.monstats.get(row.spawn);
      assertNotNull(larva);
      int egg = f.factory.createMonster(row.hcIdx, 170, 170);
      Skills.Entry skill = Riiablo.files.skills.get(row.Skill1);
      assertNotNull(skill);
      assertEquals(84, skill.srvdofunc);
      f.world.getMapper(Casting.class).create(egg).skillId = skill.Id;
      f.collisions.refreshNow();
      java.lang.reflect.Method start = Actioneer.class.getDeclaredMethod("srvstfunc",
          int.class, int.class, int.class, com.badlogic.gdx.math.Vector2.class);
      start.setAccessible(true);
      start.invoke(f.world.getSystem(Actioneer.class), egg, 43, Engine.INVALID_ENTITY, null);
      assertFalse(f.world.getMapper(NativeUnitFlags.class).get(egg).has(NativeUnitFlags.MONSTER_TARGET));
      f.collisions.refreshNow();
      assertFalse(f.collisions.hasPresence(egg));
      invoke(f, egg, 84, Engine.INVALID_ENTITY);
      f.world.process();
      int produced = count(f, larva.hcIdx);
      assertTrue(produced > 0, "calc=" + skill.calc1 + " level=" + row.Sk1lvl
          + " count=" + com.riiablo.engine.server.skill.SkillFormula.evaluate(skill.calc1, skill, Math.max(1, row.Sk1lvl))
          + " eggHp=" + f.hp(egg) + " child=" + larva.Id);
      assertEquals(0f, f.hp(egg));
      IntBag ids = f.world.getAspectSubscriptionManager().get(Aspect.all(Monster.class)).getEntities();
      for (int i = 0; i < ids.size(); i++) {
        int id = ids.get(i);
        if (f.world.getMapper(Monster.class).get(id).monstats.hcIdx == larva.hcIdx)
          assertTrue(f.world.getMapper(NativeUnitFlags.class).get(id).has(NativeUnitFlags.NO_EXPERIENCE));
      }
      invoke(f, egg, 84, Engine.INVALID_ENTITY);
      assertEquals(produced, count(f, larva.hcIdx));
    }
  }

  @Test void childReservationsPreventSameTickOverlap() {
    try (DiabPrisonIntegrationTest.Fixture f = new DiabPrisonIntegrationTest.Fixture("diablo")) {
      int row = Riiablo.files.monstats.get("fallen1").hcIdx;
      f.collisions.refreshNow();
      int first = f.factory.createMonsterChild(f.source, row, Engine.Monster.MODE_NU,
          170, 170, 0, false, NativeUnitFlags.NO_EXPERIENCE);
      assertTrue(first >= 0);
      assertEquals(Engine.INVALID_ENTITY, f.factory.createMonsterChild(f.source, row,
          Engine.Monster.MODE_NU, 170, 170, 0, false, 0));
    }
  }

  static void invoke(DiabPrisonIntegrationTest.Fixture f, int source, int fn, int target) throws Exception {
    java.lang.reflect.Method method = Actioneer.class.getDeclaredMethod("srvdofunc",
        int.class, int.class, int.class, com.badlogic.gdx.math.Vector2.class);
    method.setAccessible(true);
    method.invoke(f.world.getSystem(Actioneer.class), source, fn, target, null);
  }

  static int count(DiabPrisonIntegrationTest.Fixture f, int row) {
    int count = 0;
    IntBag ids = f.world.getAspectSubscriptionManager().get(Aspect.all(Monster.class)).getEntities();
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      Monster monster = f.world.getMapper(Monster.class).get(id);
      if (monster != null && monster.monstats.hcIdx == row) count++;
    }
    return count;
  }

  static int first(DiabPrisonIntegrationTest.Fixture f, int row) {
    IntBag ids = f.world.getAspectSubscriptionManager().get(Aspect.all(Monster.class)).getEntities();
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      Monster monster = f.world.getMapper(Monster.class).get(id);
      if (monster != null && monster.monstats.hcIdx == row) return id;
    }
    throw new AssertionError("spawn missing");
  }
}
