package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;

class VileDogAiIntegrationTest extends RiiabloTest {
  private String row() {
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if ("VileDog".equals(mon.AI)) return mon.Id;
    }
    throw new AssertionError("Missing real VileDog row");
  }

  @Test void allTenRealRowsResolveDedicatedAi() {
    int rows = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"VileDog".equals(mon.AI)) continue;
      rows++;
      assertEquals("VileDog", AI.findAI(12, mon.AI).getClass().getSimpleName());
    }
    assertEquals(10, rows);
  }

  @Test void firstDecisionWaitsFiveFramesBeforeAttackingInEveryDifficulty() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture(row(), difficulty, 1)) {
        f.ai.params[0] = 100;
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals("IDLE", f.ai.getState());
        assertNull(f.casting());
        f.ai.update(4 * Animation.FRAME_DURATION);
        assertNull(f.casting());
        f.ai.update(Animation.FRAME_DURATION + .00001f);
        assertEquals(SkillCodes.attack, f.casting().skillId);
        assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
        f.sequence().started = true;
        f.ai.update(10);
        assertTrue(f.sequence().started);
      }
    }
  }

  @Test void failedMeleeRollUsesConfiguredStallThenRetries() {
    try (Fixture f = new Fixture(row(), 0, 1)) {
      f.ai.params[0] = 0;
      f.ai.params[1] = 17;
      f.ai.update(Animation.FRAME_DURATION);
      f.ai.update(5 * Animation.FRAME_DURATION + .00001f);
      f.ai.params[0] = 100;
      f.ai.update(16 * Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertEquals(SkillCodes.attack, f.casting().skillId);
    }
  }

  @Test void rangedFailureWaitsTenFramesIndependentlyOfMeleeStall() {
    try (Fixture f = new Fixture(row(), 0, 10)) {
      f.ai.params[1] = 100;
      f.ai.params[2] = 0;
      f.ai.update(Animation.FRAME_DURATION);
      f.ai.update(5 * Animation.FRAME_DURATION + .00001f);
      f.ai.params[2] = 100;
      f.ai.update(9 * Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertEquals("APPROACH", f.ai.getState());
      assertNull(f.casting());
    }
  }

  @Test void initialWaitRunsWithoutTargetAndIsNotRepeatedWhenTargetReturns() {
    try (Fixture f = new Fixture(row(), 0, 1)) {
      f.ai.params[0] = 100;
      f.targetPosition().set(1000, 1000);
      f.ai.update(Animation.FRAME_DURATION);
      f.targetPosition().set(11, 10);
      f.ai.update(5 * Animation.FRAME_DURATION + .00001f);
      assertEquals(SkillCodes.attack, f.casting().skillId);
    }
  }
}
