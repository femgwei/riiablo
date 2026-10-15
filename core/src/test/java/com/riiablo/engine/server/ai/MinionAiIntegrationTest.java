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

class MinionAiIntegrationTest extends RiiabloTest {
  private String row() {
    for (MonStats.Entry mon : Riiablo.files.monstats)
      if ("Minion".equals(mon.AI)) return mon.Id;
    throw new AssertionError("Missing real Minion row");
  }

  @Test void elevenRowsSelectDedicatedAi() {
    int count = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"Minion".equals(mon.AI)) continue;
      count++;
      assertEquals("Minion", AI.findAI(12, mon.AI).getClass().getSimpleName());
    }
    assertEquals(11, count);
  }

  @Test void originalParameterOneBugSelectsA2InsteadOfParameterFour() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      for (boolean a2 : new boolean[] {false, true}) {
        try (Fixture f = new Fixture(row(), difficulty, 1)) {
          f.ai.params[0] = 100;
          f.ai.params[1] = a2 ? 100 : 0;
          f.ai.params[4] = a2 ? 0 : 100;
          f.ai.update(Animation.FRAME_DURATION);
          assertEquals(SkillCodes.attack, f.casting().skillId);
          assertEquals(a2 ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1,
              f.sequence().mode1);
          f.sequence().started = true;
          f.ai.update(10);
          assertTrue(f.sequence().started);
        }
      }
    }
  }

  @Test void failedAttackWaitsParameterOneAndDoesNotApproach() {
    try (Fixture f = new Fixture(row(), 0, 1)) {
      f.ai.params[0] = 0;
      f.ai.params[1] = 17;
      f.ai.params[2] = 100;
      f.ai.params[3] = 1;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      assertNull(f.casting());
      f.ai.params[0] = 100;
      f.ai.update(16 * Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertEquals(SkillCodes.attack, f.casting().skillId);
    }
  }

  @Test void failedApproachWaitsParameterThreeThenRetries() {
    try (Fixture f = new Fixture(row(), 0, 10)) {
      f.ai.params[0] = 100;
      f.ai.params[1] = 1;
      f.ai.params[2] = 0;
      f.ai.params[3] = 13;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      f.ai.params[2] = 100;
      f.ai.update(12 * Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertEquals("APPROACH", f.ai.getState());
      assertNull(f.casting());
    }
  }

  @Test void approachHasNoStartupWaitAndMissingTargetCannotAttack() {
    try (Fixture f = new Fixture(row(), 0, 10)) {
      f.ai.params[2] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("APPROACH", f.ai.getState());
      assertNull(f.casting());
    }
    try (Fixture f = new Fixture(row(), 0, 1)) {
      f.ai.params[0] = f.ai.params[2] = 100;
      f.targetPosition().set(1000, 1000);
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      assertNull(f.casting());
    }
  }
}
