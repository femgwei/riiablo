package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.riiablo.engine.server.ai.LegacyFsmCheckpointIntegrationTest.fixture;

import com.artemis.Aspect;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.engine.replay.UnitStateRuntimeCheckpoint;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.skill.SkillCodes;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Existing host decision phases; external MathUtils RNG is deliberately not part of the image. */
class LegacyPhaseCheckpointIntegrationTest extends RiiabloTest {
  @Test void larvaConsumesRestoredRecoveryBeforeDrawingAnyGlobalRandomness() throws Exception {
    AiRuntimeState image;
    try (Fixture f = fixture("MaggotLarva", 1)) {
      MaggotLarva ai = (MaggotLarva) f.ai;
      ai.aiParam0 = 1; ai.time = .2f; ai.params[0] = 100; ai.params[1] = 12;
      ai.stateMachine.changeState(MaggotLarva.State.ATTACK);
      image = AiRuntimeState.capture(ai, 0);
    }
    java.util.Random previous = MathUtils.random; RandomXS128 external = mock(RandomXS128.class);
    try (Fixture f = fixture("MaggotLarva", 1)) {
      MathUtils.random = external;
      image.restore(f.ai, 0); MaggotLarva ai = (MaggotLarva) f.ai;
      clearInvocations(f.paths);
      ai.update(.1f); assertEquals(1, ai.aiParam0); assertNull(f.casting());
      ai.update(.15f);
      assertEquals(0, ai.aiParam0); assertEquals("IDLE", ai.getState());
      assertEquals(12 * Animation.FRAME_DURATION, ai.time); assertNull(f.casting());
      verifyNoInteractions(external); verifyNoInteractions(f.paths);
      ai.update(ai.time + .01f);
      assertEquals(1, ai.aiParam0); assertEquals("ATTACK", ai.getState());
      assertEquals(SkillCodes.attack, f.casting().skillId);
    } finally { MathUtils.random = previous; }
  }

  @Test void fetishRetainsTheLoopBoundaryAndEscapesOnTheNextDecision() throws Exception {
    AiRuntimeState image;
    try (Fixture f = fixture("Fetish", 1)) {
      Fetish ai = (Fetish) f.ai;
      ai.aiParam0 = 1; ai.aiParam1 = 3; ai.time = .2f;
      ai.params[2] = 3; ai.params[3] = 50;
      ai.stateMachine.changeState(Fetish.State.ATTACK);
      image = AiRuntimeState.capture(ai, 0);
    }
    java.util.Random previous = MathUtils.random;
    try (Fixture f = fixture("Fetish", 1)) {
      MathUtils.random = mock(RandomXS128.class);
      image.restore(f.ai, 0); Fetish ai = (Fetish) f.ai;
      ai.update(.1f); assertEquals(3, ai.aiParam1);
      ai.update(.15f);
      assertEquals(2, ai.aiParam0); assertEquals(0, ai.aiParam1); assertEquals("ESCAPE", ai.getState());
      assertNull(f.casting()); assertNull(f.sequence());
      verify(f.paths).findPath(eq(0), any(), eq(false), eq(-1));
    } finally { MathUtils.random = previous; }
  }

  @Test void sandMaggotRestoresReadyToLayWithoutResettingOrDoubleConsumingTheEggBudget() throws Exception {
    Map<Integer, UnitStateRuntimeCheckpoint> image;
    try (Fixture f = fixture("SandMaggot", 8)) {
      SandMaggot ai = (SandMaggot) f.ai;
      ai.aiParam0 = 2; ai.aiParam1 = 37; ai.aiParam2 = 2; ai.time = .2f;
      ai.params = new int[] {100, 0, 3, 0, 37, 0, 0, 0};
      ai.stateMachine.changeState(SandMaggot.State.APPROACH);
      image = UnitStateRuntimeCheckpoint.capture(f.world, new int[] {0, 1});
    }
    java.util.Random previous = MathUtils.random; RandomXS128 external = mock(RandomXS128.class);
    when(external.nextFloat()).thenReturn(.99f); // No circle/spit/melee; lay succeeds.
    try (Fixture f = fixture("SandMaggot", 8)) {
      MathUtils.random = external;
      UnitStateRuntimeCheckpoint.restore(f.world, image); SandMaggot ai = (SandMaggot) f.ai;
      assertEquals(0, f.probe.starts);
      ai.update(.1f); assertEquals(2, ai.aiParam2); assertNull(f.casting());
      ai.update(.15f);
      assertEquals("LAY_EGG", ai.getState()); assertEquals(1, ai.aiParam0);
      assertEquals(37, ai.aiParam1); assertEquals(3, ai.aiParam2);
      assertEquals(Riiablo.files.skills.get(ai.monster.monstats.Skill3).Id, f.casting().skillId);
      assertEquals(1, f.probe.starts);
      f.finishAction(); ai.update(.1f);
      assertEquals(3, ai.aiParam2); assertNull(f.casting());
      ai.update(ai.time + .01f);
      assertEquals("IDLE", ai.getState()); assertEquals(3, ai.aiParam2);
      assertEquals(1, f.probe.starts); assertNull(f.casting());
    } finally { MathUtils.random = previous; }
  }

