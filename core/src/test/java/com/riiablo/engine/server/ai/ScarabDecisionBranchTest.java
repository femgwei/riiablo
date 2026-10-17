package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.Velocity;
import org.junit.jupiter.api.Test;

class ScarabDecisionBranchTest extends RiiabloTest {
  @Test void ordinaryPursuitPhaseIsNotAnAttackCommand() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("scarab1", difficulty, 10)) {
        Scarab ai = (Scarab) f.ai;
        ai.aiParam0 = 1;
        ai.update(0.04f);
        assertEquals("APPROACH", ai.getState());
        assertEquals(0f, f.world.getMapper(Velocity.class).get(f.actor).modeSpeedBonusMultiplier);
      }
    }
  }

  @Test void failedAttackRollCannotFallThroughIntoJab() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("scarab1", difficulty, 1)) {
        Scarab ai = (Scarab) f.ai;
        ai.params[0] = 0;
        ai.params[2] = 13;
        ai.params[3] = 100;
        ai.update(0.04f);
        assertEquals("IDLE", ai.getState());
        assertNull(f.casting());
        assertNull(f.sequence());
        assertEquals(13 * com.riiablo.codec.Animation.FRAME_DURATION, ai.time);
      }
    }
  }
  @Test void successfulJabRollUsesConfiguredSkillInsteadOfBasicAttack() {
    try (Fixture f = new Fixture("scarab1", 0, 1)) {
      Scarab ai = (Scarab) f.ai;
      assertNotNull(ai.monster.monstats.Skill1);
      assertFalse(ai.monster.monstats.Skill1.isEmpty());
      ai.params[0] = 100;
      ai.params[3] = 100;
      ai.update(0.04f);
      assertEquals("JAB", ai.getState());
      assertNotNull(f.casting());
      assertEquals(com.riiablo.Riiablo.files.skills.get(ai.monster.monstats.Skill1).Id, f.casting().skillId);
      assertEquals(f.target, f.casting().targetId);
      assertEquals(f.targetPosition(), f.casting().targetVec);
      assertNotNull(f.sequence().nativeMonster);
      assertEquals(f.sequence().nativeMonster.mode, f.sequence().mode1);
    }
  }
}