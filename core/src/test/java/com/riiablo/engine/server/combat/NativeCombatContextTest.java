package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NativeCombatContextTest {
  @Test
  void clampsDifficultyAtTheCombatBoundary() {
    assertEquals(0, NativeCombatContext.ofDifficulty(-4).difficulty());
    assertEquals(1, NativeCombatContext.ofDifficulty(1).difficulty());
    assertEquals(2, NativeCombatContext.ofDifficulty(99).difficulty());
  }

  @Test
  void exposesNativePlayerResistancePenalties() {
    assertEquals(0, NativeDifficultyRules.playerResistancePenalty(0));
    assertEquals(-40, NativeDifficultyRules.playerResistancePenalty(1));
    assertEquals(-100, NativeDifficultyRules.playerResistancePenalty(2));
  }

  @Test
  void exposesNativeMonsterColdDurationDivisors() {
    assertEquals(80, NativeDifficultyRules.scaleMonsterColdDuration(80, 0));
    assertEquals(40, NativeDifficultyRules.scaleMonsterColdDuration(80, 1));
    assertEquals(20, NativeDifficultyRules.scaleMonsterColdDuration(80, 2));
  }
}
