package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;

/** Real 1.10f rows + ECS decisions for AITHINK_Fn141_BaalMinion. */
class BaalMinionAiIntegrationTest extends RiiabloTest {
  @Test
  void nativeRowsResolveDedicatedAiAndSkillSlotOne() {
    int rows = 0;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (!"BaalMinion".equalsIgnoreCase(row.AI)) continue;
      rows++;
      assertTrue(AI.findAI(12, row.AI) instanceof BaalMinion);
      if (row.Skill1 != null && !row.Skill1.isEmpty()) {
        assertNotNull(Riiablo.files.skills.get(row.Skill1));
      }
    }
    assertTrue(rows > 0, "1.10f contains no MonStats row for BaalMinion");
  }

  @Test
  void meleeAttackRollThenSkillRollMatchesNativePriority() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1f)) {
      f.ai.params[BaalMinion.PARAM_ATTACK_CHANCE] = 100;
      f.ai.params[BaalMinion.PARAM_SKILL_CHANCE_OR_MELEE_STALL] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(skillId(f), f.world.getMapper(Casting.class).get(f.actor).skillId);
    }
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1f)) {
      f.ai.params[BaalMinion.PARAM_ATTACK_CHANCE] = 100;
      f.ai.params[BaalMinion.PARAM_SKILL_CHANCE_OR_MELEE_STALL] = 0;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(SkillCodes.attack, f.world.getMapper(Casting.class).get(f.actor).skillId);
    }
  }

  @Test
  void nonCombatApproachChanceControlsMovementOrStall() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(10f)) {
      f.ai.params[BaalMinion.PARAM_APPROACH_CHANCE] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("APPROACH", f.ai.getState());
    }
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(10f)) {
      f.ai.params[BaalMinion.PARAM_APPROACH_CHANCE] = 0;
      f.ai.params[BaalMinion.PARAM_SKILL_STALL_DURATION] = 3;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
    }
  }

  private static Act1NativeAiIntegrationTest.Fixture fixture(float distance) {
    String rowId = rowIdForAi("BaalMinion");
    Act1NativeAiIntegrationTest.Fixture f =
        new Act1NativeAiIntegrationTest.Fixture(rowId, 0, distance);
    f.ai.params[BaalMinion.PARAM_ATTACK_CHANCE] = 100;
    f.ai.params[BaalMinion.PARAM_APPROACH_CHANCE] = 100;
    f.ai.params[BaalMinion.PARAM_SKILL_CHANCE_OR_MELEE_STALL] = 0;
    f.ai.params[BaalMinion.PARAM_SKILL_STALL_DURATION] = 1;
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
