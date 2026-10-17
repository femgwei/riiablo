package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import org.junit.jupiter.api.Test;

class ScarabInitialOrbitTest extends RiiabloTest {
  @Test void initialPursuitRequestsDefaultFiveStepOrbit() { verifyOrbit(true); }
  @Test void rejectedOrbitStillAdvancesPhaseWithoutRetry() { verifyOrbit(false); }

  private void verifyOrbit(boolean accepted) {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      for (int seedValue : new int[] {0, 1, 2, 123456}) {
        try (Fixture f = new Fixture("scarab1", difficulty, 10)) {
          Scarab ai = (Scarab) f.ai;
          Riiablo.gameSeed = seedValue;
          D2Seed seed = new D2Seed(seedValue ^ (f.actor * 0x45D9F3B), 666);
          boolean direction = (Seed.rollRandomNumber(seed) & 255L) < 128L;
          when(f.paths.findNativeOrbit(anyInt(), anyInt(), anyInt(), anyBoolean())).thenReturn(accepted);
          ai.update(0.04f);
          verify(f.paths).findNativeOrbit(f.actor, f.target, 5, direction);
          verifyNoMoreInteractions(f.paths);
          assertEquals(1, ai.aiParam0);
          assertEquals(accepted ? "APPROACH" : "IDLE", ai.getState());
          assertEquals(ai.SLEEP, ai.time);
          assertNull(f.casting());
          assertEquals(Seed.rollRandomNumber(seed) & 0xffffffffL, ai.rollAiRandomNumber());
        }
      }
    }
  }
}
