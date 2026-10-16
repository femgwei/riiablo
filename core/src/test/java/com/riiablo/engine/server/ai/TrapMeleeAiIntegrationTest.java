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

class TrapMeleeAiIntegrationTest extends RiiabloTest {
  private String row() {
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if ("Trap-Melee".equals(mon.AI)) return mon.Id;
    }
    throw new AssertionError("Missing real Trap-Melee row");
  }

  @Test void realTableNameResolvesWithoutFallback() {
    int count = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"Trap-Melee".equals(mon.AI)) continue;
      assertEquals(TrapMelee.class, AI.findAI(12, mon.AI).getClass());
      count++;
    }
    assertEquals(1, count);
  }

  @Test void attacksInAllDifficultiesAndBlocksWhileActionRuns() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture(row(), difficulty, 1)) {
        f.ai.params[0] = 100;
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(SkillCodes.attack, f.casting().skillId);
        assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
        f.sequence().started = true;
        f.ai.update(10);
        assertTrue(f.sequence().started);
      }
    }
  }

  @Test void failedAttackWaitsConfiguredFrames() {
    try (Fixture f = new Fixture(row(), 0, 1)) {
      f.ai.params[0] = 0;
      f.ai.params[1] = 17;
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.ai.params[0] = 100;
      f.ai.update(16 * Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertNotNull(f.casting());
    }
  }

  @Test void distantTargetWaitsFortyFramesAndNeverApproaches() {
    try (Fixture f = new Fixture(row(), 0, 10)) {
      f.ai.params[0] = 100;
      f.ai.params[1] = 1;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      f.targetPosition().set(11, 10);
      f.ai.update(39 * Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertNotNull(f.casting());
    }
  }

  @Test void absentTargetAlsoWaitsFortyFrames() {
    try (Fixture f = new Fixture(row(), 0, 1)) {
      f.ai.params[0] = 100;
      f.targetPosition().set(1000, 1000);
      f.ai.update(Animation.FRAME_DURATION);
      f.targetPosition().set(11, 10);
      f.ai.update(39 * Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertNotNull(f.casting());
    }
  }
}
