package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.replay.MonsterRuntimeCheckpoint;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.NativeUnitFlags;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** AI + Monster images; movement modes, collision/map and owners are explicitly rebuilt dependencies. */
class FlightAndIndependentCheckpointIntegrationTest extends RiiabloTest {
  @Test void flightKeepsHistoricalWaypointAndReturnsSavedGroundModesAfterRestore() {
    AiRuntimeState image; MonsterRuntimeCheckpoint monster; String expected;
    try (Fixture f = flyingFixture()) {
      Vulture ai = (Vulture) f.ai;
      f.seedFirstRoll(60, true);
      ai.update(.04f); assertTrue(ai.monster.vultureAirborne);
      // Existing, unreached waypoint must survive rather than drawing a replacement.
      ai.aiParam0 = 8; ai.aiParam1 = 60; ai.aiParam2 = 45;
      image = AiRuntimeState.capture(ai, f.actor);
      monster = MonsterRuntimeCheckpoint.capture(f.world, f.actor, Set.of(f.actor, f.target));
      expected = continueFlight(f);
    }
    try (Fixture f = flyingFixture()) {
      Vulture ai = (Vulture) f.ai;
      monster.restore(f.world, f.actor); // Shared per-unit RNG is not private AI state.
      f.world.getMapper(MovementModes.class).get(f.actor)
          .set(Engine.Monster.MODE_S1, Engine.Monster.MODE_S1, Engine.Monster.MODE_S1);
      Object modes = f.world.getMapper(MovementModes.class).get(f.actor);
      image.validateRestore(ai, f.actor);
      assertEquals(0, ai.aiParam1);
      image.restore(ai, f.actor);
      assertSame(modes, f.world.getMapper(MovementModes.class).get(f.actor));
      assertEquals(expected, continueFlight(f));
    }
  }

  private static Fixture flyingFixture() {
    Fixture f = new Fixture("vulture1", 0, 20);
    f.world.getMapper(MovementModes.class).create(f.actor)
        .set(Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);
    f.world.getMapper(NativeUnitFlags.class).create(f.actor).set(NativeUnitFlags.MONSTER_TARGET);
    return f;
  }

  private static String continueFlight(Fixture f) {
    Vulture ai = (Vulture) f.ai;
    clearInvocations(f.paths);
    ai.update(13 * Animation.FRAME_DURATION);
    assertEquals(7, ai.aiParam0);
    assertEquals(60, ai.aiParam1); assertEquals(45, ai.aiParam2);
    verify(f.paths).findPath(eq(f.actor), eq(new Vector2(60, 45)), eq(false), eq(Engine.INVALID_ENTITY));
    String trace = ai.getDecisionDiagnostics() + ":" + ai.rollAiRandomNumber();
    ai.kill();
    MovementModes modes = f.world.getMapper(MovementModes.class).get(f.actor);
    assertEquals(Engine.Monster.MODE_NU, modes.NU);
    assertEquals(Engine.Monster.MODE_WL, modes.WL);
    assertEquals(Engine.Monster.MODE_RN, modes.RN);
    assertFalse(ai.monster.vultureAirborne);
    assertTrue(f.world.getMapper(NativeUnitFlags.class).get(f.actor).has(NativeUnitFlags.MONSTER_TARGET));
    return trace + ":" + ai.getState();
  }

  @Test void mountedImpKeepsChosenSkillAndRandomContinuationOnPrebuiltOwner() throws Exception {
    AiRuntimeState image; MonsterRuntimeCheckpoint monster; int selected; String expected;
    try (Fixture f = mountedFixture()) {
      Imp ai = (Imp) f.ai;
      ai.think(f.target, 12, false);
      selected = (Integer) AiRuntimeStateTest.get(ai, Imp.class, "mountedSkill");
      assertTrue(selected == 2 || selected == 4, "real imp row must choose an available mounted skill");
      f.finishAction(); image = AiRuntimeState.capture(ai, f.actor);
      monster = MonsterRuntimeCheckpoint.capture(f.world, f.actor,
          Set.of(f.actor, f.target, ai.monster.nativeOwnerId));
      expected = continueMounted(f);
    }
    try (Fixture f = mountedFixture()) {
      monster.restore(f.world, f.actor);
      image.restore(f.ai, f.actor);
      assertEquals(selected, AiRuntimeStateTest.get(f.ai, Imp.class, "mountedSkill"));
      assertEquals(expected, continueMounted(f));
    }
  }

