package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillFormula;
import org.junit.jupiter.api.Test;

/** Twenty-seventh DMG-04 audit: Power Strike's skill-owned lightning packet. */
class PowerStrikeGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_MIN_LIGHTNING = {
      1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
      1, 1, 1, 1, 1, 1, 1, 1, 1, 1
  };
  private static final int[] D2MOO_MAX_LIGHTNING = {
      16, 34, 52, 70, 88, 106, 124, 142, 178, 214,
      250, 286, 322, 358, 394, 430, 484, 538, 592, 646
  };

  @Test
  void levelOneToTwentyMatchD2mooLightningCurveWithoutWeaponOrSynergy() {
    Skills.Entry skill = Riiablo.files.skills.get("Power Strike");
    assertNotNull(skill);
    assertEquals(14, skill.Id);
    assertEquals(6, skill.srvstfunc);
    assertEquals(2, skill.srvdofunc);
    assertEquals(128, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals(20, skill.ToHit);
    assertEquals(12, skill.LevToHit);
    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MaxLevDam);
    assertEquals("ltng", skill.EType);
    assertEquals(1, skill.EMin);
    assertEquals(16, skill.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMinLev);
    assertArrayEquals(new int[] {18, 36, 54, 72, 90}, skill.EMaxLev);
    assertEquals(
        "(skill('Lightning Strike'.blvl)+skill('Lightning Bolt'.blvl)"
            + "+skill('Charged Strike'.blvl)+skill('Lightning Fury'.blvl)) * par8",
        skill.EDmgSymPerCalc);
    assertTrue(blank(skill.srvmissile));
    assertTrue(blank(skill.srvmissilea));
    assertTrue(blank(skill.srvmissileb));
    assertTrue(blank(skill.srvmissilec));
    assertTrue(blank(skill.srvmissiled));

    for (int level = 1; level <= 20; level++) {
      int expectedMin = D2MOO_MIN_LIGHTNING[level - 1];
      int expectedMax = D2MOO_MAX_LIGHTNING[level - 1];
      assertEquals(0, SkillFormula.evaluate(
          skill.EDmgSymPerCalc, skill, level, name -> 0),
          "zero hard-point synergy level " + level);
      assertEquals(expectedMin, MissileDamageResolver.skillElementalDamage(
          skill, level, true, name -> 0), "minimum level " + level);
      assertEquals(expectedMax, MissileDamageResolver.skillElementalDamage(
          skill, level, false, name -> 0), "maximum level " + level);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
