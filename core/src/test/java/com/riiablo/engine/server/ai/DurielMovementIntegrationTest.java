package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Velocity;
import org.junit.jupiter.api.Test;

class DurielMovementIntegrationTest extends RiiabloTest {
  @Test void diagnosticSnapshotDoesNotRunAnotherDecisionOrPathQuery() {
    try (Fixture f = new Fixture("duriel", 0, 10)) {
      f.ai.params[Duriel.PARAM_CHARGE_CHANCE] = 0;
      f.ai.update(Animation.FRAME_DURATION);
      clearInvocations(f.paths);
      String snapshot = f.ai.getDecisionDiagnostics();
      assertTrue(snapshot.contains("decisions=1 target=" + f.target));
      assertTrue(snapshot.contains("approach=ACCEPTED"));
      assertEquals(snapshot, f.ai.getDecisionDiagnostics());
      verifyNoInteractions(f.paths);
      assertEquals("APPROACH", f.ai.getState());
      NativeDecisionAI observed = spy((NativeDecisionAI) f.ai);
      // Some subclasses perform recovery scheduling inside this gameplay hook.
      doThrow(new AssertionError("diagnostics must not execute gameplay hooks"))
          .when(observed).actionBlocksDecision();
      assertDoesNotThrow(observed::getDecisionDiagnostics);
      verify(observed, never()).actionBlocksDecision();
    }
  }

  @Test void pathTypeThirteenIsNotAThirteenPercentSpeedBonus() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("duriel", difficulty, 10)) {
        f.ai.params[Duriel.PARAM_CHARGE_CHANCE] = 0;
        Velocity velocity = f.world.getMapper(Velocity.class).get(f.actor);
        velocity.setModeSpeedBonusPercent(50);
        f.world.getMapper(Running.class).create(f.actor);
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals("APPROACH", f.ai.getState());
        assertEquals(0f, velocity.modeSpeedBonusMultiplier,
            "SetVelocity(unit, 13, 0, 0): path type 13, velocity bonus 0");
        assertEquals(velocity.walkSpeed * Velocity.MONSTER_BASE_SPEED_MULTIPLIER,
            velocity.speed(false), .00001f);
        assertFalse(f.world.getMapper(Running.class).has(f.actor));
        assertNull(f.casting());
        verify(f.paths).findPath(f.actor, f.targetPosition(), false, f.target);
      }
    }
  }

  @Test void rejectedApproachClearsThePreviousMovementBonus() {
    try (Fixture f = new Fixture("duriel", 0, 10)) {
      f.ai.params[Duriel.PARAM_CHARGE_CHANCE] = 0;
      Velocity velocity = f.world.getMapper(Velocity.class).get(f.actor);
      velocity.setModeSpeedBonusPercent(50);
      when(f.paths.findPath(anyInt(), any(), anyBoolean(), anyInt())).thenReturn(false);
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      assertEquals(0f, velocity.modeSpeedBonusMultiplier);
      assertNull(f.casting());
    }
  }

  @Test void realTableRadiusAllowsFarTargetsWithoutArenaRoomConstraints() {
    for (String row : new String[] {"duriel", "deathmauler1"}) {
      for (int difficulty = 0; difficulty < 3; difficulty++) {
        try (Fixture f = new Fixture(row, difficulty, 18)) {
          int radius = f.world.getMapper(Monster.class).get(f.actor).monstats.aidist[difficulty];
          int effectiveRadius = radius == 0 ? 35 : radius;
          assertTrue(effectiveRadius >= 18, row + " difficulty=" + difficulty + " aidist=" + radius);
          assertEquals(f.target, f.ai.findNearestTargetWithAidist(new float[1]));
          f.targetPosition().set(20, 10);
          assertEquals(f.target, f.ai.findNearestTargetWithAidist(new float[1]));
        }
      }
    }
  }
}
