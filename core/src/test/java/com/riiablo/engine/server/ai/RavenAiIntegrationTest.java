package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.BaseSystem;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.skill.SkillId;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;

/** Deterministic Raven target/peck budget checks independent of client polling. */
class RavenAiIntegrationTest extends RiiabloTest {
  @Test
  void firstThinkWaitsTwentyFiveFramesForTheSummonSpawnPhase() throws Exception {
    try (Act1NativeAiIntegrationTest.Fixture f =
             new Act1NativeAiIntegrationTest.Fixture("druidhawk", 0, 8f)) {
      f.world.getMapper(SummonedPet.class).create(f.actor).set(f.target, "raven", SkillId.RAVEN, 1, false, 0);
      for (int i = 0; i < 24; i++) f.ai.update(1f / 25f);
      assertEquals(-1, AiRuntimeStateTest.get(f.ai, Raven.class, "pecks"));
      org.mockito.Mockito.verifyNoInteractions(f.paths);
      f.ai.update(1f / 25f);
      assertEquals(12, AiRuntimeStateTest.get(f.ai, Raven.class, "pecks"));
      assertEquals("CIRCLE", f.ai.getState());
    }
  }

  @Test
  void cooldownAdvancesDuringA1AndRequiresAFrameStrictlyAfterTheDeadline() throws Exception {
    try (Act1NativeAiIntegrationTest.Fixture f =
             new Act1NativeAiIntegrationTest.Fixture("druidhawk", 0, 1f)) {
      f.ally("fallen1", 100f, 10f, 10f);
      f.world.getMapper(SummonedPet.class).create(f.actor).set(f.target, "raven", SkillId.RAVEN, 1, false, 0);
      f.ai.params[3] = 100;
      AiRuntimeStateTest.put(f.ai, Raven.class, "nextDecisionFrame", 0L);
      AiRuntimeStateTest.put(f.ai, Raven.class, "attackDueFrame", 0L);
      f.ai.update(1f / 25f);
      assertNotNull(f.casting());
      assertEquals(51L, AiRuntimeStateTest.get(f.ai, Raven.class, "attackDueFrame"));
      f.ai.update(25f / 25f); // A1 stays active while the absolute clock advances.
      f.finishAction();
      f.ai.onActionReturnedToNeutral();
      f.ai.update(15f / 25f);
      assertNull(f.casting());
      AiRuntimeStateTest.put(f.ai, Raven.class, "nextDecisionFrame", 0L);
      f.ai.update(10f / 25f); // frame 51 equals the deadline: still ineligible.
      assertNull(f.casting());
      AiRuntimeStateTest.put(f.ai, Raven.class, "nextDecisionFrame", 0L);
      f.ai.update(1f / 25f);
      assertNotNull(f.casting());
      assertEquals(10, AiRuntimeStateTest.get(f.ai, Raven.class, "pecks"));
    }
  }

  @Test
  void noEnemyAndRejectedAttackChanceBothCircleAroundTheOwner() {
    for (boolean enemy : new boolean[] {false, true}) {
      try (Act1NativeAiIntegrationTest.Fixture f =
               new Act1NativeAiIntegrationTest.Fixture("druidhawk", 0, 8f)) {
        f.world.getMapper(SummonedPet.class).create(f.actor).set(f.target, "raven", SkillId.RAVEN, 1, false, 0);
        if (enemy) f.ally("fallen1", 100f, 10f, 10f);
        f.ai.params[3] = 0;
        f.ai.update(1f);
        assertNull(f.casting());
        assertEquals("CIRCLE", f.ai.getState());
        org.mockito.Mockito.verify(f.paths).findNativeOrbit(
            org.mockito.ArgumentMatchers.eq(f.actor), org.mockito.ArgumentMatchers.eq(f.target),
            org.mockito.ArgumentMatchers.eq(4), org.mockito.ArgumentMatchers.anyBoolean());
      }
    }
  }

