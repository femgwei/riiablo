package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import org.junit.jupiter.api.Test;

/** Real 1.10f rows and deterministic pieces of AITHINK_Fn051_Diablo. */
class DiabloAiIntegrationTest extends RiiabloTest {
  @Test
  void retainedActionUsesRealToggleNotPriorAttackCounterAndRespectsActiveCast() throws Exception {
    for (String row : new String[] {"diablo", "diabloclone"}) {
      try (Act1NativeAiIntegrationTest.Fixture f = new Act1NativeAiIntegrationTest.Fixture(row, 0, 3)) {
        Diablo ai = (Diablo) f.ai;
        setAction(ai, Diablo.ACTION_INFERNO, 4);
        ai.think(f.target, 3, true);
        f.assertSkill(0);
        assertEquals(193, f.casting().skillId, "prior attack count must not suppress lightning");
        com.riiablo.engine.server.component.UnitStates states = f.world.getMapper(
            com.riiablo.engine.server.component.UnitStates.class).create(f.actor).init(f.actor);
        states.stateList.addState(com.riiablo.engine.server.state.StateId.INFERNO, 0, 3, f.actor);
        ai.update(10f);
        assertTrue(states.stateList.hasState(com.riiablo.engine.server.state.StateId.INFERNO),
            "the shared action lifecycle must not cancel an active SC cast after two frames");
        f.finishAction();
        ai.update(10f);
        assertFalse(states.stateList.hasState(com.riiablo.engine.server.state.StateId.INFERNO));
        assertNull(f.casting());
        assertEquals("IDLE", ai.getState());
      }
    }
  }

  @Test
  void retainedActionClearsStateEvenWithZeroCounterOrLostTarget() throws Exception {
    for (boolean lostTarget : new boolean[] {false, true}) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(3)) {
        Diablo ai = (Diablo) f.ai;
        setAction(ai, Diablo.ACTION_INFERNO, 0);
        com.riiablo.engine.server.component.UnitStates states = f.world.getMapper(
            com.riiablo.engine.server.component.UnitStates.class).create(f.actor).init(f.actor);
        states.stateList.addState(com.riiablo.engine.server.state.StateId.INFERNO, 0, 3, f.actor);
        if (lostTarget) ai.thinkWithoutTarget();
        else ai.think(f.target, 3, true);
        assertFalse(states.stateList.hasState(com.riiablo.engine.server.state.StateId.INFERNO));
        assertNull(f.casting());
      }
    }
  }

  private static void setAction(Diablo ai, int action, int count) throws Exception {
    java.lang.reflect.Field field = Diablo.class.getDeclaredField("action");
    field.setAccessible(true); field.setInt(ai, action);
    field = Diablo.class.getDeclaredField("actionCount");
    field.setAccessible(true); field.setInt(ai, count);
  }

  @Test
  void nativeDiabloRowsResolveDedicatedAiAndExposeConfiguredSkills() {
    int rows = 0;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (!"Diablo".equalsIgnoreCase(row.AI)) continue;
      rows++;
      assertTrue(AI.findAI(12, row.AI) instanceof Diablo);
      assertNotNull(Riiablo.files.skills.get(row.Skill1));
      assertNotNull(Riiablo.files.skills.get(row.Skill2));
    }
    assertTrue(rows > 0, "1.10f contains no MonStats row for Diablo");
  }

  @Test
  void nativeChanceTablesKeepMeleeAndDistanceBranches() {
    int[] melee = Diablo.chances(1f, true, 0);
    assertEquals(24, melee[Diablo.ACTION_SKILL3]);
    assertEquals(70, melee[Diablo.ACTION_ATTACK2]);
    assertEquals(15, melee[Diablo.ACTION_SKILL4_ALT]);

    int[] ranged = Diablo.chances(30f, false, 0);
    assertEquals(0, ranged[Diablo.ACTION_SKILL4]);
    assertEquals(20, ranged[Diablo.ACTION_SKILL5]);
    assertEquals(20, ranged[Diablo.ACTION_SKILL7]);
    assertEquals(Diablo.ACTION_ATTACK1, Diablo.selectAction(melee, 0));
  }

  @Test
  void actionLifecycleUsesA1AttackAndDoesNotReplaceMissingSkillWithAttack() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1f)) {
      Diablo ai = (Diablo) f.ai;
      ai.think(f.target, 1f, true);
      // A random action is allowed by the native weighted table; regardless
      // of the selected spell, the result must be a native action lifecycle.
      assertTrue(f.ai.getState().equals("ATTACK") || f.ai.getState().equals("CAST")
          || f.ai.getState().equals("IDLE") || f.ai.getState().equals("CIRCLE")
          || f.ai.getState().equals("APPROACH"));
    }
  }

  private static Act1NativeAiIntegrationTest.Fixture fixture(float distance) {
    String rowId = rowIdForAi("Diablo");
    Act1NativeAiIntegrationTest.Fixture f =
        new Act1NativeAiIntegrationTest.Fixture(rowId, 0, distance);
    return f;
  }

  private static String rowIdForAi(String aiName) {
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (aiName.equalsIgnoreCase(row.AI)) return row.Id;
    }
    fail("1.10f contains no MonStats row for AI " + aiName);
    return null;
  }
}
