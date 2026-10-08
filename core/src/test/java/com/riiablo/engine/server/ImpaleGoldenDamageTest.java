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

/** Thirty-second DMG-04 audit: Impale owns one enhanced weapon record. */
class ImpaleGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_PHYSICAL_DAMAGE_PERCENT = {
      300, 325, 350, 375, 400, 425, 450, 475, 500, 525,
      550, 575, 600, 625, 650, 675, 700, 725, 750, 775
  };
  private static final int[] D2MOO_RESOURCE_LOSS_CHANCE = {
      46, 42, 39, 37, 35, 34, 33, 32, 31, 30,
      29, 28, 28, 27, 27, 26, 26, 26, 25, 25
  };

  @Test
  void levelOneToTwentyUseNativeWeaponPercentAndResourceCurves() {
    Skills.Entry skill = Riiablo.files.skills.get("Impale");
    assertNotNull(skill);
    assertEquals(19, skill.Id);
    assertEquals(7, skill.srvstfunc);
    assertEquals(2, skill.srvdofunc);
    assertEquals(128, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals(100, skill.ToHit);
    assertEquals(25, skill.LevToHit);
    assertEquals("ln12", skill.calc1);
    assertEquals("par6-dm34", skill.calc2);
    assertEquals("par5", skill.calc3);
    assertArrayEquals(new int[] {300, 25, 0, 30, 1, 50, 0, 0}, skill.Param);

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
      assertEquals(D2MOO_PHYSICAL_DAMAGE_PERCENT[level - 1],
          SkillFormula.evaluate(skill.calc1, skill, level),
          "native Calc1 level " + level);
      assertEquals(D2MOO_PHYSICAL_DAMAGE_PERCENT[level - 1],
          AmazonSkills.getPhysicalDamagePercent(skill, level),
          "riiablo weapon damage percent level " + level);
      assertEquals(D2MOO_RESOURCE_LOSS_CHANCE[level - 1],
          SkillFormula.evaluate(skill.calc2, skill, level),
          "native Calc2 level " + level);
      assertEquals(1, SkillFormula.evaluate(skill.calc3, skill, level),
          "native Calc3 level " + level);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
