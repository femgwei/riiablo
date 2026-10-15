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

class BloodLordAiIntegrationTest extends RiiabloTest {
  @Test
  void everyBloodLordRowResolvesNativeAiAndFrenzySkill() {
    int count = 0;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (!"BloodLord".equals(row.AI)) continue;
      count++;
      assertTrue(AI.findAI(12, row.AI) instanceof BloodLord);
      assertEquals(109, Riiablo.files.skills.get(row.Skill1).srvdofunc);
    }
    assertTrue(count > 0);
  }

  @Test
  void meleeSelectsFrenzyInA2OrBasicA1AndNeverRestartsActiveAction() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      for (int frenzy : new int[] {0, 100}) {
        try (Fixture f = new Fixture("bloodlord1", difficulty, 1f)) {
          f.ai.params[0] = 100;
          f.ai.params[2] = frenzy;
          f.ai.update(Animation.FRAME_DURATION);
          assertEquals(frenzy == 0 ? SkillCodes.attack
              : Riiablo.files.skills.get(f.ai.monster.monstats.Skill1).Id, f.casting().skillId);
          assertEquals(frenzy == 0 ? Engine.Monster.MODE_A1 : Engine.Monster.MODE_A2,
              f.sequence().mode1);
          f.sequence().started = true;
          f.ai.update(10f);
          assertTrue(f.sequence().started);
        }
      }
    }
  }

  @Test
  void failedMeleeRollStallsWithoutRollingApproachAndFarTargetUsesApproachChance() {
    try (Fixture f = new Fixture("bloodlord1", 0, 1f)) {
      f.ai.params[0] = 0;
      f.ai.params[1] = 100;
      f.ai.params[3] = 10;
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
      assertEquals("IDLE", f.ai.getState());
      f.ai.params[0] = 100;
      f.ai.update(9 * Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.ai.update(Animation.FRAME_DURATION + 0.00001f);
      assertNotNull(f.casting());
    }
    for (int approach : new int[] {0, 100}) {
      try (Fixture f = new Fixture("bloodlord1", 0, 18f)) {
        f.ai.params[1] = approach;
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(approach == 100 ? "APPROACH" : "IDLE", f.ai.getState());
        assertNull(f.casting());
      }
    }
  }

  @Test
  void rejectedFrenzyDoesNotSubstituteOrdinaryAttack() {
    try (Fixture f = new Fixture("bloodlord1", 0, 1f)) {
      f.ai.params[0] = f.ai.params[2] = 100;
      f.probe.reject = true;
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
      assertEquals("IDLE", f.ai.getState());
    }
  }
}
