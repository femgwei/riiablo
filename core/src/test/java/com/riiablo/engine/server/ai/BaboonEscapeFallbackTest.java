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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaboonEscapeFallbackTest extends RiiabloTest {
  @Test void rejectedEscapeWalksAroundSelfUsingNativeSeedDraws() { verifyFallback(true); }
  @Test void rejectedLocalWalkDoesNotRetryOrChasePlayer() { verifyFallback(false); }

  @Test void acceptedEscapeRetainsConfiguredVelocityBonus() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("baboon1", difficulty, 10)) {
        Baboon ai = (Baboon) f.ai;
        f.health(50, 100);
        ai.aiParam0 = 4;
        int expectedBonus = Math.min(
            100 * ai.monster.monstats.Run / ai.monster.monstats.Velocity - 100, 120);
        assertTrue(expectedBonus > 0);
        ai.update(0.04f);
        assertEquals("ESCAPE", ai.getState());
        assertEquals(expectedBonus / 100f,
            f.world.getMapper(com.riiablo.engine.server.component.Velocity.class)
                .get(f.actor).modeSpeedBonusMultiplier);
      }
    }
  }

  private void verifyFallback(boolean acceptLocalWalk) {
    for (int seedValue : new int[] {0, 1, 2, 123456}) {
      try (Fixture f = new Fixture("baboon1", 0, 10)) {
        Baboon ai = (Baboon) f.ai;
        f.health(50, 100);
        ai.aiParam0 = 4;
        assertTrue(ai.monster.monstats.Run > ai.monster.monstats.Velocity,
            "Fixture must exercise a nonzero escape bonus");
        Riiablo.gameSeed = seedValue;
        D2Seed seed = new D2Seed(seedValue ^ (f.actor * 0x45D9F3B), 666);
        boolean axis = (Seed.rollRandomNumber(seed) & 1L) != 0;
        int random = (int) ((Seed.rollRandomNumber(seed) & 0xffffffffL) % 5);
        int x = axis ? 5 : random, y = axis ? random : 5;
        if ((Seed.rollRandomNumber(seed) & 1L) != 0) x = -x;
        if ((Seed.rollRandomNumber(seed) & 1L) != 0) y = -y;
        Vector2 expected = new Vector2(10 + x, 10 + y);
        List<Vector2> requests = new ArrayList<>();
        when(f.paths.findPath(anyInt(), any(), anyBoolean(), anyInt())).thenAnswer(call -> {
          requests.add(new Vector2((Vector2) call.getArgument(1)));
          assertEquals(Engine.INVALID_ENTITY, (int) call.getArgument(3));
          return requests.size() > 1 && acceptLocalWalk;
        });
        ai.update(0.04f);
        assertEquals(2, requests.size());
        assertEquals(expected, requests.get(1));
        assertEquals(acceptLocalWalk ? "APPROACH" : "IDLE", ai.getState());
        assertEquals(ai.SLEEP, ai.time);
        assertNull(f.casting());
        assertEquals(0f, f.world.getMapper(com.riiablo.engine.server.component.Velocity.class)
            .get(f.actor).modeSpeedBonusMultiplier);
        assertEquals(Seed.rollRandomNumber(seed) & 0xffffffffL, ai.rollAiRandomNumber());
      }
    }
  }
}
