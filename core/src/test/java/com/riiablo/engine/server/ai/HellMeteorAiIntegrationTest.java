package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import org.junit.jupiter.api.Test;

class HellMeteorAiIntegrationTest extends RiiabloTest {
  private String row() {
    for (MonStats.Entry mon : Riiablo.files.monstats)
      if ("HellMeteor".equals(mon.AI)) return mon.Id;
    throw new AssertionError("Missing HellMeteor row");
  }

  @Test void realRowsSelectDedicatedAi() {
    int count = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"HellMeteor".equals(mon.AI)) continue;
      count++;
      assertEquals("HellMeteor", AI.findAI(12, mon.AI).getClass().getSimpleName());
    }
    assertTrue(count > 0);
  }

  @Test void integerSelfCoordinatesUseTwoOrderedUnsignedLimitedRollsWithoutTarget() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      for (int range : new int[] {0, 1, 7, 16}) {
        try (Fixture f = new Fixture(row(), difficulty, 1000)) {
          f.ai.params[0] = 100;
          f.ai.params[2] = range;
          f.world.getMapper(Position.class).get(f.actor).position.set(10.75f, 12.25f);
          D2Seed seed = new D2Seed(Riiablo.gameSeed ^ (f.actor * 0x45D9F3B), 666);
          for (int cast = 0; cast < 4; cast++) {
            Seed.rollPercentage(seed);
            int x = 10 + Seed.rollLimitedRandomNumber(seed, 2 * range) - range;
            int y = 12 + Seed.rollLimitedRandomNumber(seed, 2 * range) - range;
            f.ai.update(Animation.FRAME_DURATION);
            assertNotNull(f.casting());
            assertEquals(Engine.INVALID_ENTITY, f.casting().targetId);
            assertEquals(x, f.casting().targetVec.x);
            assertEquals(y, f.casting().targetVec.y);
            assertEquals(Riiablo.files.skills.get(Riiablo.files.monstats.get(row()).Skill1).Id,
                f.casting().skillId);
            f.finishAction();
          }
        }
      }
    }
  }

  @Test void targetPresenceDoesNotChangeCoordinatesAndBusyActionIsPreserved() {
    try (Fixture f = new Fixture(row(), 0, 1)) {
      f.ai.params[0] = 100;
      f.ai.params[2] = 0;
      f.ai.update(Animation.FRAME_DURATION);
      assertNotNull(f.casting());
      assertEquals(Engine.INVALID_ENTITY, f.casting().targetId);
      assertEquals(10, f.casting().targetVec.x);
      assertEquals(10, f.casting().targetVec.y);
      f.sequence().started = true;
      f.ai.update(10);
      assertTrue(f.sequence().started);
      f.ai.kill();
      f.ai.update(100);
      assertNull(f.casting());
      assertEquals(Engine.Monster.MODE_DT, f.sequence().mode1);
    }
  }

  @Test void failedChanceWaitsConfiguredFramesWithAndWithoutTarget() {
    for (int distance : new int[] {1, 1000}) {
      try (Fixture f = new Fixture(row(), 0, distance)) {
        f.ai.params[0] = 0;
        f.ai.params[1] = 17;
        f.ai.update(Animation.FRAME_DURATION);
        assertNull(f.casting());
        f.ai.params[0] = 100;
        f.ai.update(16 * Animation.FRAME_DURATION);
        assertNull(f.casting());
        f.ai.update(Animation.FRAME_DURATION + .00001f);
        assertNotNull(f.casting());
      }
    }
  }
}
