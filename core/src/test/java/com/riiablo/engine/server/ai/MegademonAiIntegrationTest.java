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

class MegademonAiIntegrationTest extends RiiabloTest {
  @Test void rangedInfernoRequiresStrictSkillLevelRangeAndClearsExistingState() {
    for (int branch = 0; branch < 3; branch++) {
      try (Fixture f = new Fixture("megademon1", 0, 3)) {
        Megademon ai = (Megademon) f.ai;
        ai.params[0] = 100;
        ai.params[3] = 0;
        UnitStates states = f.world.getMapper(UnitStates.class).create(f.actor);
        states.init(f.actor);
        if (branch == 2) states.stateList.addState(StateId.INFERNO, 0, 1, f.actor);
        ai.beforeThink(Animation.FRAME_DURATION);
        float range = AI.monsterSkillLevel(Riiablo.files.monstats.get("megademon1"), 0);
        // Isolate the native distance/combat inputs without modifying production tables.
        ai.think(f.target, branch == 1 ? range : range - 0.1f, false);
        assertFalse(states.stateList.hasState(StateId.INFERNO));
        if (branch == 0) assertEquals(319, f.casting().skillId);
        else { assertNull(f.casting()); assertEquals("IDLE", ai.getState()); }
      }
    }
  }

  @Test void rangedApproachRollCanWalkOrIdleWithoutCasting() {
    for (int chance : new int[] {0, 100}) {
      try (Fixture f = new Fixture("megademon1", 0, 30)) {
        f.ai.params[0] = 100;
        f.ai.params[3] = chance;
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(chance == 0 ? "IDLE" : "APPROACH", f.ai.getState());
        assertNull(f.casting());
      }
    }
  }

  @Test void cooldownAdvancesDuringBusyAction() {
    try (Fixture f = new Fixture("megademon1", 0, 1)) {
      f.ai.params[1] = 100;
      f.ai.params[5] = 50;
      f.ai.update(Animation.FRAME_DURATION);
      f.ai.update(51 * Animation.FRAME_DURATION);
      f.finishAction();
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(319, f.casting().skillId);
    }
  }

  @Test void zeroElapsedTimeDoesNotPassStrictInitialTimer() {
    try (Fixture f = new Fixture("megademon1", 0, 1)) {
      f.ai.params[1] = f.ai.params[2] = 100;
      f.ai.update(0);
      assertEquals(SkillCodes.attack, f.casting().skillId);
    }
  }

  @Test void originalRowsResolveDedicatedAiAnd319S1() {
    int rows = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"Megademon".equals(mon.AI)) continue;
      rows++;
      assertEquals("Megademon", AI.findAI(12, mon.AI).getClass().getSimpleName());
      assertEquals(319, Riiablo.files.skills.get(mon.Skill1).Id);
      assertEquals("S1", mon.Sk1mode);
    }
    assertTrue(rows > 0);
  }

  @Test void infernoPrecedesSwingAndNeverRestartsBusyAction() {
    for (int difficulty=0; difficulty<3; difficulty++) {
      try (Fixture f = new Fixture("megademon1", difficulty, 1)) {
        f.ai.params[1] = f.ai.params[2] = 100;
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(319, f.casting().skillId);
        assertEquals(Engine.Monster.MODE_S1, f.sequence().mode1);
        f.sequence().started=true;
        f.ai.update(10);
        assertTrue(f.sequence().started);
      }
    }
  }

  @Test void stateIsClearedButMeleeCanImmediatelyCastAgainWhenTimerAllows() {
    try (Fixture f = new Fixture("megademon1",0,1)) {
      UnitStates states=f.world.getMapper(UnitStates.class).create(f.actor); states.init(f.actor);
      states.stateList.addState(StateId.INFERNO,0,1,f.actor);
      f.ai.params[1]=100;
      f.ai.update(Animation.FRAME_DURATION);
      assertFalse(states.stateList.hasState(StateId.INFERNO));
      assertEquals(319,f.casting().skillId);
    }
  }

  @Test void failedInfernoRollUsesSwingThenCircleThenFiveFrameIdle() {
    for(int branch=0;branch<3;branch++) {
      try(Fixture f=new Fixture("megademon1",0,1)) {
        f.ai.params[1]=0; f.ai.params[2]=branch==0?100:0; f.ai.params[4]=branch==1?100:0;
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(branch==0?"ATTACK":branch==1?"CIRCLE":"IDLE",f.ai.getState());
        if(branch==0) assertEquals(SkillCodes.attack,f.casting().skillId);
        else assertNull(f.casting());
      }
    }
  }

  @Test void rejectedInfernoConsumesCooldownAndDoesNotBecomeSwing() {
    try(Fixture f=new Fixture("megademon1",0,1)) {
      f.ai.params[1]=f.ai.params[2]=100; f.ai.params[5]=50;
      f.probe.reject=true;
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
      assertEquals("IDLE",f.ai.getState());
      f.probe.reject=false;
      f.ai.update(11*Animation.FRAME_DURATION);
      assertEquals(SkillCodes.attack,f.casting().skillId);
    }
  }
}
