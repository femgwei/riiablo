package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/**
 * Eighth DMG-04 level-by-level golden audit.
 *
 * <p>The constants below are the direct result of the Diablo II 1.10f
 * D2Common formula, not values produced by riiablo. D2MOO references:
 * {@code D2Skills.cpp:2459 SKILLS_CalculateDamageBonusByLevel},
 * {@code D2Skills.cpp:2623 SKILLS_GetMinElemDamage},
 * {@code D2Skills.cpp:2685 SKILLS_GetMaxElemDamage}, and
 * {@code Units/Missile.cpp:467 MISSILE_CalculateDamageData}.</p>
 *
 * <p>The values describe one damage packet applied once to one target.
 * SrvDo022's 64-way delivery, cold duration, multi-target contact, and
 * cast-wide totals remain DMG-07 work.</p>
 */
class FrostNovaGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIXED_MIN = {
      512, 1024, 1536, 2048, 2560, 3072, 3584, 4096, 4864, 5632,
      6400, 7168, 7936, 8704, 9472, 10240, 11264, 12288, 13312, 14336
  };
  private static final int[] D2MOO_FIXED_MAX = {
      1024, 1664, 2304, 2944, 3584, 4224, 4864, 5504, 6400, 7296,
      8192, 9088, 9984, 10880, 11776, 12672, 13824, 14976, 16128, 17280
  };
  private static final int[] D2MOO_INTEGER_MIN = {
      2, 4, 6, 8, 10, 12, 14, 16, 19, 22,
      25, 28, 31, 34, 37, 40, 44, 48, 52, 56
  };
  private static final int[] D2MOO_INTEGER_MAX = {
      4, 6, 9, 11, 14, 16, 19, 21, 25, 28,
      32, 35, 39, 42, 46, 49, 54, 58, 63, 67
  };

  @Test
  void levelOneToTwentyMatchesD2mooFixedPointFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.FROST_NOVA);
    Missiles.Entry missile = Riiablo.files.Missiles.get("frostnova");
    assertNotNull(skill);
    assertNotNull(missile);
    assertEquals("Frost Nova", skill.skill);
    assertEquals(22, skill.srvdofunc);
    assertEquals("frostnova", skill.srvmissilea);
    assertEquals("Frost Nova", missile.Skill);
    assertEquals(0, skill.SrcDam);
    assertEquals(7, skill.HitShift);
    assertEquals("cold", skill.EType);
    assertEquals(4, skill.EMin);
    assertEquals(8, skill.EMax);
    assertArrayEquals(new int[] {4, 6, 8, 10, 12}, skill.EMinLev);
    assertArrayEquals(new int[] {5, 7, 9, 11, 13}, skill.EMaxLev);
    assertEquals(
        "(skill('Blizzard'.blvl)+skill('Frozen Orb'.blvl))*par8",
        skill.EDmgSymPerCalc);
    assertEquals(200, skill.ELen);
    assertArrayEquals(new int[] {25, 25, 25}, skill.ELevLen);
    assertEquals(1, missile.pSrvDoFunc);
    assertTrue(missile.NextHit);
    assertEquals(4, missile.NextDelay);
    assertTrue(missile.LastCollide);

    Attributes owner = Attributes.obtainStandard();
    for (int level = 1; level <= 20; level++) {
      int fixedMin = d2mooElementalDamageFixed(skill.EMin, skill.EMinLev,
          skill.HitShift, level);
      int fixedMax = d2mooElementalDamageFixed(skill.EMax, skill.EMaxLev,
          skill.HitShift, level);
      assertEquals(D2MOO_FIXED_MIN[level - 1], fixedMin, "fixed min level " + level);
      assertEquals(D2MOO_FIXED_MAX[level - 1], fixedMax, "fixed max level " + level);
      assertEquals(D2MOO_INTEGER_MIN[level - 1], fixedMin >> 8,
          "integer min level " + level);
      assertEquals(D2MOO_INTEGER_MAX[level - 1], fixedMax >> 8,
          "integer max level " + level);

      Missile projectile = new Missile();
      projectile.missile = missile;
      assertTrue(MissileDamageResolver.initializeSkill(
          projectile, skill, owner, level, name -> 0));
      assertEquals(D2MOO_INTEGER_MIN[level - 1], statInt(projectile, Stat.coldmindam),
          "riiablo cold min level " + level);
      assertEquals(D2MOO_INTEGER_MAX[level - 1], statInt(projectile, Stat.coldmaxdam),
          "riiablo cold max level " + level);
      assertEquals(D2MOO_INTEGER_MIN[level - 1],
          MissileDamageResolver.skillElementalDamage(skill, level, true, name -> 0),
          "riiablo displayed min level " + level);
      assertEquals(D2MOO_INTEGER_MAX[level - 1],
          MissileDamageResolver.skillElementalDamage(skill, level, false, name -> 0),
          "riiablo displayed max level " + level);
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

  private static int statInt(Missile projectile, short stat) {
    StatRef ref = projectile.damage.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }
}
