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

/** Thirtieth DMG-04 audit: Slow Missiles is a non-damaging projectile-speed state. */
class SlowMissilesGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_VELOCITY_PERCENT = {
      33, 33, 33, 33, 33, 33, 33, 33, 33, 33,
      33, 33, 33, 33, 33, 33, 33, 33, 33, 33
  };
  private static final int[] D2MOO_DURATION_FRAMES = {
      300, 450, 600, 750, 900, 1050, 1200, 1350, 1500, 1650,
      1800, 1950, 2100, 2250, 2400, 2550, 2700, 2850, 3000, 3150
  };

  @Test
  void levelOneToTwentyAreNondamagingNativeProjectileVelocityDebuffs() {
    Skills.Entry skill = Riiablo.files.skills.get("Slow Missiles");
    assertNotNull(skill);
    assertEquals(17, skill.Id);
    assertEquals(0, skill.srvstfunc);
    assertEquals(6, skill.srvdofunc);
    assertEquals(0, skill.SrcDam);
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

    assertEquals("slowmissiles", skill.auratargetstate);
    assertEquals("ln12", skill.aurastatcalc[0]);
    assertEquals("skill_handofathena", skill.aurastat[0]);
    assertEquals("ln34", skill.auralencalc);
    assertEquals("ln56", skill.aurarangecalc);
    assertArrayEquals(new int[] {33, 0, 300, 150, 20, 0, 0, 0}, skill.Param);

    for (int level = 1; level <= 20; level++) {
      assertEquals(D2MOO_VELOCITY_PERCENT[level - 1],
          SkillFormula.evaluate(skill.aurastatcalc[0], skill, level),
          "native remaining missile velocity percent level " + level);
      assertEquals(D2MOO_VELOCITY_PERCENT[level - 1],
          AmazonSkills.getSlowMissilesPercent(level),
          "riiablo remaining missile velocity percent level " + level);
      assertEquals(D2MOO_DURATION_FRAMES[level - 1],
          SkillFormula.evaluate(skill.auralencalc, skill, level),
          "native state duration level " + level);
      assertEquals(20, SkillFormula.evaluate(skill.aurarangecalc, skill, level),
          "native aura range level " + level);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
