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

class DoomKnightAiIntegrationTest extends RiiabloTest {
  @Test void checkpointRetainsAbsoluteThinkFrameAndFirstDecisionRngAcrossWorlds() {
    try (Fixture source = new Fixture(row(), 0, 1); Fixture target = new Fixture(row(), 0, 1)) {
      source.ai.params[0] = 0; source.ai.params[1] = 17;
      Riiablo.engine = source.world; source.ai.update(Animation.FRAME_DURATION);
      source.ai.params[0] = 100;
      com.riiablo.engine.replay.MonsterRuntimeCheckpoint.capture(source.world, source.actor,
          java.util.Collections.singleton(source.actor)).restore(target.world, target.actor);
      AiRuntimeState.capture(source.ai, source.actor).restore(target.ai, target.actor);
      assertEquals(source.ai.getDecisionDiagnostics(), target.ai.getDecisionDiagnostics());
      for (Fixture f : new Fixture[] {source, target}) {
        Riiablo.engine = f.world;
        f.ai.update(16 * Animation.FRAME_DURATION); assertNull(f.casting());
        f.ai.update(Animation.FRAME_DURATION + .00001f); assertNotNull(f.casting());
      }
      assertEquals(source.ai.monster.rngState, target.ai.monster.rngState);
      assertEquals(source.ai.monster.nativeSeed.getNHighSeed(), target.ai.monster.nativeSeed.getNHighSeed());
      assertArrayEquals(AiRuntimeState.capture(source.ai, source.actor).bytes(), AiRuntimeState.capture(target.ai, target.actor).bytes());
    }
  }
  private String row() {
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if ("DoomKnight".equals(mon.AI)) return mon.Id;
    }
    throw new AssertionError("Missing real DoomKnight row");
  }

  @Test void allThreeRowsResolveDedicatedAi() {
    int rows = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"DoomKnight".equals(mon.AI)) continue;
      rows++;
      assertEquals("DoomKnight", AI.findAI(12, mon.AI).getClass().getSimpleName());
    }
    assertEquals(3, rows);
  }

  @Test void meleeAttackStartsImmediatelyInEveryDifficultyAndBusyActionIsPreserved() {
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

  @Test void meleeFailureUsesOnlyMeleeStall() {
    try (Fixture f = new Fixture(row(), 0, 1)) {
      f.ai.params[0] = 0;
      f.ai.params[1] = 17;
      f.ai.params[2] = 100;
      f.ai.params[3] = 1;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      f.ai.params[0] = 100;
      f.ai.update(16 * Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertEquals(SkillCodes.attack, f.casting().skillId);
    }
  }

  @Test void rangedFailureUsesRegularStallThenRetriesApproach() {
    try (Fixture f = new Fixture(row(), 0, 10)) {
      f.ai.params[0] = 100;
      f.ai.params[1] = 100;
      f.ai.params[2] = 0;
      f.ai.params[3] = 13;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      assertNull(f.casting());
      f.ai.params[2] = 100;
      f.ai.update(12 * Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertEquals("APPROACH", f.ai.getState());
      assertNull(f.casting());
    }
  }

  @Test void rangedApproachHasNoStartupWaitAndNoTargetDoesNotAttack() {
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