  @Test
  void rejectedCircleFlipsDirectionAndRetriesBeforeFallingBack() {
    try (Act1NativeAiIntegrationTest.Fixture f =
             new Act1NativeAiIntegrationTest.Fixture("druidhawk", 0, 8f)) {
      f.world.getMapper(SummonedPet.class).create(f.actor).set(f.target, "raven", SkillId.RAVEN, 1, false, 0);
      org.mockito.Mockito.when(f.paths.findNativeOrbit(org.mockito.ArgumentMatchers.anyInt(),
          org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
          org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(false, true);
      f.ai.update(1f);
      org.mockito.ArgumentCaptor<Boolean> sides = org.mockito.ArgumentCaptor.forClass(Boolean.class);
      org.mockito.Mockito.verify(f.paths, org.mockito.Mockito.times(2)).findNativeOrbit(
          org.mockito.ArgumentMatchers.eq(f.actor), org.mockito.ArgumentMatchers.eq(f.target),
          org.mockito.ArgumentMatchers.eq(4), sides.capture());
      assertTrue(sides.getAllValues().get(0) != sides.getAllValues().get(1));
      assertEquals("CIRCLE", f.ai.getState());
    }
  }

  @Test
  void eligibleDistantEnemyUsesNativeChaseWithoutSpendingAPeck() throws Exception {
    try (Act1NativeAiIntegrationTest.Fixture f =
             new Act1NativeAiIntegrationTest.Fixture("druidhawk", 0, 1f)) {
      int enemy = f.ally("fallen1", 100f, 20f, 10f);
      f.world.getMapper(SummonedPet.class).create(f.actor).set(f.target, "raven", SkillId.RAVEN, 1, false, 0);
      f.ai.params[3] = 100;
      f.ai.update(1f);
      org.mockito.Mockito.verify(f.paths).findNativeChase(f.actor, enemy);
      assertNull(f.casting());
      assertEquals(12, AiRuntimeStateTest.get(f.ai, Raven.class, "pecks"));
      assertEquals("APPROACH", f.ai.getState());
    }
  }

  @Test
  void levelOneRavenConsumesExactlyTwelvePecksThenExhausts() {
    assertEquals(12, runToExhaustion(1));
  }

  @Test
  void higherRavenLevelAddsOnePeckPerSkillLevel() {
    assertEquals(16, runToExhaustion(5));
    assertEquals(19, runToExhaustion(8));
    assertEquals(31, runToExhaustion(20));
  }

  @Test
  void checkpointPreservesRemainingBudgetRatherThanRefillingIt() throws Exception {
    try (Act1NativeAiIntegrationTest.Fixture f =
             new Act1NativeAiIntegrationTest.Fixture("druidhawk", 0, 1f)) {
      f.world.getMapper(SummonedPet.class).create(f.actor)
          .set(f.target, "raven", SkillId.RAVEN, 5, false, 0);
      f.ai.initialize();
      AiRuntimeStateTest.put(f.ai, Raven.class, "pecks", 3);
      AiRuntimeStateTest.put(f.ai, Raven.class, "attackDueFrame", 77L);
      AiRuntimeStateTest.put(f.ai, Raven.class, "nextDecisionFrame", 50L);
      AiRuntimeStateTest.put(f.ai, Raven.class, "elapsedFrames", 30.5);
      AiRuntimeStateTest.put(f.ai, Raven.class, "clockwise", true);
      AiRuntimeState image = AiRuntimeState.capture(f.ai, f.actor);
      f.ai.initialize();
      image.restore(f.ai, f.actor);
      assertEquals(3, AiRuntimeStateTest.get(f.ai, Raven.class, "pecks"));
      assertEquals(77L, AiRuntimeStateTest.get(f.ai, Raven.class, "attackDueFrame"));
      assertEquals(50L, AiRuntimeStateTest.get(f.ai, Raven.class, "nextDecisionFrame"));
      assertEquals(30.5, AiRuntimeStateTest.get(f.ai, Raven.class, "elapsedFrames"));
      assertEquals(true, AiRuntimeStateTest.get(f.ai, Raven.class, "clockwise"));
    }
  }

  @Test
  void followingWithoutTargetsDoesNotConsumeTheAttackBudget() throws Exception {
    try (Act1NativeAiIntegrationTest.Fixture f =
             new Act1NativeAiIntegrationTest.Fixture("druidhawk", 0, 1f)) {
      f.world.getMapper(SummonedPet.class).create(f.actor)
          .set(f.target, "raven", SkillId.RAVEN, 5, false, 0);
      for (int tick = 0; tick < 90; tick++) f.ai.update(1f);
      assertNull(f.casting());
      assertEquals(16, AiRuntimeStateTest.get(f.ai, Raven.class, "pecks"));
      assertTrue(f.ai.getState().startsWith("CIRCLE"));
    }
  }

  private static int runToExhaustion(int skillLevel) {
    DeathProbe deaths = new DeathProbe();
    try (Act1NativeAiIntegrationTest.Fixture f =
             new Act1NativeAiIntegrationTest.Fixture("druidhawk", 0, 1f, deaths)) {
      int enemy = f.ally("fallen1", 100f, 10f, 10f);
      f.world.getMapper(SummonedPet.class).create(f.actor)
          .set(f.target, "raven", SkillId.RAVEN, skillLevel, false, 0);
      f.ai.params[3] = 100; // Budget test isolates the attempt count from native attack chance.
      int attacks = 0;
      for (int tick = 0; tick < 512 && !"DEAD".equals(f.ai.getState()); tick++) {
        f.ai.update(10f);
        if (f.casting() != null) {
          assertEquals("ATTACK", f.ai.getState());
          assertNotNull(f.sequence());
          assertEquals(enemy, f.casting().targetId);
          attacks++;
          // An update while the final attack is active must not replace A1
          // with death before its action keyframe has had a chance to run.
          f.ai.update(10f);
          assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
          f.finishAction();
        }
      }
      assertEquals("DEAD", f.ai.getState());
      assertEquals(Engine.Monster.MODE_DT, f.sequence().mode1);
      assertEquals(Engine.Monster.MODE_DD, f.sequence().mode2);
      assertEquals(0f, f.world.getMapper(AttributesWrapper.class).get(f.actor).attrs.get(Stat.hitpoints).asFixed());
      assertEquals(1, deaths.count);
      f.ai.update(10f);
      f.ai.kill();
      assertEquals(1, deaths.count);
      return attacks;
    }
  }

  public static final class DeathProbe extends BaseSystem {
    int count;
    @Subscribe public void onDeath(DeathEvent event) { count++; }
    @Override protected void processSystem() {}
  }
}
