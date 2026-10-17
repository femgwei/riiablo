package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import org.junit.jupiter.api.Test;

class BaboonMeleeDecisionTest extends RiiabloTest {
  @Test void firstAttackSkipsAttackChanceAndUsesNativeAttackChoice() {
    for (int seedValue : new int[] {0, 1, 2, 123456}) {
      try (Fixture f = new Fixture("baboon1", 0, 1)) {
        Baboon ai = (Baboon) f.ai;
        ai.params[2] = 0;
        ai.params[3] = 37;
        Riiablo.gameSeed = seedValue;
        D2Seed seed = new D2Seed(seedValue ^ (f.actor * 0x45D9F3B), 666);
        byte expected = Seed.rollPercentage(seed) < 37 ? Engine.Monster.MODE_A1 : Engine.Monster.MODE_A2;
        ai.update(0.04f);
        assertEquals("ATTACK", ai.getState());
        assertEquals(expected, f.sequence().mode1);
        assertEquals(f.target, f.casting().targetId);
        assertEquals(1, ai.aiParam1);
        assertEquals(Seed.rollRandomNumber(seed) & 0xffffffffL, ai.rollAiRandomNumber());
      }
    }
  }

  @Test void circleDecisionIsFollowedByNeutralWaitEvenWhenOrbitSucceeds() { verifyIdle(true); }
  @Test void declinedCircleStillConsumesChanceDrawAndWaits() { verifyIdle(false); }

  private void verifyIdle(boolean circle) {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("baboon1", difficulty, 1)) {
        Baboon ai = (Baboon) f.ai;
        ai.aiParam1 = 1;
        ai.params[2] = 0;
        ai.params[1] = circle ? 100 : 0;
        Riiablo.gameSeed = 12;
        D2Seed seed = new D2Seed(12 ^ (f.actor * 0x45D9F3B), 666);
        Seed.rollPercentage(seed);
        Seed.rollPercentage(seed);
        boolean direction = circle && (Seed.rollRandomNumber(seed) & 255L) < 128L;
        ai.update(0.04f);
        if (circle) verify(f.paths).findNativeOrbit(f.actor, f.target, 3, direction);
        else verify(f.paths, never()).findNativeOrbit(anyInt(), anyInt(), anyInt(), anyBoolean());
        assertEquals("IDLE", ai.getState());
        assertEquals(circle ? 0 : 1, ai.aiParam1);
        assertEquals(15 * com.riiablo.codec.Animation.FRAME_DURATION, ai.time);
        verify(f.paths).findPath(f.actor, null);
        assertNull(f.casting());
        assertEquals(Seed.rollRandomNumber(seed) & 0xffffffffL, ai.rollAiRandomNumber());
      }
    }
  }
  @Test void attackChoiceProbabilityEndpointsFollowOriginalHelper() {
    for (int chance : new int[] {0, 100}) {
      try (Fixture f = new Fixture("baboon1", 0, 1)) {
        Baboon ai = (Baboon) f.ai;
        ai.params[3] = chance;
        ai.update(0.04f);
        assertEquals(chance == 100 ? Engine.Monster.MODE_A1 : Engine.Monster.MODE_A2, f.sequence().mode1);
      }
    }
  }
}