package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Boundary contracts copied from D2MOO SUNITDMG_RollDamageValueInRange. */
class DamageCalculatorNativeRangeTest {
  @Test
  void collapsedPhysicalRangeDoesNotInventAnExtraPoint() {
    DamageCalculator calculator = new DamageCalculator();
    assertEquals(10, calculator.calculatePhysicalDamage(10, 10,
        0, 0, 0, 0, 0, 128));
  }

  @Test
  void collapsedElementalRangeDoesNotConsumeARandomInterval() {
    DamageCalculator calculator = new DamageCalculator();
    assertEquals(10, calculator.calculateElementalDamage(10, 10, 0));
  }
}
