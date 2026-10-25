package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;

class TrapMissileAiIntegrationTest extends RiiabloTest {
  @Test void checkpointPreservesInterShotDelayAndSpentBudgetAcrossWorlds() {
    try (Fixture source = new Fixture(row(), 0, 10); Fixture target = new Fixture(row(), 0, 10)) {
      configure(source, 2);
      Riiablo.engine = source.world;
      source.ai.update(Animation.FRAME_DURATION); assertNotNull(source.casting());
      source.finishAction(); // Action checkpointing remains a separate boundary.
      com.riiablo.engine.replay.MonsterRuntimeCheckpoint.capture(source.world, source.actor,
          java.util.Collections.singleton(source.actor)).restore(target.world, target.actor);
      AiRuntimeState.capture(source.ai, source.actor).restore(target.ai, target.actor);
      for (Fixture f : new Fixture[] {source, target}) {
        Riiablo.engine = f.world;
        f.ai.update(Animation.FRAME_DURATION); assertNull(f.casting());
        f.ai.update(16 * Animation.FRAME_DURATION); assertNull(f.casting());
        f.ai.update(Animation.FRAME_DURATION + .00001f); assertNotNull(f.casting());
        f.finishAction(); f.ai.update(Animation.FRAME_DURATION); assertExpired(f);
      }
      assertArrayEquals(AiRuntimeState.capture(source.ai, source.actor).bytes(), AiRuntimeState.capture(target.ai, target.actor).bytes());
    }
  }

  private String row() { return "trap-firebolt"; }

  private void configure(Fixture f, int shots) {
    f.ai.params[0] = 20;
    f.ai.params[1] = shots;
    f.ai.params[2] = 17;
  }

  private void assertExpired(Fixture f) {
    assertEquals("DEAD", f.ai.getState());
    assertNull(f.casting());
    assertEquals(Engine.Monster.MODE_DT, f.sequence().mode1);
    assertEquals(Engine.Monster.MODE_DD, f.sequence().mode2);
    assertTrue(f.world.getMapper(NativeUnitFlags.class).get(f.actor)
        .has(NativeUnitFlags.NO_TREASURE_CLASS));
  }

  @Test void bothRealRowsResolveAndKeepNativeProjectileData() {
    int count = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"Trap-Missile".equals(mon.AI)) continue;
      assertEquals(TrapMissile.class, AI.findAI(12, mon.AI).getClass());
      assertNotNull(mon.MissA1);
      assertFalse(mon.MissA1.isEmpty());
      count++;
    }
    assertEquals(2, count);
  }

  @Test void shootsWaitsAndExpiresAfterLastShotInEveryDifficulty() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture(row(), difficulty, 10)) {
        configure(f, 2);
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(SkillCodes.attack, f.casting().skillId);
        assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
        f.sequence().started = true;
        f.ai.update(10);
        assertTrue(f.sequence().started);
        f.finishAction();
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals("IDLE", f.ai.getState());
        assertNull(f.casting());
        f.ai.update(16 * Animation.FRAME_DURATION);
        assertNull(f.casting());
        f.ai.update(Animation.FRAME_DURATION + .00001f);
        assertNotNull(f.casting());
        f.finishAction();
        f.ai.update(Animation.FRAME_DURATION);
        assertExpired(f);
        f.sequence().started = true;
        f.ai.update(100);
        assertTrue(f.sequence().started);
      }
    }
  }

  @Test void rangeBoundaryIsInclusiveAndDoesNotRequireMelee() {
    try (Fixture f = new Fixture(row(), 0, 10)) {
      configure(f, 2);
      ((TrapMissile) f.ai).think(f.target, 20, false);
      assertNotNull(f.casting());
    }
    try (Fixture f = new Fixture(row(), 0, 10)) {
      configure(f, 2);
      ((TrapMissile) f.ai).think(f.target, 20.01f, false);
      assertExpired(f);
    }
  }

  @Test void missingTargetExpiresRatherThanWaiting() {
    try (Fixture f = new Fixture(row(), 0, 10)) {
      configure(f, 2);
      f.targetPosition().set(1000, 1000);
      f.ai.update(Animation.FRAME_DURATION);
      assertExpired(f);
    }
  }

  @Test void zeroShotsExpiresBeforeFirstAttack() {
    try (Fixture f = new Fixture(row(), 0, 10)) {
      configure(f, 0);
      f.ai.update(Animation.FRAME_DURATION);
      assertExpired(f);
    }
  }

  @Test void targetLeavingRangeExpiresBeforeInterShotDelay() {
    try (Fixture f = new Fixture(row(), 0, 10)) {
      configure(f, 3);
      f.ai.update(Animation.FRAME_DURATION);
      assertNotNull(f.casting());
      f.finishAction();
      f.ai.params[0] = 1;
      f.ai.update(Animation.FRAME_DURATION);
      assertExpired(f);
    }
  }
}