  @Test void sandMaggotBurrowMemoryWaitsForARealTargetThenRequestsTheConfiguredUnburrow() throws Exception {
    AiRuntimeState image;
    try (Fixture f = fixture("SandMaggot", 30)) {
      SandMaggot ai = (SandMaggot) f.ai;
      ai.aiParam0 = 3; ai.aiParam1 = 47; ai.aiParam2 = 2; ai.time = .2f;
      ai.params[4] = 47; ai.stateMachine.changeState(SandMaggot.State.BURROW);
      image = AiRuntimeState.capture(ai, 0);
    }
    java.util.Random previous = MathUtils.random; RandomXS128 external = mock(RandomXS128.class);
    try (Fixture f = fixture("SandMaggot", 30)) {
      MathUtils.random = external; image.restore(f.ai, 0); SandMaggot ai = (SandMaggot) f.ai;
      ai.update(.3f);
      assertEquals(3, ai.aiParam0); assertEquals(47, ai.aiParam1); assertEquals(2, ai.aiParam2);
      assertEquals("BURROW", ai.getState()); assertNull(f.casting());
      f.targetPosition().set(18, 10); ai.update(ai.time + .01f);
      assertEquals("UNBURROW", ai.getState()); assertEquals(1, ai.aiParam0);
      assertEquals(47, ai.aiParam1); assertEquals(2, ai.aiParam2); f.assertSkill(0);
      assertEquals(1, f.probe.starts); verifyNoInteractions(external);
    } finally { MathUtils.random = previous; }
  }

  @Test void pantherPackAndEnemySubscriptionsStayBoundToTheDestinationWorld() throws Exception {
    AiRuntimeState image; Object sourcePack;
    try (Fixture f = fixture("PantherWoman", 30)) {
      image = AiRuntimeState.capture(f.ai, 0);
      sourcePack = AiRuntimeStateTest.get(f.ai, PantherWoman.class, "monsterEntities");
    }
    try (Fixture f = fixture("PantherWoman", 30)) {
      Object pack = AiRuntimeStateTest.get(f.ai, PantherWoman.class, "monsterEntities");
      Object enemies = AiRuntimeStateTest.get(f.ai, PantherWoman.class, "enemyEntities");
      image.restore(f.ai, 0);
      assertNotSame(sourcePack, pack);
      assertSame(pack, AiRuntimeStateTest.get(f.ai, PantherWoman.class, "monsterEntities"));
      assertSame(enemies, AiRuntimeStateTest.get(f.ai, PantherWoman.class, "enemyEntities"));
      assertSame(f.world.getAspectSubscriptionManager().get(Aspect.all(
          com.riiablo.engine.server.component.Class.class, Monster.class, Position.class)), pack);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"Mummy", "PinHead", "Wraith"})
  void simpleMeleeMachinesResumeOnlyAfterTheirSavedDelayWithExplicitExternalRng(String type) throws Exception {
    AiRuntimeState image;
    try (Fixture f = fixture(type, 1)) {
      AiRuntimeStateTest.put(f.ai, f.ai.getClass(), "time", .2f);
      java.util.Arrays.fill(f.ai.params, 100);
      if (f.ai instanceof PinHead) f.ai.params[PinHead.PARAM_SMITE_CHANCE] = 0;
      image = AiRuntimeState.capture(f.ai, 0);
    }
    java.util.Random previous = MathUtils.random;
    try (Fixture f = fixture(type, 1)) {
      MathUtils.random = mock(RandomXS128.class);
      image.restore(f.ai, 0); f.ai.update(.1f); assertNull(f.casting());
      f.ai.update(.15f); assertEquals("ATTACK", f.ai.getState());
      assertEquals(SkillCodes.attack, f.casting().skillId);
    } finally { MathUtils.random = previous; }
  }
}
