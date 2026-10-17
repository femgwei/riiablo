package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import org.junit.jupiter.api.Test;

class BaboonChaseFallbackTest extends RiiabloTest {
  @Test void failedPursuitCanWaitTenFrames() { verifyFallback(true, false); }
  @Test void failedPursuitCanWalkAroundSelf() { verifyFallback(false, true); }
  @Test void failedLocalWalkDoesNotRetry() { verifyFallback(false, false); }

  private void verifyFallback(boolean idle, boolean accepted) {
    for (boolean recovery : new boolean[] {false, true}) {
      for (int difficulty = 0; difficulty < 3; difficulty++) {
        try (Fixture f = new Fixture("baboon1", difficulty, 10)) {
          Baboon ai = (Baboon) f.ai;
          if (recovery) {
            f.health(76, 100);
            ai.aiParam0 = 4;
          }
          int seedValue = 0;
          for (; seedValue < 10000; seedValue++) {
            D2Seed candidate = new D2Seed(seedValue ^ (f.actor * 0x45D9F3B), 666);
            if (((Seed.rollRandomNumber(candidate) & 0xffffffffL) % 100 >= 70) == idle) break;
          }
          assertTrue(seedValue < 10000);
          Riiablo.gameSeed = seedValue;
          D2Seed seed = new D2Seed(seedValue ^ (f.actor * 0x45D9F3B), 666);
          Seed.rollRandomNumber(seed);
          Vector2 local = null;
          if (!idle) {
            boolean axis = (Seed.rollRandomNumber(seed) & 1L) != 0;
            int random = (int) ((Seed.rollRandomNumber(seed) & 0xffffffffL) % 4);
            int x = axis ? 4 : random, y = axis ? random : 4;
            if ((Seed.rollRandomNumber(seed) & 1L) != 0) x = -x;
            if ((Seed.rollRandomNumber(seed) & 1L) != 0) y = -y;
            local = new Vector2(10 + x, 10 + y);
          }
          when(f.paths.findNativeChase(f.actor, f.target)).thenReturn(false);
          if (!idle) when(f.paths.findPath(f.actor, local, false, Engine.INVALID_ENTITY)).thenReturn(accepted);
          ai.update(0.04f);
          verify(f.paths).findNativeChase(f.actor, f.target);
          verify(f.paths, never()).findPath(f.actor, f.targetPosition(), false, f.target);
          if (idle) verify(f.paths, never()).findPath(eq(f.actor), any(Vector2.class), eq(false), eq(Engine.INVALID_ENTITY));
          else verify(f.paths).findPath(f.actor, local, false, Engine.INVALID_ENTITY);
          assertEquals(idle || !accepted ? "IDLE" : "APPROACH", ai.getState());
          assertEquals(idle ? 10 * com.riiablo.codec.Animation.FRAME_DURATION : ai.SLEEP, ai.time);
          assertEquals(0, ai.aiParam0);
          assertNull(f.casting());
          assertEquals(0f, f.world.getMapper(com.riiablo.engine.server.component.Velocity.class)
              .get(f.actor).modeSpeedBonusMultiplier);
          assertEquals(Seed.rollRandomNumber(seed) & 0xffffffffL, ai.rollAiRandomNumber());
        }
      }
    }
  }
}
