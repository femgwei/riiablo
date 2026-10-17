package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import org.junit.jupiter.api.Test;

class BaboonRegenerationRecoveryTest extends RiiabloTest {
  @Test void healthyMeleeBaboonContinuesEscapeInsteadOfNoncombatRecovery() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("baboon1", difficulty, 1)) {
        Baboon ai = (Baboon) f.ai;
        f.health(76, 100);
        ai.aiParam0 = 4;
        f.seedFirstRoll(33, false);
        ai.update(0.04f);
        assertEquals(3, ai.aiParam0);
        assertEquals("ESCAPE", ai.getState());
        assertNull(f.casting());
      }
    }
  }

  @Test void acceptedEscapeIsNotReclassifiedAsFailureByMissingProjectedPathComponent() {
    try (Fixture f = new Fixture("baboon1", 0, 10)) {
      Baboon ai = (Baboon) f.ai;
      f.health(50, 100);
      ai.aiParam0 = 4;
      ai.update(0.04f);
      assertEquals("ESCAPE", ai.getState());
      verify(f.paths, never()).findPath(f.actor, f.targetPosition(), false, f.target);
      assertNull(f.casting());
    }
  }

  @Test void rejectedMeleeEscapeUsesConfiguredAttackChoice() {
    try (Fixture f = new Fixture("baboon1", 0, 1)) {
      Baboon ai = (Baboon) f.ai;
      f.health(50, 100);
      ai.aiParam0 = 4;
      ai.params[3] = 100;
      f.seedFirstRoll(33, false);
      f.world.getMapper(com.riiablo.engine.server.component.Pathfind.class).create(f.actor);
      when(f.paths.findPath(anyInt(), any(), anyBoolean(), anyInt())).thenReturn(false);
      ai.update(0.04f);
      assertEquals("ATTACK", ai.getState());
      assertNotNull(f.casting());
      assertEquals(f.target, f.casting().targetId);
      assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
    }
  }
}
