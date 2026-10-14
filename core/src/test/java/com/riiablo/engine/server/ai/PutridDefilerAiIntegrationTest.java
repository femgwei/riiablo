package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;

/** Real 1.10f rows + ECS decisions for AITHINK_Fn137_PutridDefiler. */
class PutridDefilerAiIntegrationTest extends RiiabloTest {
  @Test
  void nativeRowsResolveDedicatedAi() {
    int rows = 0;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (!"PutridDefiler".equalsIgnoreCase(row.AI)) continue;
      rows++;
      assertTrue(AI.findAI(12, row.AI) instanceof PutridDefiler);
    }
    assertTrue(rows > 0, "1.10f contains no MonStats row for PutridDefiler");
  }

  @Test
  void meleeAttacksAndCloseNonCombatTargetUsesImpregnateSlot() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1f)) {
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(SkillCodes.attack, f.world.getMapper(Casting.class).get(f.actor).skillId);
    }
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(5f)) {
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("APPROACH", f.ai.getState());
    }
  }

  @Test
  void targetBeyondCallbackRangeStallsInsteadOfAttacking() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(30f)) {
      f.ai.params[PutridDefiler.PARAM_TOO_CLOSE_DISTANCE] = 0;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      assertFalse(f.world.getMapper(Casting.class).has(f.actor));
    }
  }

  private static Act1NativeAiIntegrationTest.Fixture fixture(float distance) {
    String rowId = rowIdForAi("PutridDefiler");
    return new Act1NativeAiIntegrationTest.Fixture(rowId, 0, distance);
  }

  private static String rowIdForAi(String aiName) {
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (aiName.equalsIgnoreCase(row.AI)) return row.Id;
    }
    fail("1.10f contains no MonStats row for AI " + aiName);
    return null;
  }
}
