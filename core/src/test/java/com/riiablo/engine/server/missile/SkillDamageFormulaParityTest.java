package com.riiablo.engine.server.missile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.codec.excel.Skills;
import org.junit.jupiter.api.Test;

/** Locks the shared Skills.txt damage rules to the D2Common 1.10f behavior. */
class SkillDamageFormulaParityTest {
  @Test
  void fiveDamageSegmentsChangeAtNativeBoundaries() {
    int[] increments = {1, 10, 100, 1_000, 10_000};

    assertEquals(0, MissileDamageResolver.damageBonusByLevel(1, increments));
    assertEquals(7, MissileDamageResolver.damageBonusByLevel(8, increments));
    assertEquals(17, MissileDamageResolver.damageBonusByLevel(9, increments));
    assertEquals(87, MissileDamageResolver.damageBonusByLevel(16, increments));
    assertEquals(187, MissileDamageResolver.damageBonusByLevel(17, increments));
    assertEquals(687, MissileDamageResolver.damageBonusByLevel(22, increments));
    assertEquals(1_687, MissileDamageResolver.damageBonusByLevel(23, increments));
    assertEquals(6_687, MissileDamageResolver.damageBonusByLevel(28, increments));
    assertEquals(16_687, MissileDamageResolver.damageBonusByLevel(29, increments));
  }

  @Test
  void hitShiftIsAppliedBeforeDisplayTruncation() {
    Skills.Entry skill = elementalSkill(5, 7, 7, null);

    assertEquals(2, MissileDamageResolver.skillElementalDamage(skill, 1, true, name -> 0));
    assertEquals(3, MissileDamageResolver.skillElementalDamage(skill, 1, false, name -> 0));
  }

  @Test
  void minimumElementalSynergyUsesNativeGuardButMaximumAlwaysAppliesIt() {
    Skills.Entry skill = elementalSkill(1, 1, 8, "100");

    assertEquals(1, MissileDamageResolver.skillElementalDamage(skill, 1, true, name -> 0));
    assertEquals(2, MissileDamageResolver.skillElementalDamage(skill, 1, false, name -> 0));

    skill.EMinLev[0] = 1;
    assertEquals(2, MissileDamageResolver.skillElementalDamage(skill, 1, true, name -> 0));
  }

  private static Skills.Entry elementalSkill(int min, int max, int hitShift,
      String synergy) {
    Skills.Entry skill = new Skills.Entry();
    skill.EMin = min;
    skill.EMax = max;
    skill.EMinLev = new int[5];
    skill.EMaxLev = new int[5];
    skill.HitShift = hitShift;
    skill.EDmgSymPerCalc = synergy;
    return skill;
  }
}