  private static Fixture mountedFixture() {
    Fixture f = new Fixture(row("Imp"), 0, 12);
    int owner = f.ally("siegebeast1", 100, 10, 10);
    f.world.getMapper(Monster.class).get(f.actor).nativeOwnerId = owner;
    f.world.process(); return f;
  }

  private static String continueMounted(Fixture f) {
    StringBuilder trace = new StringBuilder();
    for (int i = 0; i < 8; i++) {
      ((Imp) f.ai).think(f.target, 12, false);
      trace.append('|').append(f.ai.getState()).append(':');
      if (f.casting() != null) trace.append(f.casting().skillId).append(':').append(f.casting().targetVec);
      trace.append(':').append(f.ai.rollAiRandomNumber()); f.finishAction();
    }
    return trace.toString();
  }

  @Test void gargoyleResumesRemainingRecoveryRatherThanFiringImmediately() {
    AiRuntimeState image;
    try (Fixture f = new Fixture("gargoyletrap", 0, 5)) {
      f.ai.params[0] = 20; f.ai.params[1] = 100; f.ai.params[2] = 25; f.ai.params[3] = 1;
      f.ai.update(.04f); assertNotNull(f.casting()); f.finishAction();
      f.ai.update(.25f); assertNull(f.casting()); image = AiRuntimeState.capture(f.ai, f.actor);
    }
    try (Fixture f = new Fixture("gargoyletrap", 0, 5)) {
      image.restore(f.ai, f.actor);
      f.ai.update(.5f); assertNull(f.casting()); assertEquals(0, f.probe.starts);
      f.ai.update(.125f); assertNull(f.casting());
      f.ai.update(.25f); assertNotNull(f.casting()); assertEquals(1, f.probe.starts);
    }
  }

  @Test void deadBonePrisonDoesNotReplayItsKillCallbackAfterRestore() {
    AiRuntimeState image;
    try (Fixture f = new Fixture("boneprison1", 0, 10)) {
      BonePrison ai = prisonAi(f);
      ai.kill(); assertEquals("DEAD", ai.getState());
      image = AiRuntimeState.capture(ai, f.actor);
    }
    try (Fixture f = new Fixture("boneprison1", 0, 10)) {
      BonePrison ai = prisonAi(f);
      image.restore(ai, f.actor);
      assertEquals("DEAD", ai.getState());
      ai.kill();
      assertNull(f.sequence(), "already-dead guard must not start another death action");
    }
  }

  private static BonePrison prisonAi(Fixture f) {
    // ServerEntityFactory selects this bridge by boneprison1..4 ID, not the table's AI name.
    BonePrison ai = new BonePrison(f.actor);
    f.world.getInjector().inject(ai); ai.initialize(); return ai;
  }

  @Test void exhaustedCrowNestDoesNotRegainSpawnBudget() {
    AiRuntimeState image;
    try (Fixture f = new Fixture(row("FoulCrowNest"), 0, 10)) {
      f.ai.params[0] = 0; f.ai.params[2] = 1;
      f.ai.update(.04f); assertNotNull(f.casting()); f.finishAction();
      image = AiRuntimeState.capture(f.ai, f.actor);
    }
    try (Fixture f = new Fixture(row("FoulCrowNest"), 0, 10)) {
      image.restore(f.ai, f.actor);
      for (int i = 0; i < 5; i++) f.ai.update(2f);
      assertNull(f.casting()); assertEquals(0, f.probe.starts);
      assertEquals("EXHAUSTED", f.ai.getState());
    }
  }

  @Test void vineRetainsFactorySubscriptionInsteadOfImportingSourceQuery() throws Exception {
    AiRuntimeState image;
    try (Fixture f = new Fixture(row("Vines"), 0, 10)) {
      AiRuntimeStateTest.put(f.ai, Vines.class, "nextThink", .75f);
      image = AiRuntimeState.capture(f.ai, f.actor);
    }
    try (Fixture f = new Fixture(row("Vines"), 0, 10)) {
      Object query = AiRuntimeStateTest.get(f.ai, Vines.class, "corpseEntities");
      assertNotNull(query); image.restore(f.ai, f.actor);
      assertSame(query, AiRuntimeStateTest.get(f.ai, Vines.class, "corpseEntities"));
      f.ai.update(.25f);
      assertEquals(.5f, AiRuntimeStateTest.get(f.ai, Vines.class, "nextThink"));
      assertNull(f.casting());
    }
  }

  private static String row(String ai) {
    for (MonStats.Entry row : Riiablo.files.monstats) if (ai.equals(row.AI)) return row.Id;
    throw new AssertionError("Missing AI row: " + ai);
  }
}
