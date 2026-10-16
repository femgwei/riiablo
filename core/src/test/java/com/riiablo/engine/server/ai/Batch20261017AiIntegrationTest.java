package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Player;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;

/** Real 1.10f rows; verifies decisions and the shared action lifecycle. */
class Batch20261017AiIntegrationTest extends RiiabloTest {
  private String row(String name) {
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (name.equals(mon.AI)) return mon.Id;
    }
    throw new AssertionError("Missing real AI row: " + name);
  }

  private void removeTarget(Fixture f) {
    f.world.getMapper(Player.class).remove(f.target);
    f.world.process();
  }

  @Test void illusionAliasResolvesAndCastsFixedCoordinateInAllDifficulties() {
    assertTrue(AI.findAI(1, "7TIllusion") instanceof SevenTIllusion);
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture(row("7TIllusion"), difficulty, 10)) {
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(SkillCodes.attack, f.casting().skillId);
        assertEquals(Engine.INVALID_ENTITY, f.casting().targetId);
        assertEquals(0f, f.casting().targetVec.x);
        assertEquals(10f, f.casting().targetVec.y);
        assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
        f.ai.update(10f);
        assertNotEquals("DEAD", f.ai.getState(), "active action blocks next decision");
        f.finishAction();
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals("DEAD", f.ai.getState());
        assertEquals(Engine.Monster.MODE_DT, f.sequence().mode1);
        assertTrue(f.world.getMapper(NativeUnitFlags.class).get(f.actor)
            .has(NativeUnitFlags.NO_TREASURE_CLASS));
      }
    }
  }

  @Test void illusionRunsWithoutTargetAndStillExpiresAfterRejectedCast() {
    try (Fixture f = new Fixture(row("7TIllusion"), 0, 10)) {
      removeTarget(f);
      f.probe.reject = true;
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.ai.update(11 * Animation.FRAME_DURATION);
      assertEquals("DEAD", f.ai.getState());
    }
  }

  @Test void catapultSuccessStartsOnlyTargetlessModeInAllDifficulties() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture(row("Catapult"), difficulty, 10)) {
        assertTrue(f.ai instanceof Catapult);
        f.ai.params[0] = 100;
        f.ai.update(Animation.FRAME_DURATION);
        assertNull(f.casting(), "native mode-only action must not invent an attack cast");
        assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
        f.sequence().started = true;
        f.ai.update(10f);
        assertTrue(f.sequence().started);
      }
    }
  }

  @Test void catapultRunsWithoutTargetAndFailedRollWaitsFifteenFrames() {
    try (Fixture f = new Fixture(row("Catapult"), 0, 10)) {
      removeTarget(f);
      f.ai.params[0] = 0;
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.sequence());
      f.ai.params[0] = 100;
      f.ai.update(14 * Animation.FRAME_DURATION);
      assertNull(f.sequence());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
      assertNull(f.casting());
    }
  }

  @Test void leftArrowUsesYAxisAndRetainsRightArrowLifecycle() {
    try (Fixture f = new Fixture(row("Trap-LeftArrow"), 0, 10)) {
      assertTrue(f.ai instanceof TrapLeftArrow);
      f.ai.params[0] = 3;
      f.ai.params[1] = 20;
      f.ai.params[2] = 50;
      f.targetPosition().set(20, 13);
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.targetPosition().y = 12;
      f.ai.update(31 * Animation.FRAME_DURATION);
      assertNotNull(f.casting());
      assertEquals(SkillCodes.attack, f.casting().skillId);
    }
  }
}
