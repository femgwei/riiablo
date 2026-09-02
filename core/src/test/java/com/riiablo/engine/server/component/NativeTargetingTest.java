package com.riiablo.engine.server.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NativeTargetingTest {
  @Test
  void separatesSelectionAttackAndCombatPredicates() {
    NativeUnitFlags flags = new NativeUnitFlags().reset();
    assertFalse(NativeTargeting.isTargetable(flags));
    assertFalse(NativeTargeting.canBeAttacked(flags));
    assertFalse(NativeTargeting.isValidCombatTarget(flags));

    flags.set(NativeUnitFlags.TARGETABLE);
    assertTrue(NativeTargeting.isTargetable(flags));
    assertFalse(NativeTargeting.isValidCombatTarget(flags));

    flags.set(NativeUnitFlags.CAN_BE_ATTACKED);
    assertTrue(NativeTargeting.canBeAttacked(flags));
    assertFalse(NativeTargeting.isValidCombatTarget(flags));

    flags.set(NativeUnitFlags.IS_VALID_TARGET);
    assertTrue(NativeTargeting.isValidCombatTarget(flags));
  }

  @Test
  void missingFlagsAreConservativelyRejectedByNativePredicates() {
    assertFalse(NativeTargeting.isTargetable(null));
    assertFalse(NativeTargeting.canBeAttacked(null));
    assertFalse(NativeTargeting.isValidCombatTarget(null));
  }
}
