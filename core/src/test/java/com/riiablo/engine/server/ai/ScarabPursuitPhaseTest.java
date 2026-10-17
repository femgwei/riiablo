package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import org.junit.jupiter.api.Test;

class ScarabPursuitPhaseTest extends RiiabloTest {
  @Test void rollTenRetainsPursuitWithoutExtraDelay() { verifyPhase(10); }
  @Test void rollElevenResetsPhaseAndNextDecisionOrbits() { verifyPhase(11); }

  private void verifyPhase(int roll) {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("scarab1", difficulty, 10)) {
        Scarab ai = (Scarab) f.ai;
        ai.aiParam0 = 1;
        int seedValue = 0;
        for (; seedValue < 10000; seedValue++) {
          D2Seed candidate = new D2Seed(seedValue ^ (f.actor * 0x45D9F3B), 666);
          Seed.rollRandomNumber(candidate); // Reach a stream position with both boundary values.
          if (Seed.rollPercentage(candidate) == roll) break;
        }
        assertTrue(seedValue < 10000);
        Riiablo.gameSeed = seedValue;
        D2Seed seed = new D2Seed(seedValue ^ (f.actor * 0x45D9F3B), 666);
        Seed.rollRandomNumber(seed);
        ai.rollAiRandomNumber();
        Seed.rollPercentage(seed);
        ai.update(0.04f);
        verify(f.paths).findNativePath(f.actor, f.target, 4, 0);
        verify(f.paths, never()).findPath(eq(f.actor), any(), anyBoolean(), anyInt());
        assertEquals(roll > 10 ? 0 : 1, ai.aiParam0);
        assertEquals("APPROACH", ai.getState());
        assertEquals(ai.SLEEP, ai.time);
        assertNull(f.casting());
        if (roll > 10) {
          boolean direction = (Seed.rollRandomNumber(seed) & 255L) < 128L;
          ai.update(ai.SLEEP + 0.001f);
          verify(f.paths).findNativeOrbit(f.actor, f.target, 5, direction);
          assertEquals(1, ai.aiParam0);
        }
        assertEquals(Seed.rollRandomNumber(seed) & 0xffffffffL, ai.rollAiRandomNumber());
      }
    }
  }
}
