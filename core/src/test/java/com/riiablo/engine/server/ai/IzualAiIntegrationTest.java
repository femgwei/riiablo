package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;

/** Real 1.10f rows + ECS decisions for A4 Izual. */
class IzualAiIntegrationTest extends RiiabloTest {
  @Test
  void nativeRowsResolveDedicatedAiAndPreserveAllParameterSlots() {
    int rows = 0;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (!"Izual".equals(row.AI)) continue;
      rows++;
      assertTrue(AI.findAI(12, row.AI) instanceof Izual);
      assertNotNull(Riiablo.files.skills.get(row.Skill1));
      try (Act1NativeAiIntegrationTest.Fixture f =
          new Act1NativeAiIntegrationTest.Fixture(row.Id, 0, 12f)) {
        assertArrayEquals(new int[] {
            AI.difficultyValue(row.aip1, 0), AI.difficultyValue(row.aip2, 0),
            AI.difficultyValue(row.aip3, 0), AI.difficultyValue(row.aip4, 0),
            AI.difficultyValue(row.aip5, 0), AI.difficultyValue(row.aip6, 0),
            AI.difficultyValue(row.aip7, 0), AI.difficultyValue(row.aip8, 0)}, f.ai.params);
      }
    }
    assertTrue(rows > 0, "1.10f contains no MonStats row for Izual");
  }

  @Test
  void rangedNovaUsesStrictlyLessThanTenDistance() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(9.99f)) {
      f.ai.params[Izual.PARAM_NOVA_AT_RANGE_CHANCE] = 100;
      f.ai.params[Izual.PARAM_ENGAGE_CHANCE] = 0;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(skillId(f), f.world.getMapper(Casting.class).get(f.actor).skillId);
    }
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(10f)) {
      f.ai.params[Izual.PARAM_NOVA_AT_RANGE_CHANCE] = 100;
      f.ai.params[Izual.PARAM_ENGAGE_CHANCE] = 0;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      assertFalse(f.world.getMapper(Casting.class).has(f.actor));
    }
  }

  @Test
  void novaInstallsPostDoldrumsThenConsumesSwingBeforeAttack() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1f)) {
      f.ai.params[Izual.PARAM_ATTACK_CHANCE] = 0;
      f.ai.params[Izual.PARAM_NOVA_IN_MELEE_CHANCE] = 100;
      f.ai.params[Izual.PARAM_POST_NOVA_DOLDRUMS] = 1;
      f.ai.params[Izual.PARAM_NUM_SWINGS] = 2;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(skillId(f), f.world.getMapper(Casting.class).get(f.actor).skillId);
      f.finishAction();
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      assertEquals(2, ((Izual) f.ai).swings());
      f.ai.update(2 * Animation.FRAME_DURATION);
      assertEquals(SkillCodes.attack, f.world.getMapper(Casting.class).get(f.actor).skillId);
      assertEquals(1, ((Izual) f.ai).swings());
    }
  }

  @Test
  void meleeAttackChanceWinsAndRejectedNovaNeverFallsBackToAttack() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1f)) {
      f.ai.params[Izual.PARAM_ATTACK_CHANCE] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(SkillCodes.attack, f.world.getMapper(Casting.class).get(f.actor).skillId);
    }
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1f)) {
      f.probe.reject = true;
      f.ai.params[Izual.PARAM_ATTACK_CHANCE] = 0;
      f.ai.params[Izual.PARAM_NOVA_IN_MELEE_CHANCE] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      assertFalse(f.world.getMapper(Casting.class).has(f.actor));
      assertEquals(f.ai.params[Izual.PARAM_POST_NOVA_DOLDRUMS], ((Izual) f.ai).doldrums());
      assertEquals(f.ai.params[Izual.PARAM_NUM_SWINGS], ((Izual) f.ai).swings());
    }
  }

  private static Act1NativeAiIntegrationTest.Fixture fixture(float distance) {
    String rowId = rowIdForAi("Izual");
    Act1NativeAiIntegrationTest.Fixture f =
        new Act1NativeAiIntegrationTest.Fixture(rowId, 0, distance);
    f.ai.params[Izual.PARAM_ENGAGE_CHANCE] = 100;
    f.ai.params[Izual.PARAM_NOVA_AT_RANGE_CHANCE] = 0;
    f.ai.params[Izual.PARAM_NOVA_IN_MELEE_CHANCE] = 0;
    f.ai.params[Izual.PARAM_ATTACK_CHANCE] = 100;
    return f;
  }

  private static int skillId(Act1NativeAiIntegrationTest.Fixture f) {
    return Riiablo.files.skills.get(f.ai.monster.monstats.Skill1).Id;
  }

  private static String rowIdForAi(String aiName) {
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (aiName.equalsIgnoreCase(row.AI)) return row.Id;
    }
    fail("1.10f contains no MonStats row for AI " + aiName);
    return null;
  }
}
