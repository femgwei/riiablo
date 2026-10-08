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
 * Fifteenth DMG-04 level-by-level audit: one Fire Wall segment, one target,
 * one game frame.
 *
 * <p>The constants are the direct 8.8 fixed-point result of the Diablo II 1.10f
 * D2Common skill formula. D2MOO references:
 * {@code SkillSor.cpp:694 SKILLS_SrvDo024_FireWall},
 * {@code MissMode.cpp:811 MISSMODE_SrvDo05_FireWall_ImmolationFire_MeteorFire},
 * {@code MissMode.cpp:845 MISSMODE_SrvDo06_MoltenBoulder_FireWallMaker},
 * {@code D2Skills.cpp:2459 SKILLS_CalculateDamageBonusByLevel},
 * {@code D2Skills.cpp:2623 SKILLS_GetMinElemDamage},
 * {@code D2Skills.cpp:2685 SKILLS_GetMaxElemDamage}, and
 * {@code Units/Missile.cpp:467 MISSILE_CalculateDamageData}.</p>
 */
class FireWallGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIXED_MIN = {
      240, 384, 528, 672, 816, 960, 1104, 1248, 1472, 1696,
      1920, 2144, 2368, 2592, 2816, 3040, 3376, 3712, 4048, 4384
  };
  private static final int[] D2MOO_FIXED_MAX = {
      320, 464, 608, 752, 896, 1040, 1184, 1328, 1552, 1776,
      2000, 2224, 2448, 2672, 2896, 3120, 3456, 3792, 4128, 4464
  };

  @Test
  void levelOneToTwentyMatchesD2mooPerFrameFixedPointFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.FIRE_WALL);
    Missiles.Entry maker = Riiablo.files.Missiles.get("firewallmaker");
    Missiles.Entry fire = Riiablo.files.Missiles.get("firewall");
    assertNotNull(skill);
    assertNotNull(maker);
    assertNotNull(fire);
    assertEquals("Fire Wall", skill.skill);
    assertEquals(24, skill.srvdofunc);
    assertEquals("firewallmaker", skill.srvmissilea);
    assertEquals("firewall", skill.srvmissileb);
    assertEquals(4, skill.HitShift);
    assertEquals("fire", skill.EType);
    assertEquals(15, skill.EMin);
    assertEquals(20, skill.EMax);
    assertArrayEquals(new int[] {9, 14, 21, 21, 21}, skill.EMinLev);
    assertArrayEquals(new int[] {9, 14, 21, 21, 21}, skill.EMaxLev);
    assertEquals(6, maker.pSrvDoFunc);
    assertEquals("firewall", maker.SubMissile[0]);
    assertEquals(5, fire.pSrvDoFunc);
    assertEquals(3, fire.pSrvDmgFunc);
    assertEquals(41, fire.DamageRate,
        "DamageRate scales flat DR/MDR and does not delay per-frame collision");

    Attributes owner = Attributes.obtainStandard();
    for (int level = 1; level <= 20; level++) {
      int fixedMin = d2mooElementalDamageFixed(
          skill.EMin, skill.EMinLev, skill.HitShift, level);
      int fixedMax = d2mooElementalDamageFixed(
          skill.EMax, skill.EMaxLev, skill.HitShift, level);
      assertEquals(D2MOO_FIXED_MIN[level - 1], fixedMin, "fixed min level " + level);
      assertEquals(D2MOO_FIXED_MAX[level - 1], fixedMax, "fixed max level " + level);

      Missile projectile = new Missile();
      projectile.missile = fire;
      projectile.tickInterval = 1;
      assertTrue(MissileDamageResolver.initializeSorceressFireArea(
          projectile, skill, owner, true, level, name -> 0));
      assertEquals(D2MOO_FIXED_MIN[level - 1], projectile.elementalMinRateFixed,
          "riiablo fixed min rate level " + level);
      assertEquals(D2MOO_FIXED_MAX[level - 1], projectile.elementalMaxRateFixed,
          "riiablo fixed max rate level " + level);
      assertEquals(1, projectile.tickInterval, "one collision pass per game frame");
      assertEquals(41, projectile.elementalDamageRate,
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
