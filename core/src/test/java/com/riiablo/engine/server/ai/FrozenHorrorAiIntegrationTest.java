package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;

class FrozenHorrorAiIntegrationTest extends RiiabloTest {
  @Test void allFiveRowsResolveDedicatedAi() {
    int rows = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"FrozenHorror".equals(mon.AI)) continue;
      assertTrue(AI.findAI(12, mon.AI) instanceof FrozenHorror);
      rows++;
    }
    assertEquals(5, rows);
  }

  @Test void blastPrecedesMeleeAndActiveCastBlocksNewDecisions() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("frozenhorror1", difficulty, 1f)) {
        f.ai.params[0] = f.ai.params[2] = 100;
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(323, f.casting().skillId);
        assertEquals(Engine.Monster.MODE_A2, f.sequence().mode1);
        f.sequence().started = true;
        f.ai.update(10f);
        assertTrue(f.sequence().started);
      }
    }
  }

  @Test void infernoToggleIsClearedBeforeOrdinaryMeleeThenBlastCanResume() {
    try (Fixture f = new Fixture("frozenhorror1", 0, 1f)) {
      UnitStates states = f.world.getMapper(UnitStates.class).create(f.actor);
      states.init(f.actor);
      states.stateList.addState(StateId.INFERNO, 0, 1, f.actor);
      f.ai.params[0] = f.ai.params[2] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(SkillCodes.attack, f.casting().skillId);
      assertFalse(states.stateList.hasState(StateId.INFERNO));
      f.finishAction();
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(323, f.casting().skillId);
    }
  }

  @Test void rangeIsStrictAndRejectedBlastDoesNotBecomeMelee() {
    try (Fixture f = new Fixture("frozenhorror1", 0, 1f)) {
      f.ai.params[0] = f.ai.params[2] = 100;
      ((FrozenHorror) f.ai).think(f.target,
          AI.monsterSkillLevel(f.ai.monster.monstats, 0), true);
      assertEquals(SkillCodes.attack, f.casting().skillId);
    }
    try (Fixture f = new Fixture("frozenhorror1", 0, 1f)) {
      f.ai.params[0] = f.ai.params[2] = 100;
      f.probe.reject = true;
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
      assertEquals("IDLE", f.ai.getState());
    }
  }
}
