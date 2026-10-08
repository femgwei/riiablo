package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.AmazonSkills;
import com.riiablo.engine.server.skill.SkillFormula;
import org.junit.jupiter.api.Test;

/** Forty-third DMG-04 audit: Fend owns independent enhanced weapon records. */
class FendGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_DAMAGE_PERCENT = {
      70, 80, 90, 100, 110, 120, 130, 140, 150, 160,
      170, 180, 190, 200, 210, 220, 230, 240, 250, 260
  };

  @Test
  void levelOneToTwentyUseNativeTargetCapAndWeaponPercentWithoutOwningFixedDamage() {
    Skills.Entry skill = Riiablo.files.skills.get("Fend");
    assertNotNull(skill);
    assertEquals(30, skill.Id);
    assertEquals(9, skill.srvstfunc);
    assertEquals(13, skill.srvdofunc);
    assertEquals(128, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals(40, skill.ToHit);
    assertEquals(10, skill.LevToHit);
    assertEquals("12", skill.calc1);
    assertEquals("ln34", skill.calc2);
    assertTrue(blank(skill.calc3));
    assertArrayEquals(new int[] {1, 60, 70, 10, 0, 0, 0, 0}, skill.Param);

    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MaxLevDam);
    assertTrue(blank(skill.EType));
    assertEquals(0, skill.EMin);
    assertEquals(0, skill.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMinLev);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMaxLev);
    assertTrue(blank(skill.srvmissile));
    assertTrue(blank(skill.srvmissilea));
    assertTrue(blank(skill.srvmissileb));
    assertTrue(blank(skill.srvmissilec));
    assertTrue(blank(skill.srvmissiled));

    for (int level = 1; level <= 20; level++) {
      int expected = D2MOO_DAMAGE_PERCENT[level - 1];
      assertEquals(12, SkillFormula.evaluate(skill.calc1, skill, level),
          "native Calc1 target cap level " + level);
      assertEquals(12, AmazonSkills.getFendHitCount(skill, level),
          "riiablo target cap level " + level);
      assertEquals(expected, SkillFormula.evaluate(skill.calc2, skill, level),
          "native Calc2 level " + level);
      assertEquals(expected, AmazonSkills.getPhysicalDamagePercent(skill, level),
          "riiablo weapon percent level " + level);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
