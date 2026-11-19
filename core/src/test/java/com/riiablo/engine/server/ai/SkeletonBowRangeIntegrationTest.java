package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static com.riiablo.engine.server.ai.LegacyFsmCheckpointIntegrationTest.fixture;

import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class SkeletonBowRangeIntegrationTest extends RiiabloTest {
  @ParameterizedTest
  @CsvSource({"3063969912,622133184,2445778925,1537150721,false",
      "2445778925,1537150721,2810212450,1020115726,true"})
  void recordedOriginalFollowingDecisionsReverseOrbitThenShoot(
      long low,int high,long expectedLow,int expectedHigh,boolean shoots) {
    try (Fixture f = fixture("SkeletonBow",18)) {
      SkeletonBow ai = (SkeletonBow) f.ai;
      ai.params = new int[] {75,15,0,5,0,0,0,0};
      com.riiablo.engine.server.component.Monster monster = f.world.getMapper(
          com.riiablo.engine.server.component.Monster.class).get(f.actor);
      monster.nativeSeed = new com.d2moo.common.drlg.D2Seed((int)low,high);
      monster.rngState = (int)low;
      org.mockito.Mockito.clearInvocations(f.paths);
      ai.update(.04f);
      if (shoots) assertNotNull(f.casting());
      else {
        assertNull(f.casting());
        org.mockito.Mockito.verify(f.paths).findNativeOrbit(f.actor,f.target,3,false);
      }
      assertEquals(expectedLow,Integer.toUnsignedLong(monster.nativeSeed.getNLowSeed()));
      assertEquals(expectedHigh,monster.nativeSeed.getNHighSeed());
    }
  }

  @Test void recordedOriginalStep89ChoosesOrbitAndPreservesBothSeedWords() {
    // 1.10F far-archer frames.csv, run 20261118-165751-85800355,
    // step 88 -> 89: neutral -> walk, three draws, no forced action.
    try (Fixture f = fixture("SkeletonBow", 18)) {
      SkeletonBow ai = (SkeletonBow) f.ai;
      ai.params = new int[] {75,15,0,5,0,0,0,0};
      com.riiablo.engine.server.component.Monster monster = f.world.getMapper(
          com.riiablo.engine.server.component.Monster.class).get(f.actor);
      monster.nativeSeed = new com.d2moo.common.drlg.D2Seed(423202256,1109836705);
      monster.rngState = 423202256;
      org.mockito.Mockito.clearInvocations(f.paths);
      ai.update(.04f);
      assertNull(f.casting(), "64-bit remainder incorrectly selects a shot (65 < 75)");
      org.mockito.Mockito.verify(f.paths).findNativeOrbit(f.actor,f.target,3,true);
      assertEquals(3063969912L,Integer.toUnsignedLong(monster.nativeSeed.getNLowSeed()));
      assertEquals(622133184,monster.nativeSeed.getNHighSeed());
    }
  }

  @Test void circleUsesSeedChosenOrbitInsteadOfDirectPursuit() {
    try (Fixture f = fixture("SkeletonBow", 18)) {
      SkeletonBow ai = (SkeletonBow) f.ai;
      ai.params = new int[] {0,15,0,5,0,0,0,0};
      int seed=1;
      com.d2moo.common.drlg.D2Seed expected=null;
      for (;seed<10000;seed++) {
        expected=new com.d2moo.common.drlg.D2Seed(seed,666);
        com.d2moo.common.seed.Seed.rollPercentage(expected);
        if(com.d2moo.common.seed.Seed.rollPercentage(expected)<20) break;
      }
      assertTrue(seed<10000);
      boolean clockwise=(com.d2moo.common.seed.Seed.rollRandomNumber(expected)&255L)<128;
      com.riiablo.engine.server.component.Monster monster=f.world.getMapper(
          com.riiablo.engine.server.component.Monster.class).get(f.actor);
      monster.nativeSeed=new com.d2moo.common.drlg.D2Seed(seed,666);monster.rngState=seed;
      org.mockito.Mockito.clearInvocations(f.paths);
      ai.update(.04f);
      org.mockito.Mockito.verify(f.paths).findNativeOrbit(f.actor,f.target,3,clockwise);
      assertEquals(expected.getNLowSeed(),monster.nativeSeed.getNLowSeed());
      assertEquals(expected.getNHighSeed(),monster.nativeSeed.getNHighSeed());
    }
  }

  @ParameterizedTest
  @CsvSource({"8,15", "40,5"})
  void approachUsesBoundedStepAndDesiredRadius(int radius,int destinationX) {
    try(Fixture f=fixture("SkeletonBow",30)) {
      SkeletonBow ai=(SkeletonBow)f.ai;
      ai.params=new int[]{0,15,100,5,radius,0,0,0};
      org.mockito.Mockito.clearInvocations(f.paths);
      ai.update(.04f);
      org.mockito.Mockito.verify(f.paths).findPath(f.actor,
          new com.badlogic.gdx.math.Vector2(destinationX,10),false,com.riiablo.engine.Engine.INVALID_ENTITY);
    }
  }

  @Test void failedFarApproachUsesTwentyFrameIdleRatherThanShootStall() {
    try(Fixture f=fixture("SkeletonBow",30)) {
      SkeletonBow ai=(SkeletonBow)f.ai;
      ai.params=new int[]{0,7,0,5,0,0,0,0};
      ai.update(.04f);
      assertEquals(20*com.riiablo.codec.Animation.FRAME_DURATION,ai.time,.00001f);
      assertNull(f.casting());
    }
  }

  @ParameterizedTest
  @CsvSource({"0,15", "15,15", "8,8"})
  void completedShotUsesNeutralAiDelayInsteadOfRandomSeconds(int aiDelay, int expectedFrames) {
    try (Fixture f = fixture("SkeletonBow", 18)) {
      SkeletonBow ai = (SkeletonBow) f.ai;
      ai.params = new int[] {100,15,0,5,0,0,0,0};
      ai.SLEEP = aiDelay * com.riiablo.codec.Animation.FRAME_DURATION;
      ai.update(.04f);
      assertNotNull(f.casting());
      float expected = expectedFrames * com.riiablo.codec.Animation.FRAME_DURATION;
      assertEquals(expected, ai.time, .00001f);
      ai.update(10f);
      assertEquals(expected, ai.time, .00001f, "live attack must not consume post-animation delay");
      f.finishAction();
      ai.update(expected - .001f);
      assertNull(f.casting());
      ai.update(.0011f);
      assertNotNull(f.casting(), "next decision must become eligible at the neutral delay boundary");
    }
  }

  @Test void successfulOrdinaryShotDoesNotConsumeAnExtraRandomCooldownDraw() {
    try (Fixture f = fixture("SkeletonBow", 18)) {
      SkeletonBow ai = (SkeletonBow) f.ai;
      ai.params = new int[] {75,15,0,5,0,0,0,0};
      // Recorded native archer spawn seed; only this decision is exercised,
      // not production mode entry, animation callbacks or missile settlement.
      com.d2moo.common.drlg.D2Seed expected = new com.d2moo.common.drlg.D2Seed(1550559079,1258811200);
      assertTrue(com.d2moo.common.seed.Seed.rollPercentage(expected) < 75);
      com.riiablo.engine.server.component.Monster monster = f.world.getMapper(
          com.riiablo.engine.server.component.Monster.class).get(f.actor);
      monster.nativeSeed = new com.d2moo.common.drlg.D2Seed(1550559079,1258811200);
      monster.rngState = 1550559079;
      ai.update(.04f);
      assertNotNull(f.casting());
      assertEquals(expected.getNLowSeed(), monster.nativeSeed.getNLowSeed());
      assertEquals(expected.getNHighSeed(), monster.nativeSeed.getNHighSeed());
    }
  }

  @Test void zeroShootChanceCannotBeBypassedByEnteringRange() {
    try (Fixture f = fixture("SkeletonBow", 18)) {
      SkeletonBow ai = (SkeletonBow) f.ai;
      ai.params = new int[] {0,15,0,5,0,0,0,0};
      ai.update(.04f);
      assertNull(f.casting(), "ordinary target acquisition must honor shoot chance");
    }
  }

  @Test void targetInRangeCannotBypassOutstandingDecisionDelay() {
    try (Fixture f = fixture("SkeletonBow", 18)) {
      SkeletonBow ai = (SkeletonBow) f.ai;
      ai.params = new int[] {100,15,0,5,0,0,0,0};
      ai.time = .5f;
      ai.update(.04f);
      assertNull(f.casting(), "range acquisition must not cancel the decision delay");
      ai.update(.47f);
      assertNotNull(f.casting());
    }
  }

  @Test void activeArrowActionCannotBeReplacedAfterDecisionDelayExpires() {
    try (Fixture f = fixture("SkeletonBow", 18)) {
      SkeletonBow ai = (SkeletonBow) f.ai;
      ai.params = new int[] {100,15,0,5,0,0,0,0};
      ai.update(.04f);
      assertNotNull(f.casting());
      long action = f.casting().actionId;
      Object sequence = f.sequence();
      ai.update(10f);
      assertEquals(action, f.casting().actionId, "do not restart a live attack");
      assertSame(sequence, f.sequence());
    }
  }

  @ParameterizedTest
  @CsvSource({"18,0,true", "18,5,true", "20,40,false", "21,40,false"})
  void pursuitRadiusDoesNotReplaceTwentySubtileShootingBoundary(
      int distance, int pursuitRadius, boolean shoots) throws Exception {
    try (Fixture f = fixture("SkeletonBow", distance)) {
      SkeletonBow ai = (SkeletonBow) f.ai;
      ai.params = new int[] {100,15,0,5,pursuitRadius,0,0,0};
      ai.update(.04f);
      if (shoots) {
        assertNotNull(f.casting(), "clear target within 20 must be eligible to shoot");
        assertEquals(f.target, f.casting().targetId);
      } else {
        assertNull(f.casting(), "pursuit radius must not expand shooting range");
      }
    }
  }
}
