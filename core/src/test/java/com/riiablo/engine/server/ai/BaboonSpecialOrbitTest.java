package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.NativeAiState;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import org.junit.jupiter.api.Test;

class BaboonSpecialOrbitTest extends RiiabloTest {
  @Test void distantRegenerationAlwaysEndsWithTwentyFrameWait() {
    for (boolean chosen : new boolean[] {false, true}) {
      for (boolean accepted : new boolean[] {false, true}) {
        try (Fixture f = new Fixture("baboon1", 0, 25)) {
          Baboon ai = (Baboon) f.ai;
          f.health(50, 100);
          ai.aiParam0 = 4;
          f.seedFirstRoll(33, chosen);
          D2Seed seed = new D2Seed(Riiablo.gameSeed ^ (f.actor * 0x45D9F3B), 666);
          Seed.rollPercentage(seed);
          boolean direction = chosen && (Seed.rollRandomNumber(seed) & 255L) < 128L;
          when(f.paths.findNativeOrbit(anyInt(), anyInt(), anyInt(), anyBoolean())).thenReturn(accepted);
          ai.update(0.04f);
          if (chosen) verify(f.paths).findNativeOrbit(f.actor, f.target, 4, direction);
          else verify(f.paths, never()).findNativeOrbit(anyInt(), anyInt(), anyInt(), anyBoolean());
          verify(f.paths).findPath(f.actor, null);
          assertEquals("IDLE", ai.getState());
          assertEquals(20 * com.riiablo.codec.Animation.FRAME_DURATION, ai.time);
          assertEquals(3, ai.aiParam0);
          assertNull(f.casting());
          assertEquals(Seed.rollRandomNumber(seed) & 0xffffffffL, ai.rollAiRandomNumber());
        }
      }
    }
  }

  @Test void hitMemoryOrbitReturnsWithoutOrdinaryNeutralTail() {
    for (byte mode : new byte[] {3, 19}) {
      for (boolean accepted : new boolean[] {false, true}) {
        try (Fixture f = new Fixture("baboon1", 0, 1)) {
          Baboon ai = (Baboon) f.ai;
          f.health(100, 100);
          ai.aiParam1 = 1;
          f.world.getMapper(NativeAiState.class).create(f.actor).previousMode = mode;
          f.seedFirstRoll(20, true);
          D2Seed seed = new D2Seed(Riiablo.gameSeed ^ (f.actor * 0x45D9F3B), 666);
          Seed.rollPercentage(seed);
          boolean direction = (Seed.rollRandomNumber(seed) & 255L) < 128L;
          when(f.paths.findNativeOrbit(anyInt(), anyInt(), anyInt(), anyBoolean())).thenReturn(accepted);
          ai.update(0.04f);
          verify(f.paths).findNativeOrbit(f.actor, f.target, 3, direction);
          verify(f.paths, never()).findPath(f.actor, null);
          assertEquals(accepted ? "APPROACH" : "IDLE", ai.getState());
          assertEquals(0, ai.aiParam1);
          assertEquals(ai.SLEEP, ai.time);
          assertNull(f.casting());
          assertEquals(Seed.rollRandomNumber(seed) & 0xffffffffL, ai.rollAiRandomNumber());
        }
      }
    }
  }
}

