package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/**
 * Sixteenth DMG-04 level-by-level audit: one Inferno stream missile hitting
 * one target once.
 *
 * <p>The constants are the direct 8.8 fixed-point result of the Diablo II
 * 1.10f D2Common skill formula. D2MOO references:
 * {@code SkillSor.cpp:50 SKILLS_DoInferno},
 * {@code SkillSor.cpp:124 SKILLS_StartInferno},
 * {@code SkillSor.cpp:177 SKILLS_SrvSt11_Inferno_ArcticBlast},
 * {@code SkillSor.cpp:394 SKILLS_SrvDo019_Inferno_ArcticBlast},
 * {@code D2Skills.cpp:2459 SKILLS_CalculateDamageBonusByLevel},
 * {@code D2Skills.cpp:2623 SKILLS_GetMinElemDamage},
 * {@code D2Skills.cpp:2685 SKILLS_GetMaxElemDamage}, and
 * {@code Units/Missile.cpp:467 MISSILE_CalculateDamageData}.</p>
 */
class InfernoGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIXED_MIN = {
      128, 224, 320, 416, 512, 608, 704, 800, 904, 1008,
      1112, 1216, 1320, 1424, 1528, 1632, 1744, 1856, 1968, 2080
  };
  private static final int[] D2MOO_FIXED_MAX = {
      256, 352, 448, 544, 640, 736, 832, 928, 1036, 1144,
      1252, 1360, 1468, 1576, 1684, 1792, 1908, 2024, 2140, 2256
  };

  @Test
  void levelOneToTwentyMatchesD2mooPerStreamMissileFixedPointFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.INFERNO);
    Missiles.Entry stream = Riiablo.files.Missiles.get("infernoflame1");
    assertNotNull(skill);
    assertNotNull(stream);
    assertEquals("Inferno", skill.skill);
    assertEquals(11, skill.srvstfunc);
    assertEquals(19, skill.srvdofunc);
    assertEquals("infernoflame1", skill.srvmissilea);
    assertEquals(0, skill.SrcDam);
    assertEquals(2, skill.HitShift);
    assertEquals("fire", skill.EType);
    assertEquals(32, skill.EMin);
    assertEquals(64, skill.EMax);
    assertArrayEquals(new int[] {24, 26, 28, 32, 36}, skill.EMinLev);
    assertArrayEquals(new int[] {24, 27, 29, 33, 37}, skill.EMaxLev);

    Attributes owner = Attributes.obtainStandard();
    for (int level = 1; level <= 20; level++) {
      int fixedMin = d2mooElementalDamageFixed(
          skill.EMin, skill.EMinLev, skill.HitShift, level);
      int fixedMax = d2mooElementalDamageFixed(
          skill.EMax, skill.EMaxLev, skill.HitShift, level);
      assertEquals(D2MOO_FIXED_MIN[level - 1], fixedMin, "fixed min level " + level);
      assertEquals(D2MOO_FIXED_MAX[level - 1], fixedMax, "fixed max level " + level);

      Missile projectile = new Missile();
      projectile.missile = stream;
      assertTrue(MissileDamageResolver.initializeSorceressFireArea(
          projectile, skill, owner, true, level, name -> 0));
      assertEquals(D2MOO_FIXED_MIN[level - 1], projectile.elementalMinRateFixed,
          "riiablo fixed min level " + level);
      assertEquals(D2MOO_FIXED_MAX[level - 1], projectile.elementalMaxRateFixed,
          "riiablo fixed max level " + level);
      assertEquals(stream.DamageRate, projectile.elementalDamageRate);
      assertTrue(projectile.fixedElementalRate);
    }
  }

  private static int d2mooElementalDamageFixed(
      int base, int[] perLevel, int hitShift, int level) {
    return (base + d2mooDamageBonusByLevel(level, perLevel)) << hitShift;
  }

  private static int d2mooDamageBonusByLevel(int level, int[] values) {
    if (level <= 1) return 0;
    if (level > 28) {
      return 7 * values[0] + values[4] * (level - 28)
          + 6 * (values[2] + values[3]) + 8 * values[1];
    }
    if (level > 22) {
      return 7 * values[0] + values[3] * (level - 22)
          + 6 * values[2] + 8 * values[1];
    }
    if (level > 16) {
      return 7 * values[0] + values[2] * (level - 16) + 8 * values[1];
    }
    if (level > 8) return 7 * values[0] + values[1] * (level - 8);
    return values[0] * (level - 1);
  }
}
