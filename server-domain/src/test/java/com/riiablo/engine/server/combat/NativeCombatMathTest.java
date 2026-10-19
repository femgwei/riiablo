package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NativeCombatMathTest {
  @Test
  void ratingAppliesMonsterDexterityBeforePercentThenTargetBonuses() {
    assertEquals(251, NativeCombatMath.effectiveAttackRating(100, false, 10, 50, 17, 9, true, true));
    assertEquals(176, NativeCombatMath.effectiveAttackRating(100, true, 10, 50, 17, 9, true, true));
    assertEquals(0, NativeCombatMath.effectiveAttackRating(-100, true, 0, 0, -5, -5, true, true));
    assertEquals(0, NativeCombatMath.effectiveDefense(20, -30, 40, true));
    assertEquals(60, NativeCombatMath.effectiveDefense(20, -30, 40, false));
  }

  @Test
  void hitChanceKeepsIntegerRoundingLevelFallbackAndBounds() {
    assertEquals(95, NativeCombatMath.hitChance(0, 0, 10, 10));
    assertEquals(20, NativeCombatMath.hitChance(0, 0, 10, 90));
    assertEquals(95, NativeCombatMath.hitChance(100, 0, 0, 0));
    assertEquals(33, NativeCombatMath.hitChance(100, 200, 10, 10));
    assertEquals(44, NativeCombatMath.hitChance(100, 200, 20, 10));
    assertEquals(5, NativeCombatMath.hitChance(0, 100, 20, 10));
  }

  @Test
  void blockCapsBeforeRunningAndDoesNotScaleMonstersByDexterity() {
    assertEquals(75, NativeCombatMath.blockChance(true, true, true, 20, 40, 315, 50, false));
    assertEquals(25, NativeCombatMath.blockChance(true, true, true, 20, 40, 315, 50, true));
    assertEquals(40, NativeCombatMath.blockChance(true, false, false, 20, 40, 0, 0, true));
    assertEquals(0, NativeCombatMath.blockChance(true, true, false, 20, 40, 315, 50, false));
    assertEquals(0, NativeCombatMath.blockChance(false, false, false, 0, 40, 0, 0, false));
    assertEquals(0, NativeCombatMath.blockChance(true, true, true, 20, 40, 14, 0, false));
  }

  @Test
  void physicalBonusesCombinePercentagesButRetainMissileStrengthExemption() {
    assertEquals(22, NativeCombatMath.physicalDamageAfterBonuses(11, false, 50, 25, 10, 15, true, true));
    assertEquals(16, NativeCombatMath.physicalDamageAfterBonuses(11, true, 50, 25, 10, 15, true, true));
    assertEquals(2, NativeCombatMath.physicalDamageAfterBonuses(11, false, -5, -100, 0, 0, false, false));
    assertEquals(1, NativeCombatMath.physicalDamageAfterBonuses(0, true, 0, 0, 0, 0, false, false));
  }

  @Test
  void physicalReductionKeepsMinimumAndSanctuaryBypassButNotFlatBypass() {
    assertEquals(0, NativeCombatMath.physicalReduction(100, true, 100, 5, false));
    assertEquals(95, NativeCombatMath.physicalReduction(100, true, 100, 5, true));
    assertEquals(1, NativeCombatMath.physicalReduction(100, false, 50, 100, false));
    assertEquals(145, NativeCombatMath.physicalReduction(100, false, -50, 5, false));
    assertEquals(0, NativeCombatMath.physicalReduction(0, false, 0, -10, false));
  }

  @Test
  void legacyResistanceKeepsCapsPierceAndResistanceBeforeMdr() {
    assertEquals(40, NativeCombatMath.legacyElementalResistance(100, false, 50, 75, 0, true, 10, 1));
    assertEquals(50, NativeCombatMath.legacyElementalResistance(100, false, 50, 75, 0, false, 10, 1));
    assertEquals(0, NativeCombatMath.legacyElementalResistance(100, true, 50, 75, 100, false, 0, 1));
    assertEquals(5, NativeCombatMath.legacyElementalResistance(100, false, 200, 200, 0, false, 0, 1));
    assertEquals(200, NativeCombatMath.legacyElementalResistance(100, false, -200, 75, -10, false, 0, 1));
    assertEquals(150, NativeCombatMath.legacyElementalResistance(100, false, 50, 75, 200, false, 0, 1));
    assertEquals(40 * 256, NativeCombatMath.legacyElementalResistance(100 * 256, false, 50, 75, 0, true, 10, 256));
    assertEquals(Integer.MAX_VALUE, NativeCombatMath.legacyElementalResistance(Integer.MAX_VALUE, false, -100, 75, 0, false, 0, 1));
  }

  @Test
  void absorbClampsPercentAndFlatAfterScalingWithoutExceedingDamage() {
    assertEquals(35, NativeCombatMath.elementalAbsorb(100, 25, 10, 1));
    assertEquals(100, NativeCombatMath.elementalAbsorb(100, 200, 10, 1));
    assertEquals(0, NativeCombatMath.elementalAbsorb(100, -1, -1, 256));
    assertEquals(1408, NativeCombatMath.elementalAbsorb(10 * 256, 25, 3, 256));
    assertEquals(100, NativeCombatMath.elementalAbsorb(100, 0, Integer.MAX_VALUE, 256));
    assertEquals(0, NativeCombatMath.elementalAbsorb(0, 100, 100, 1));
  }

  @Test
  void leechKeepsPhysicalOnlyScalarRoundingWithoutAddingNewPolicies() {
    assertEquals(7, NativeCombatMath.leech(99, 8));
    assertEquals(0, NativeCombatMath.leech(99, -1));
    assertEquals(0, NativeCombatMath.leech(0, 100));
    assertEquals(150, NativeCombatMath.leech(100, 150));
  }
}
