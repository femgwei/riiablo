package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.NativeAiState;
import org.junit.jupiter.api.Test;

class BaboonDecisionTimingTest extends RiiabloTest {
  @Test void ordinaryAttackHasNoExtraWait() { verifyTiming(0); }
  @Test void ordinaryApproachHasNoExtraWait() { verifyTiming(1); }
  @Test void regenerationAttackHasNoExtraWait() { verifyTiming(2); }
  @Test void healthyRecoveryApproachHasNoExtraWait() { verifyTiming(3); }
  @Test void regenerationEscapeHasNoExtraWait() { verifyTiming(4); }
  @Test void failedMeleeEscapeAttackHasNoExtraWait() { verifyTiming(5); }
  @Test void hitStartsRegenerationWithoutExtraWait() { verifyTiming(6); }
  @Test void hitFirstAttackHasNoExtraWait() { verifyTiming(7); }

  private void verifyTiming(int branch) {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      int distance = branch == 1 || branch == 3 || branch == 4 ? 10 : 1;
      try (Fixture f = new Fixture("baboon1", difficulty, distance)) {
        Baboon ai = (Baboon) f.ai;
        f.health(100, 100);
        if (branch >= 2 && branch <= 5) {
          f.health(branch == 3 ? 76 : 50, 100);
          ai.aiParam0 = 4;
          if (distance == 1) f.seedFirstRoll(33, branch == 2);
          if (branch == 5) when(f.paths.findPath(anyInt(), any(), anyBoolean(), anyInt())).thenReturn(false);
        }
        if (branch >= 6) {
          f.world.getMapper(NativeAiState.class).create(f.actor).previousMode = 3;
          if (branch == 6) {
            f.health(25, 100);
            ai.params[0] = 50;
            f.seedFirstRoll(50, true);
          }
        }
        ai.update(0.04f);
        String expected = branch == 1 || branch == 3 ? "APPROACH"
            : branch == 4 || branch == 6 ? "ESCAPE" : "ATTACK";
        assertEquals(expected, ai.getState());
        assertEquals(ai.SLEEP, ai.time);
        if ("ATTACK".equals(expected)) {
          assertNotNull(f.casting());
          assertEquals(f.target, f.casting().targetId);
          assertNotNull(f.sequence());
        } else assertNull(f.casting());
        if (branch == 3) assertEquals(0, ai.aiParam0);
        if (branch == 6) assertTrue(ai.aiParam0 >= 2 && ai.aiParam0 <= 6);
      }
    }
  }
}
