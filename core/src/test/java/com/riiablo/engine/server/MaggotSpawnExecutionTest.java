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
      assertEquals(168f, f.pos(child).x);
      assertEquals(160f, f.pos(child).y);
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
