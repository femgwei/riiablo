package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.NativeAiCommand;
import com.riiablo.engine.server.component.Velocity;
import org.junit.jupiter.api.Test;

class ScarabCommandTest extends RiiabloTest {
  @Test void meleeCommandUsesJabWithoutChanceRollsAndPopsOnlyCurrent() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("scarab1", difficulty, 1)) {
        Scarab ai = (Scarab) f.ai;
        ai.params[0] = ai.params[3] = 0;
        NativeAiCommand command = f.world.getMapper(NativeAiCommand.class).create(f.actor);
        command.set(f.actor, NativeAiCommand.MOVE, f.target);
        command.copy(f.world, f.actor, NativeAiCommand.ATTACK, f.target);
        Riiablo.gameSeed = 12;
        ai.update(0.04f);
        assertEquals("JAB", ai.getState());
        assertEquals(Riiablo.files.skills.get(ai.monster.monstats.Skill1).Id, f.casting().skillId);
        assertEquals(f.target, f.casting().targetId);
        assertEquals(NativeAiCommand.MOVE, command.command);
        assertEquals(ai.SLEEP, ai.time);
        assertUnconsumed(f, ai);
      }
    }
  }
  @Test void successfulCommandApproachRetainsCommandAndAddsHundredPercent() { verifyApproach(true, false); }
  @Test void failedCommandApproachRemovesOnlyCurrentCommand() { verifyApproach(false, true); }
  @Test void failedSoleCommandRemovesComponent() { verifyApproach(false, false); }

  @Test void commandUsesCurrentAiTargetRatherThanSavedTargetId() {
    try (Fixture f = new Fixture("scarab1", 0, 1)) {
      Scarab ai = (Scarab) f.ai;
      ai.params[0] = ai.params[3] = 0;
      // Fn020 command 1 carries a marker; its action uses pAiTickParam->pTarget.
      f.world.getMapper(NativeAiCommand.class).create(f.actor)
          .set(f.actor, NativeAiCommand.ATTACK, 999999);
      Riiablo.gameSeed = 12;
      ai.update(0.04f);
      assertEquals("JAB", ai.getState());
      assertEquals(f.target, f.casting().targetId);
      assertEquals(f.targetPosition(), f.casting().targetVec);
      assertFalse(f.world.getMapper(NativeAiCommand.class).has(f.actor));
      assertUnconsumed(f, ai);
    }
  }

  @Test void meleeCommandWithoutSkillUsesApproachAndRetainsCommand() {
    try (Fixture f = new Fixture("scarab1", 0, 1)) {
      Scarab ai = (Scarab) f.ai;
      // Replace only this actor's row reference, preserving the shared tables.
      ai.monster.monstats = Riiablo.files.monstats.get("fallen1");
      assertTrue(ai.monster.monstats.Skill1 == null || ai.monster.monstats.Skill1.isEmpty());
      NativeAiCommand command = f.world.getMapper(NativeAiCommand.class).create(f.actor)
          .set(f.actor, NativeAiCommand.ATTACK, f.target);
      Riiablo.gameSeed = 12;
      ai.update(0.04f);
      verify(f.paths).findNativePath(f.actor, f.target, 5, 0);
      assertEquals("APPROACH", ai.getState());
      assertEquals(NativeAiCommand.ATTACK, command.command);
      assertNull(f.casting());
      assertEquals(1f, f.world.getMapper(Velocity.class).get(f.actor).modeSpeedBonusMultiplier);
      assertUnconsumed(f, ai);
    }
  }

  private void verifyApproach(boolean accepted, boolean pending) {
    try (Fixture f = new Fixture("scarab1", 0, 10)) {
      Scarab ai = (Scarab) f.ai;
      NativeAiCommand command = f.world.getMapper(NativeAiCommand.class).create(f.actor);
      if (pending) {
        command.set(f.actor, NativeAiCommand.MOVE, f.target);
        command.copy(f.world, f.actor, NativeAiCommand.ATTACK, f.target);
      } else command.set(f.actor, NativeAiCommand.ATTACK, f.target);
      when(f.paths.findNativePath(f.actor, f.target, 5, 0)).thenReturn(accepted);
      Riiablo.gameSeed = 12;
      ai.update(0.04f);
      verify(f.paths).findNativePath(f.actor, f.target, 5, 0);
      verify(f.paths, never()).findNativeOrbit(anyInt(), anyInt(), anyInt(), anyBoolean());
      assertEquals(accepted ? "APPROACH" : "IDLE", ai.getState());
      assertEquals(accepted ? 1f : 0f,
          f.world.getMapper(Velocity.class).get(f.actor).modeSpeedBonusMultiplier);
      if (accepted) assertEquals(NativeAiCommand.ATTACK, command.command);
      else if (pending) assertEquals(NativeAiCommand.MOVE, command.command);
      else assertFalse(f.world.getMapper(NativeAiCommand.class).has(f.actor));
      assertEquals(0, ai.aiParam0);
      assertEquals(ai.SLEEP, ai.time);
      assertNull(f.casting());
      assertUnconsumed(f, ai);
    }
  }
  private void assertUnconsumed(Fixture f, Scarab ai) {
    D2Seed seed = new D2Seed(12 ^ (f.actor * 0x45D9F3B), 666);
    assertEquals(Seed.rollRandomNumber(seed) & 0xffffffffL, ai.rollAiRandomNumber());
  }
}
