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
 * Fourteenth DMG-04 level-by-level audit: one Blaze ground missile, one target,
 * one game frame.
 *
 * <p>The constants are the direct 8.8 fixed-point result of the Diablo II 1.10f
 * D2Common skill formula. D2MOO references:
 * {@code SkillSor.cpp:604 SKILLS_SrvDo023_Blaze_EnergyShield_SpiderLay},
 * {@code SkillSor.cpp:650 SKILLS_CreateBlazeMissile},
 * {@code D2Skills.cpp:2459 SKILLS_CalculateDamageBonusByLevel},
 * {@code D2Skills.cpp:2623 SKILLS_GetMinElemDamage},
 * {@code D2Skills.cpp:2685 SKILLS_GetMaxElemDamage},
 * {@code Units/Missile.cpp:467 MISSILE_CalculateDamageData}, and
 * {@code MissMode.cpp:811 MISSMODE_SrvDo05_FireWall_ImmolationFire_MeteorFire}.</p>
 */
class BlazeGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIXED_MIN = {
      64, 96, 128, 160, 192, 224, 256, 288, 336, 384,
      432, 480, 528, 576, 624, 672, 736, 800, 864, 928
  };
  private static final int[] D2MOO_FIXED_MAX = {
      128, 160, 192, 224, 256, 288, 320, 352, 400, 448,
      496, 544, 592, 640, 688, 736, 800, 864, 928, 992
  };

  @Test
  void levelOneToTwentyMatchesD2mooPerFrameFixedPointFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.BLAZE);
    Missiles.Entry row = Riiablo.files.Missiles.get("blaze");
    assertNotNull(skill);
    assertNotNull(row);
    assertEquals("Blaze", skill.skill);
    assertEquals(23, skill.srvdofunc);
    assertEquals("blaze", skill.srvmissilea);
    assertEquals(4, skill.HitShift);
    assertEquals("fire", skill.EType);
    assertEquals(4, skill.EMin);
    assertEquals(8, skill.EMax);
    assertArrayEquals(new int[] {2, 3, 4, 6, 9}, skill.EMinLev);
    assertArrayEquals(new int[] {2, 3, 4, 6, 9}, skill.EMaxLev);
    assertTrue(skill.EDmgSymPerCalc == null || skill.EDmgSymPerCalc.isEmpty());
    assertEquals(5, row.pSrvDoFunc);
    assertEquals(3, row.pSrvDmgFunc);
    assertEquals(0, row.DamageRate,
        "Blaze DamageRate does not delay its once-per-game-frame collision");

    Attributes owner = Attributes.obtainStandard();
    for (int level = 1; level <= 20; level++) {
      int fixedMin = d2mooElementalDamageFixed(
          skill.EMin, skill.EMinLev, skill.HitShift, level);
      int fixedMax = d2mooElementalDamageFixed(
          skill.EMax, skill.EMaxLev, skill.HitShift, level);
      assertEquals(D2MOO_FIXED_MIN[level - 1], fixedMin, "fixed min level " + level);
      assertEquals(D2MOO_FIXED_MAX[level - 1], fixedMax, "fixed max level " + level);

      Missile projectile = new Missile();
      projectile.missile = row;
      projectile.tickInterval = 1;
      assertTrue(MissileDamageResolver.initializeSorceressFireArea(
          projectile, skill, owner, true, level, name -> 0));
      assertEquals(D2MOO_FIXED_MIN[level - 1], projectile.elementalMinRateFixed,
          "riiablo fixed min rate level " + level);
      assertEquals(D2MOO_FIXED_MAX[level - 1], projectile.elementalMaxRateFixed,
          "riiablo fixed max rate level " + level);
      assertEquals(1, projectile.tickInterval, "one collision pass per game frame");
      assertEquals(0, projectile.elementalDamageRate,
          "DamageRate is a flat DR/MDR scale, not an application cadence");
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
