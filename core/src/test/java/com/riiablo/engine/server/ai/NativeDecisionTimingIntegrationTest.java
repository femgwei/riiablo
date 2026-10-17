package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.engine.server.AuthoritativeSimulation;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import org.junit.jupiter.api.Test;

/** AITACTICS_Idle schedules current game frame + nFrames, not float subtraction. */
class NativeDecisionTimingIntegrationTest extends RiiabloTest {
  @Test void waitsExpireOnTheExactFrameWithoutAnEpsilonDelta() {
    for (int wait : new int[] {1, 5, 7, 10, 15, 35, 50, 100}) {
      try (Fixture f = new Fixture("doomknight1", 0, 1)) {
        CountingAI ai = attach(f, wait);
        ai.update(Animation.FRAME_DURATION);
        for (int frame = 1; frame < wait; frame++) {
          ai.update(Animation.FRAME_DURATION);
          assertEquals(1, ai.decisions, "early wait=" + wait + " frame=" + frame);
        }
        ai.update(Animation.FRAME_DURATION);
        assertEquals(2, ai.decisions, "deadline wait=" + wait);
      }
    }
  }

  @Test void authoritativeDeadlineElapsesWhileAiIsNotUpdated() {
    try (Fixture f = new Fixture("doomknight1", 0, 1)) {
      CountingAI ai = attach(f, 10);
      AuthoritativeSimulation simulation = new AuthoritativeSimulation(f.world);
      simulation.tick(() -> ai.update(Animation.FRAME_DURATION), null);
      for (int i = 0; i < 9; i++) simulation.tick(null, null);
      assertEquals(1, ai.decisions);
      simulation.tick(() -> ai.update(Animation.FRAME_DURATION), null);
      assertEquals(2, ai.decisions, "wake at frame 11 must observe the frame-1 deadline");
    }
  }

  @Test void expiredWaitStillPreservesAnActiveAction() {
    try (Fixture f = new Fixture("doomknight1", 0, 1)) {
      CountingAI ai = attach(f, 5);
      ai.update(Animation.FRAME_DURATION);
      ai.busy = true;
      for (int i = 0; i < 20; i++) ai.update(Animation.FRAME_DURATION);
      assertEquals(1, ai.decisions);
      ai.busy = false;
      ai.update(Animation.FRAME_DURATION);
      assertEquals(2, ai.decisions);
    }
  }

  @Test void realMeleeRangedAndChargeActionsResumeOnTheDeadlineInAllDifficulties() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      // Ordinary melee: a failed attack roll requests precisely seven frames.
      try (Fixture f = new Fixture("doomknight1", difficulty, 1)) {
        f.ai.params[0] = 0;
        f.ai.params[1] = 7;
        f.ai.update(Animation.FRAME_DURATION);
        f.ai.params[0] = 100;
        advanceBeforeDeadline(f, 7);
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
        preserveBusyAction(f);
      }
      // Ranged: the real DeathMauler decision waits fifteen frames before retrying.
      try (Fixture f = new Fixture(rowFor("DeathMauler"), difficulty, 10)) {
        f.ai.params[1] = f.ai.params[3] = 0;
        f.ai.update(Animation.FRAME_DURATION);
        f.ai.params[2] = 30;
        f.ai.params[3] = 100;
        advanceBeforeDeadline(f, 15);
        f.ai.update(Animation.FRAME_DURATION);
        assertFirstSkillTarget(f);
        assertTrue(f.sequence().nativeDeathMaul);
        preserveBusyAction(f);
      }
      // Charge: a rejected start waits ten frames, then retries the same skill.
      try (Fixture f = new Fixture("duriel", difficulty, 10)) {
        f.ai.params[Duriel.PARAM_CHARGE_CHANCE] = 100;
        f.probe.reject = true;
        f.ai.update(Animation.FRAME_DURATION);
        assertNull(f.casting());
        f.probe.reject = false;
        advanceBeforeDeadline(f, 10);
        f.ai.update(Animation.FRAME_DURATION);
        assertFirstSkillTarget(f);
        preserveBusyAction(f);
      }
    }
  }

  private static void advanceBeforeDeadline(Fixture f, int frames) {
    for (int i = 1; i < frames; i++) {
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting(), "must not start an action before frame " + frames);
    }
  }

  private static void assertFirstSkillTarget(Fixture f) {
    MonStats.Entry row = f.world.getMapper(
        com.riiablo.engine.server.component.Monster.class).get(f.actor).monstats;
    assertNotNull(f.casting());
    assertEquals(Riiablo.files.skills.get(row.Skill1).Id, f.casting().skillId);
    assertEquals(f.target, f.casting().targetId);
  }

  private static void preserveBusyAction(Fixture f) {
    Object casting = f.casting();
    Object sequence = f.sequence();
    int starts = f.probe.starts;
    for (int i = 0; i < 30; i++) f.ai.update(Animation.FRAME_DURATION);
    assertSame(casting, f.casting());
    assertSame(sequence, f.sequence());
    assertEquals(starts, f.probe.starts, "active action must not restart");
  }

  private static String rowFor(String ai) {
    for (MonStats.Entry row : Riiablo.files.monstats) if (ai.equals(row.AI)) return row.Id;
    throw new AssertionError("Missing real AI row: " + ai);
  }

  private static CountingAI attach(Fixture f, int wait) {
    CountingAI ai = new CountingAI(f.actor, wait);
    f.world.getInjector().inject(ai);
    ai.pathfinder = f.paths;
    ai.initialize();
    return ai;
  }

  private static final class CountingAI extends NativeDecisionAI {
    final int wait;
    int decisions;
    boolean busy;
    CountingAI(int id, int wait) { super(id); this.wait = wait; }
    @Override protected boolean actionBlocksDecision() { return busy; }
    @Override protected void think(int target, float distance, boolean combat) {
      decisions++;
      idle(wait);
    }
    @Override protected void thinkWithoutTarget() { think(-1, 0, false); }
  }
}
