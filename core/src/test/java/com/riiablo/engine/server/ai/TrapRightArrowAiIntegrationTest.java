package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.map.Map;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;

class TrapRightArrowAiIntegrationTest extends RiiabloTest {
  private String row() {
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if ("Trap-RightArrow".equals(mon.AI)) return mon.Id;
    }
    throw new AssertionError("Missing real Trap-RightArrow row");
  }

  private void configure(Fixture f) {
    f.ai.params[0] = 3;
    f.ai.params[1] = 20;
    f.ai.params[2] = 50;
    f.targetPosition().set(12, 20);
  }

  @Test void realRowResolvesAndAttackRunsInAllDifficulties() {
    int count = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"Trap-RightArrow".equals(mon.AI)) continue;
      assertEquals(TrapRightArrow.class, AI.findAI(12, mon.AI).getClass());
      System.out.println("[TRAP_RIGHT_DATA] row=" + mon.Id + " skill=" + mon.Skill1
          + " mode=" + mon.Sk1mode + " missile=" + mon.MissA1);
      count++;
    }
    assertEquals(1, count);
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture(row(), difficulty, 10)) {
        configure(f);
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(SkillCodes.attack, f.casting().skillId);
        assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
        f.sequence().started = true;
        f.ai.update(10);
        assertTrue(f.sequence().started);
      }
    }
  }

  @Test void misalignedTargetWaitsThirtyFrames() {
    try (Fixture f = new Fixture(row(), 0, 10)) {
      configure(f);
      f.targetPosition().x = 13;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      f.targetPosition().x = 12;
      f.ai.update(29 * Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertNotNull(f.casting());
    }
  }

  @Test void distanceEndpointsAreInclusiveAndOutsideWaitsFortyFrames() {
    for (float distance : new float[] {3, 20}) {
      try (Fixture f = new Fixture(row(), 0, 10)) {
        configure(f);
        ((TrapRightArrow) f.ai).beforeThink(Animation.FRAME_DURATION);
        ((TrapRightArrow) f.ai).think(f.target, distance, false);
        assertNotNull(f.casting());
      }
    }
    for (float distance : new float[] {2.99f, 20.01f}) {
      try (Fixture f = new Fixture(row(), 0, 10)) {
        configure(f);
        ((TrapRightArrow) f.ai).think(f.target, distance, false);
        f.ai.update(39 * Animation.FRAME_DURATION);
        assertNull(f.casting());
        f.ai.update(Animation.FRAME_DURATION + .00001f);
        assertNotNull(f.casting());
      }
    }
  }

  @Test void cooldownIsStrictAndKeepsRunningDuringActions() {
    try (Fixture f = new Fixture(row(), 0, 10)) {
      configure(f);
      f.ai.update(Animation.FRAME_DURATION);
      f.ai.update(50 * Animation.FRAME_DURATION);
      f.finishAction();
      ((TrapRightArrow) f.ai).think(f.target, 10, false);
      assertNull(f.casting(), "exact deadline must still wait");
      f.ai.update(30 * Animation.FRAME_DURATION + .00001f);
      assertNotNull(f.casting());
    }
  }

  @Test void regionTypeOneCastsOwnSkill() {
    try (Fixture f = new Fixture(row(), 0, 10)) {
      configure(f);
      Map map = new Map(1, 0);
      try {
        Map.Zone zone = map.createSyntheticArena(40, 1, 1);
        assertEquals(40, zone.levelId());
        assertEquals(1, map.nativeArrowTrapType(40, () -> 1L));
        f.world.getMapper(MapWrapper.class).get(f.actor).set(map, zone);
        f.ai.update(Animation.FRAME_DURATION);
        f.assertSkill(0);
        com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(
            Riiablo.files.monstats.get(row()).Skill1);
        System.out.println("[TRAP_RIGHT_SKILL] skill=" + skill.skill + " srvdo="
            + skill.srvdofunc + " missile=" + skill.srvmissile + " missileA=" + skill.srvmissilea);
      } finally {
        map.dispose();
      }
    }
  }

  @Test void regionChoiceIsLazySharedPerLevelAndResetOnDispose() {
    Map map = new Map(1, 0);
    int[] rolls = {0};
    java.util.function.LongSupplier random = () -> { rolls[0]++; return 4L; };
    assertEquals(0, map.nativeArrowTrapType(39, random));
    assertEquals(0, rolls[0]);
    assertEquals(1, map.nativeArrowTrapType(40, random));
    assertEquals(1, map.nativeArrowTrapType(40, random));
    assertEquals(1, rolls[0]);
    assertEquals(1, map.nativeArrowTrapType(41, random));
    assertEquals(2, rolls[0]);
    map.dispose();
    assertEquals(1, map.nativeArrowTrapType(40, random));
    assertEquals(3, rolls[0]);
    map.dispose();
  }

  @Test void absentTargetWaitsFortyFrames() {
    try (Fixture f = new Fixture(row(), 0, 10)) {
      configure(f);
      f.targetPosition().set(1000, 1000);
      f.ai.update(Animation.FRAME_DURATION);
      f.targetPosition().set(12, 20);
      f.ai.update(39 * Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.ai.update(Animation.FRAME_DURATION + .00001f);
      assertNotNull(f.casting());
    }
  }
}
