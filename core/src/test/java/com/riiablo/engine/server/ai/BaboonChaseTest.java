package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.Velocity;
import org.junit.jupiter.api.Test;

class BaboonChaseTest extends RiiabloTest {
  @Test void ordinaryApproachRequestsNativeChase() { verifyChase(false); }
  @Test void healthyRecoveryRequestsNativeChaseWithRunRatioBonus() { verifyChase(true); }

  private void verifyChase(boolean recovery) {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("baboon1", difficulty, 10)) {
        Baboon ai = (Baboon) f.ai;
        if (recovery) {
          f.health(76, 100);
          ai.aiParam0 = 4;
        }
        ai.update(0.04f);
        verify(f.paths).findNativeChase(f.actor, f.target);
        verify(f.paths, never()).findPath(eq(f.actor), any(), anyBoolean(), anyInt());
        assertEquals("APPROACH", ai.getState());
        assertEquals(ai.SLEEP, ai.time);
        assertNull(f.casting());
        int expectedBonus = 0;
        if (recovery && ai.monster.monstats.Velocity > 0) {
          int ratio = 100 * ai.monster.monstats.Run / ai.monster.monstats.Velocity;
          if (ratio >= 100) expectedBonus = Math.min(ratio - 100, 120);
        }
        assertEquals(expectedBonus / 100f,
            f.world.getMapper(Velocity.class).get(f.actor).modeSpeedBonusMultiplier);
        if (recovery) assertEquals(0, ai.aiParam0);
      }
    }
  }
}
