package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NativeStatusDurationMathTest {
  @Test
  void coldUsesPlayerCapButMonsterImmunityAndNoDifficultyDivisor() {
    assertEquals(25, NativeStatusDurationMath.coldLengthBeforeState(100, true, 99, 75, 0));
    assertEquals(1, NativeStatusDurationMath.coldLengthBeforeState(100, false, 99, 75, 0));
    assertEquals(0, NativeStatusDurationMath.coldLengthBeforeState(100, false, 100, 75, 250));
    assertEquals(200, NativeStatusDurationMath.coldLengthBeforeState(100, false, 90, 75, 250));
    assertEquals(Integer.MAX_VALUE, NativeStatusDurationMath.coldLengthBeforeState(Integer.MAX_VALUE, false, -100, 75, 0));
  }

  @Test
  void poisonUsesGameModePenaltyIndependentlyOfFoldedElementalResistance() {
    com.riiablo.codec.excel.DifficultyLevels.Entry row = new com.riiablo.codec.excel.DifficultyLevels.Entry();
    row.ResistPenalty = -37;
    assertEquals(150, NativeStatusDurationMath.poisonDuration(100, true, 0, 0,
        NativeCombatContext.forGameplay(2, row, false)));
    assertEquals(137, NativeStatusDurationMath.poisonDuration(100, true, 0, 0,
        NativeCombatContext.forGameplay(2, row, true)));
    assertEquals(110, NativeStatusDurationMath.poisonDuration(100, false, 90, 250, null));
    assertEquals(25, NativeStatusDurationMath.poisonDuration(100, true, 100, 0, NativeCombatContext.normal()));
    assertEquals(0, NativeStatusDurationMath.poisonDuration(100, false, 100, 0, null));
  }

  @Test
  void poisonMasterySnapshotClampsAndSaturatesBeforeSpread() {
    assertEquals(16, NativeStatusDurationMath.poisonMasterySnapshot(11, 50));
    assertEquals(0, NativeStatusDurationMath.poisonMasterySnapshot(11, -200));
    assertEquals(0, NativeStatusDurationMath.poisonMasterySnapshot(-1, 100));
    assertEquals(Integer.MAX_VALUE, NativeStatusDurationMath.poisonMasterySnapshot(Integer.MAX_VALUE, 100));
  }

  @Test
  void rateConversionsKeepFractionAndInvalidDurationDefaults() {
    assertEquals(58, NativeStatusDurationMath.totalPoisonToBitRate(17, 75));
    assertEquals(58f / 256f, NativeStatusDurationMath.totalPoisonToPerFrame(17, 75));
    assertEquals(0.5f, NativeStatusDurationMath.fixed8RateToPerFrame(128));
    assertEquals(0f, NativeStatusDurationMath.fixed8RateToPerFrame(-1));
    assertEquals(0, NativeStatusDurationMath.totalPoisonToBitRate(10, 0));
    assertEquals(Integer.MAX_VALUE, NativeStatusDurationMath.totalPoisonToBitRate(Integer.MAX_VALUE, 1));
  }
}
