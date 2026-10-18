package com.riiablo.server.d2gs;

import org.junit.Test;
import static org.junit.Assert.*;

public class MonsterActionTimingTest {
  @Test public void castingReuseAssignsANewIdentityEvenForTheSameSkillAndTarget() {
    com.riiablo.engine.server.component.Casting cast = new com.riiablo.engine.server.component.Casting();
    cast.set(0, 10, com.badlogic.gdx.math.Vector2.Zero);
    long first = cast.actionId;
    cast.set(0, 10, com.badlogic.gdx.math.Vector2.Zero);
    assertTrue(first > 0);
    assertNotEquals(first, cast.actionId);
  }
  @Test public void missesAndRepeatedPulsesKeepFirstExecutionLatency() {
    MonsterActionTiming timing = new MonsterActionTiming();
    Object cast = new Object(), seq = new Object();
    timing.observe(4, 1, true, 0, 10, 4, 10);
    timing.observe(5, 1, true, 0, 10, 4, 10);
    timing.melee(9, false);
    timing.execute(9, 0); timing.execute(11, 0);
    timing.observe(12, 0, false, -1, -1, -1, 10);
    assertEquals(1, timing.actions.size());
    assertEquals(5, timing.actions.get(0).executionTick - timing.actions.get(0).decisionTick);
    assertEquals("EXECUTED", timing.actions.get(0).outcome);
    assertEquals(9, timing.actions.get(0).meleeAttemptTick);
    assertEquals(1, timing.actions.get(0).meleeMisses);
  }

  @Test public void pooledComponentsReusedForANewSequenceAreSeparateDecisions() {
    MonsterActionTiming timing = new MonsterActionTiming();
    Object cast = new Object(), seq = new Object();
    timing.observe(1, 1, true, 0, 10, 4, 10);
    timing.observe(2, 1, true, 0, 10, 4, 10);
    timing.observe(8, 2, true, 0, 10, 4, 10);
    assertEquals("REPLACED_WITHOUT_EXECUTION", timing.actions.get(0).outcome);
    timing.close(10);
    assertEquals(2, timing.actions.size());
    assertEquals("CENSORED_AT_TEST_END", timing.actions.get(1).outcome);
    assertEquals(8, timing.actions.get(1).decisionTick);
  }

  @Test public void unknownExecutionIsNotInventedAsZeroLatencyAttack() {
    MonsterActionTiming timing = new MonsterActionTiming();
    timing.execute(5, 0);
    assertEquals(1, timing.unmatchedExecutions);
    assertTrue(timing.actions.isEmpty());
  }

  @Test public void neutralSequenceWithStaleCastingIsNotAnAttackDecision() {
    MonsterActionTiming timing = new MonsterActionTiming();
    timing.observe(1, 1, true, 0, 10, 1, 10);
    assertTrue(timing.actions.isEmpty());
  }

  @Test public void chargeModeChangesDoNotResetTheOriginalDecisionClock() {
    MonsterActionTiming timing = new MonsterActionTiming();
    timing.observe(1, 1, true, 352, 10, 2, 10);
    timing.observe(20, 1, true, 352, 10, 5, 10);
    timing.execute(24, 352);
    assertEquals(1, timing.actions.size());
    assertEquals(23, timing.actions.get(0).executionTick - timing.actions.get(0).decisionTick);
  }

  @Test public void executionThatRemovesItsOwnCastingStillPairsToThatAction() {
    MonsterActionTiming timing = new MonsterActionTiming();
    timing.observe(1, 1, true, 159, 10, 0, 10);
    timing.observe(10, 0, false, -1, -1, -1, 10);
    timing.execute(10, 159);
    assertEquals("EXECUTED", timing.actions.get(0).outcome);
    assertEquals(0, timing.unmatchedExecutions);
  }

  @Test public void missileCreationIsEvidenceWithoutWaitingForImpactOrDamage() {
    MonsterActionTiming timing = new MonsterActionTiming();
    timing.observe(1, 1, true, 0, 10, 4, 10);
    timing.execute(5, 0);
    timing.projectile(5, -1);
    timing.projectile(50, -1); // A later child must not be attributed to this old cast.
    assertEquals(5, timing.actions.get(0).projectileTick);
    assertEquals(1, timing.actions.get(0).projectiles);
  }
}
