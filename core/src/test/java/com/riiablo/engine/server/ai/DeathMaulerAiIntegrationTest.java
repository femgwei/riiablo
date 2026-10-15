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

class DeathMaulerAiIntegrationTest extends RiiabloTest {
  @Test void sixRealRowsSelectDedicatedAiAndDeathMaul() {
    int rows = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"DeathMauler".equals(mon.AI)) continue;
      rows++;
      assertEquals("DeathMauler", AI.findAI(12, mon.AI).getClass().getSimpleName());
      assertEquals(308, Riiablo.files.skills.get(mon.Skill1).Id);
      assertEquals(136, Riiablo.files.skills.get(mon.Skill1).srvdofunc);
    }
    assertEquals(6, rows);
  }

  @Test void meleeUsesA1InsteadOfRangedSkillInAllDifficulties() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("deathmauler1", difficulty, 1)) {
        f.ai.params[0] = f.ai.params[3] = 100;
        f.ai.params[2] = 100;
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(SkillCodes.attack, f.casting().skillId);
        assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
      }
    }
  }

  @Test void failedMeleeRollWaitsFifteenFramesWithoutApproachingOrShooting() {
    try (Fixture f = new Fixture("deathmauler1", 0, 1)) {
      f.ai.params[0] = 0;
      f.ai.params[1] = f.ai.params[3] = 100;
      f.ai.params[2] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      assertNull(f.casting());
      f.ai.params[0] = 100;
      f.ai.update(14 * Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertEquals(SkillCodes.attack, f.casting().skillId);
    }
  }

  @Test void rangedSkillPrecedesApproachAndPreservesBusyAction() {
    try (Fixture f = new Fixture("deathmauler1", 0, 10)) {
      f.ai.params[1] = f.ai.params[3] = 100;
      f.ai.params[2] = 11;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(308, f.casting().skillId);
      assertEquals("CAST", f.ai.getState());
      f.sequence().started = true;
      f.ai.update(10);
      assertTrue(f.sequence().started);
    }
  }

  @Test void strictRangeAndFailedShootRollFallThroughToApproach() {
    for (boolean boundary : new boolean[] {false, true}) {
      try (Fixture f = new Fixture("deathmauler1", 0, 10)) {
        f.ai.params[1] = 100;
        f.ai.params[2] = boundary ? 10 : 11;
        f.ai.params[3] = boundary ? 100 : 0;
        // Directly supply the native distance/combat inputs to isolate equality.
        ((NativeDecisionAI) f.ai).think(f.target, 10, false);
        assertEquals("APPROACH", f.ai.getState());
        assertNull(f.casting());
      }
    }
  }

  @Test void rejectedRangedSkillDoesNotBecomeApproachOrOrdinaryAttack() {
    try (Fixture f = new Fixture("deathmauler1", 0, 10)) {
      f.ai.params[1] = f.ai.params[3] = 100;
      f.ai.params[2] = 20;
      f.probe.reject = true;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      assertNull(f.casting());
    }
  }

  @Test void failedRangedRollsWaitFifteenFramesAndNoTargetDoesNotCast() {
    try (Fixture f = new Fixture("deathmauler1", 0, 10)) {
      f.ai.params[1] = f.ai.params[3] = 0;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      f.ai.params[1] = 100;
      f.ai.update(14 * Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertEquals("APPROACH", f.ai.getState());
    }
    try (Fixture f = new Fixture("deathmauler1", 0, 1)) {
      f.targetPosition().set(1000, 1000);
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
    }
  }
}
