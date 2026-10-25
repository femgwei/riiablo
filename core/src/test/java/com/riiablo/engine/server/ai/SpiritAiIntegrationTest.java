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

class SpiritAiIntegrationTest extends RiiabloTest {
  private String row() {
    for (MonStats.Entry mon : Riiablo.files.monstats)
      if ("Spirit".equals(mon.AI)) return mon.Id;
    throw new AssertionError("Missing Spirit row");
  }

  @Test void realRowSelectsDedicatedAi() {
    int count = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"Spirit".equals(mon.AI)) continue;
      count++;
      assertEquals("Spirit", AI.findAI(12, mon.AI).getClass().getSimpleName());
    }
    assertEquals(1, count);
  }

  @Test void attacksOnlyOnceAcrossAllDifficultiesAndPreservesBusyAction() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture(row(), difficulty, 1)) {
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(SkillCodes.attack, f.casting().skillId);
        assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
        f.sequence().started = true;
        f.ai.update(10);
        assertTrue(f.sequence().started);
        f.finishAction();
        for (int i = 0; i < 10; i++) {
          f.ai.update(51 * Animation.FRAME_DURATION);
          assertNull(f.casting());
          assertNull(f.sequence());
          assertEquals("IDLE", f.ai.getState());
        }
      }
    }
  }

  @Test void farWaitsTenFramesThenCanAttackWhenTargetEntersMelee() {
    try (Fixture f = new Fixture(row(), 0, 10)) {
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      assertNull(f.casting());
      f.targetPosition().set(11, 10);
      f.ai.update(9 * Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertEquals(SkillCodes.attack, f.casting().skillId);
    }
  }

  @Test void targetLossAndOwnerWarpDoNotRearmSpentAttack() {
    try (Fixture f = new Fixture(row(), 0, 1)) {
      f.ai.update(Animation.FRAME_DURATION);
      assertNotNull(f.casting());
      f.finishAction();
      f.targetPosition().set(1000, 1000);
      f.ai.update(10);
      f.ai.onOwnerWarp();
      f.targetPosition().set(11, 10);
      f.ai.update(10);
      assertNull(f.casting());
    }
  }

  @Test void missingTargetDoesNotConsumeOnlyAttackAndDeathStopsDecisions() {
    try (Fixture f = new Fixture(row(), 0, 1)) {
      f.targetPosition().set(1000, 1000);
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.targetPosition().set(11, 10);
      f.ai.update(11 * Animation.FRAME_DURATION);
      assertNotNull(f.casting());
      f.ai.kill();
      f.ai.update(100);
      assertNull(f.casting());
      assertEquals(Engine.Monster.MODE_DT, f.sequence().mode1);
    }
  }

  @Test void spentSpiritWaitsExactlyFiftyFramesWithAndWithoutTarget() {
    for (boolean missing : new boolean[] {false, true}) {
      try (Fixture f = new Fixture(row(), 0, 1)) {
        CountingSpirit spirit = new CountingSpirit(f.actor);
        f.world.getInjector().inject(spirit);
        spirit.pathfinder = f.paths;
        spirit.initialize();
        spirit.update(Animation.FRAME_DURATION);
        assertNotNull(f.casting());
        f.finishAction();
        if (missing) f.targetPosition().set(1000, 1000);
        spirit.update(Animation.FRAME_DURATION);
        int calls = spirit.decisions;
        spirit.update(49 * Animation.FRAME_DURATION);
        assertEquals(calls, spirit.decisions);
        spirit.update(Animation.FRAME_DURATION + .00001f);
        assertEquals(calls + 1, spirit.decisions);
        assertNull(f.casting());
      }
    }
  }

  @Test void checkpointKeepsSpentAttackAcrossFreshWorldAndTargetLoss() {
    AiRuntimeState image;
    try (Fixture f = new Fixture(row(), 0, 1)) {
      f.ai.update(Animation.FRAME_DURATION);
      assertNotNull(f.casting());
      f.finishAction();
      image = AiRuntimeState.capture(f.ai, f.actor);
    }
    try (Fixture f = new Fixture(row(), 0, 1)) {
      image.restore(f.ai, f.actor);
      for (int i = 0; i < 10; i++) {
        f.targetPosition().set(i % 2 == 0 ? 1000 : 11, 10);
        f.ai.update(51 * Animation.FRAME_DURATION);
        assertNull(f.casting()); assertNull(f.sequence());
        assertEquals("IDLE", f.ai.getState());
      }
    }
  }

  private static final class CountingSpirit extends Spirit {
    int decisions;
    CountingSpirit(int entityId) { super(entityId); }
    @Override protected void think(int targetId, float distance, boolean combat) {
      decisions++;
      super.think(targetId, distance, combat);
    }
    @Override protected void thinkWithoutTarget() {
      decisions++;
      super.thinkWithoutTarget();
    }
  }
}
