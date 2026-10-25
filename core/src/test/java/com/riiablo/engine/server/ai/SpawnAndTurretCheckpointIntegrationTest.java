package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import org.junit.jupiter.api.Test;

/** Real rows and decisions, at controlled action-completion boundaries. */
class SpawnAndTurretCheckpointIntegrationTest extends RiiabloTest {
  @Test void hatchedEggDiesAfterRestoreWithoutHatchingAgain() {
    AiRuntimeState image;
    try (Fixture f = new Fixture("maggotegg1", 0, 10)) {
      f.ai.params[MaggotEgg.PARAM_HATCH_CHANCE] = 100;
      f.ai.params[MaggotEgg.PARAM_STALL_DURATION] = 1;
      f.ai.update(Animation.FRAME_DURATION);
      assertNotNull(f.casting()); f.finishAction();
      image = AiRuntimeState.capture(f.ai, f.actor);
    }
    try (Fixture f = new Fixture("maggotegg1", 0, 10)) {
      image.restore(f.ai, f.actor);
      f.ai.update(2 * Animation.FRAME_DURATION);
      assertEquals("DEAD", f.ai.getState());
      assertNull(f.casting()); assertEquals(0, f.probe.starts);
    }
  }

  @Test void exhaustedMinionSpawnerDoesNotGetANewBudget() {
    String row = row("MinionSpawner"); AiRuntimeState image;
    try (Fixture f = new Fixture(row, 0, 10)) {
      f.ai.params[0] = 1; f.ai.params[1] = 10; f.ai.params[2] = 20; f.ai.params[3] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      assertNotNull(f.casting()); f.finishAction();
      image = AiRuntimeState.capture(f.ai, f.actor);
    }
    try (Fixture f = new Fixture(row, 0, 10)) {
      image.restore(f.ai, f.actor);
      for (int i = 0; i < 5; i++) f.ai.update(100 * Animation.FRAME_DURATION);
      assertNull(f.casting()); assertEquals(0, f.probe.starts);
    }
  }

  @Test void turretResumesDirectionalBurstAndCooldownRatherThanInitializationCast() {
    AiRuntimeState image; String expected;
    try (Fixture f = new Fixture("turret1", 0, 10)) {
      f.ai.params[DesertTurret.PARAM_SHORT_DELAY] = 10;
      f.ai.params[DesertTurret.PARAM_LONG_DELAY] = 30;
      f.ai.params[DesertTurret.PARAM_NUM_SHOTS] = 2;
      f.ai.params[DesertTurret.PARAM_RANGE] = 20;
      f.ai.params[DesertTurret.PARAM_SPREAD] = 2;
      f.ai.update(Animation.FRAME_DURATION); assertNotNull(f.casting()); f.finishAction();
      f.ai.update(16 * Animation.FRAME_DURATION);
      assertNotNull(f.casting());
      assertEquals(12f, f.casting().targetVec.x); assertEquals(12f, f.casting().targetVec.y);
      f.finishAction(); image = AiRuntimeState.capture(f.ai, f.actor);
      expected = continueTurret(f);
      assertTrue(expected.contains("SHOT"));
    }
    try (Fixture f = new Fixture("turret1", 0, 10)) {
      image.restore(f.ai, f.actor);
      assertEquals(expected, continueTurret(f));
    }
  }

  private static String continueTurret(Fixture f) {
    StringBuilder trace = new StringBuilder();
    for (int i = 0; i < 80; i++) {
      f.ai.update(Animation.FRAME_DURATION);
      trace.append('|').append(f.ai.getState()).append(':').append(f.ai.getDecisionDiagnostics());
      if (f.casting() != null) {
        assertNotEquals(f.world.getMapper(com.riiablo.engine.server.component.Position.class)
            .get(f.actor).position, f.casting().targetVec, "initialization cast repeated");
        trace.append(":SHOT:").append(f.casting().targetVec);
        f.finishAction();
      }
    }
    return trace.toString();
  }

  private static String row(String ai) {
    for (MonStats.Entry row : Riiablo.files.monstats) if (ai.equals(row.AI)) return row.Id;
    throw new AssertionError("Missing AI row: " + ai);
  }
}
