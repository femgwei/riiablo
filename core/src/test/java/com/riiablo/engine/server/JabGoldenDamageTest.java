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

/** Twenty-third DMG-04 audit: Jab owns three weapon records and no fixed damage packet. */
class JabGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_PHYSICAL_DAMAGE_PERCENT = {
      -15, -12, -9, -6, -3, 0, 3, 6, 9, 12,
      15, 18, 21, 24, 27, 30, 33, 36, 39, 42
  };

  @Test
  void levelOneToTwentyUseNativeWeaponPercentWithoutOwningFixedDamage() {
    Skills.Entry skill = Riiablo.files.skills.get("Jab");
    assertNotNull(skill);
    assertEquals(10, skill.Id);
    assertEquals(5, skill.srvstfunc);
    assertEquals(7, skill.srvdofunc);
    assertEquals(128, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals(10, skill.ToHit);
    assertEquals(9, skill.LevToHit);
    assertEquals("ln34", skill.calc1);
    assertEquals(-15, skill.Param[2]);
    assertEquals(3, skill.Param[3]);

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
      int expected = D2MOO_PHYSICAL_DAMAGE_PERCENT[level - 1];
      assertEquals(expected, SkillFormula.evaluate(skill.calc1, skill, level),
          "native Calc1 level " + level);
      assertEquals(expected, AmazonSkills.getPhysicalDamagePercent(skill, level),
          "riiablo weapon damage percent level " + level);
    }

    assertEquals(-15, AmazonSkills.getPhysicalDamagePercent(skill, 1));
    assertEquals(0, AmazonSkills.getPhysicalDamagePercent(skill, 6));
    assertEquals(42, AmazonSkills.getPhysicalDamagePercent(skill, 20));
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
