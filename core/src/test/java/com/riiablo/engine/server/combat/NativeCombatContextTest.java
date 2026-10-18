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

  @Test
  void carriesCombatTargetAndPlayerCountContextWithoutChangingDifficulty() {
    NativeCombatContext context = NativeCombatContext.ofDifficulty(2)
        .withCombatants(true, true)
        .withTargetType(true, false)
        .withPlayerCount(99);
    assertEquals(2, context.difficulty());
    assertEquals(true, context.attackerPlayer());
    assertEquals(true, context.defenderPlayer());
    assertEquals(true, context.isPvp());
    assertEquals(true, context.defenderDemon());
    assertEquals(false, context.defenderUndead());
    assertEquals(8, context.playerCount());
  }

  @Test
  void derivedContextsPreserveTheInjectedTableAndLeaveOriginalUnchanged() {
    com.riiablo.codec.excel.DifficultyLevels.Entry row =
        new com.riiablo.codec.excel.DifficultyLevels.Entry();
    row.StaticFieldMin = 33;
    NativeCombatContext original = NativeCombatContext.ofDifficulty(1, row);
    NativeCombatContext derived = original.withCombatants(true, true).withPlayerCount(0);
    assertEquals(row, derived.difficultyRow());
    assertEquals(33, derived.difficultyRow().StaticFieldMin);
    assertEquals(false, original.isPvp());
    assertEquals(true, derived.isPvp());
    assertEquals(1, derived.playerCount());
  }
}
