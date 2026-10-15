package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import org.junit.jupiter.api.Test;

class TrappedSoulAiIntegrationTest extends RiiabloTest {
  private static final float FRAME = Animation.FRAME_DURATION;

  @Test void bothRealRowsSelectDedicatedAi() {
    int count = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"TrappedSoul".equals(mon.AI)) continue;
      count++;
      assertEquals("TrappedSoul", AI.findAI(12, mon.AI).getClass().getSimpleName());
    }
    assertEquals(2, count);
  }

  @Test void firstNearDecisionAwakensOnceAcrossRowsAndDifficulties() {
    for (String row : new String[] {"trappedsoul1", "trappedsoul2"}) {
      for (int difficulty = 0; difficulty < 3; difficulty++) {
        try (Fixture f = new Fixture(row, difficulty, 1)) {
          f.ai.update(FRAME);
          assertEquals(Engine.Monster.MODE_S2, f.sequence().mode1);
          assertEquals(Engine.Monster.MODE_S1, f.sequence().mode2,
              "native TrappedSoul end-animation callback returns to S1");
          assertNull(f.casting(), "S2 is a mode change, not an injected skill");
          assertTrue(f.world.getMapper(NativeUnitFlags.class).get(f.actor)
              .has(NativeUnitFlags.NO_TREASURE_CLASS));
          f.sequence().started = true;
          f.ai.update(10);
          assertTrue(f.sequence().started);
          f.finishAction();
          f.ai.update(FRAME);
          assertEquals(Engine.Monster.MODE_A2, f.sequence().mode1);
          assertNotNull(f.casting());
        }
      }
    }
  }

  @Test void integerQuadrantsAndEqualitySelectNativeModes() {
    int[][] cases = {{9,11,Engine.Monster.MODE_A1}, {9,9,Engine.Monster.MODE_S1},
        {10,9,Engine.Monster.MODE_A2}, {11,11,Engine.Monster.MODE_S1},
        {11,9,Engine.Monster.MODE_A2}, {10,11,Engine.Monster.MODE_A1},
        {10,10,Engine.Monster.MODE_A1}};
    for (int[] c : cases) {
      try (Fixture f = new Fixture("trappedsoul1", 0, 1)) {
        f.ai.update(FRAME);
        f.finishAction();
        // Integer native coordinates, rather than fractional quadrant tests.
        f.world.getMapper(Position.class).get(f.actor).position.set(10.75f, 10.25f);
        f.targetPosition().set(c[0] + .1f, c[1] + .8f);
        f.ai.update(FRAME);
        assertEquals(c[2], f.sequence().mode1, "target=" + c[0] + "," + c[1]);
        assertEquals(Engine.Monster.MODE_S1, f.sequence().mode2);
        assertEquals(c[2] != Engine.Monster.MODE_S1, f.casting() != null);
      }
    }
  }

  @Test void gateUsesStrictGreaterThanAndThirtyFiveFrameAttackDeadline() {
    try (Fixture f = new Fixture("trappedsoul1", 0, 1)) {
      f.ai.update(FRAME);
      f.finishAction();
      f.ai.update(0);
      assertEquals(Engine.Monster.MODE_S1, f.sequence().mode1, "same awakening frame");
      f.finishAction();
      f.ai.update(FRAME);
      assertEquals(Engine.Monster.MODE_A2, f.sequence().mode1);
      f.finishAction();
      f.ai.update(35 * FRAME);
      assertEquals(Engine.Monster.MODE_S1, f.sequence().mode1, "deadline equality");
      f.finishAction();
      f.ai.update(FRAME);
      assertEquals(Engine.Monster.MODE_A2, f.sequence().mode1);
    }
  }

  @Test void skillOneQuadrantAdvancesFiveFrameGate() {
    try (Fixture f = new Fixture("trappedsoul1", 0, 1)) {
      f.ai.update(FRAME);
      f.finishAction();
      f.targetPosition().set(9,9);
      f.ai.update(FRAME);
      assertEquals(Engine.Monster.MODE_S1, f.sequence().mode1);
      f.finishAction();
      f.targetPosition().set(11,10);
      f.ai.update(5 * FRAME);
      assertEquals(Engine.Monster.MODE_S1, f.sequence().mode1);
      f.finishAction();
      f.ai.update(FRAME);
      assertEquals(Engine.Monster.MODE_A2, f.sequence().mode1);
    }
  }

  @Test void inactiveWaitsFifteenFramesAndFiveDistanceDoesNotAwaken() {
    for (int distance : new int[] {5, 1000}) {
      try (Fixture f = new Fixture("trappedsoul1", 0, distance)) {
        f.ai.update(FRAME);
        assertNull(f.sequence());
        assertTrue(f.world.getMapper(NativeUnitFlags.class).get(f.actor)
            .has(NativeUnitFlags.NO_TREASURE_CLASS));
        f.targetPosition().set(11,10);
        f.ai.update(14 * FRAME);
        assertNull(f.sequence());
        f.ai.update(FRAME + .00001f);
        assertEquals(Engine.Monster.MODE_S2, f.sequence().mode1);
      }
    }
  }

  @Test void awakenedFarAndMissingTargetUseS1AndWarpDoesNotRearm() {
    try (Fixture f = new Fixture("trappedsoul1", 0, 1)) {
      f.ai.update(FRAME);
      f.finishAction();
      for (int distance : new int[] {7,1000}) {
        f.targetPosition().set(10 + distance,10);
        f.ai.update(FRAME);
        assertEquals(Engine.Monster.MODE_S1, f.sequence().mode1);
        assertNull(f.casting());
        f.finishAction();
      }
      f.ai.onOwnerWarp();
      f.targetPosition().set(11,10);
      f.ai.update(FRAME);
      assertEquals(Engine.Monster.MODE_A2, f.sequence().mode1);
      f.ai.kill();
      f.ai.update(100);
      assertEquals(Engine.Monster.MODE_DT, f.sequence().mode1);
      assertNull(f.casting());
    }
  }
}
