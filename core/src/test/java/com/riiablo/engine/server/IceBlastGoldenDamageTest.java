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
 * Fourth DMG-04 level-by-level golden audit.
 *
 * <p>The constants below are the direct result of the Diablo II 1.10f
 * D2Common formula, not values produced by riiablo. D2MOO references:
 * {@code D2Skills.cpp:2459 SKILLS_CalculateDamageBonusByLevel},
 * {@code D2Skills.cpp:2623 SKILLS_GetMinElemDamage},
 * {@code D2Skills.cpp:2685 SKILLS_GetMaxElemDamage}, and
 * {@code Units/Missile.cpp:467 MISSILE_CalculateDamageData}.</p>
 *
 * <p>The values describe one damage packet applied once to one target.
 * SrvDmg04 freeze conversion and cold duration remain DMG-07 work.</p>
 */
class IceBlastGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIXED_MIN = {
      2048, 3840, 5632, 7424, 9216, 11008, 12800, 14592, 18176, 21760,
      25344, 28928, 32512, 36096, 39680, 43264, 48640, 54016, 59392, 64768
  };
  private static final int[] D2MOO_FIXED_MAX = {
      3072, 4992, 6912, 8832, 10752, 12672, 14592, 16512, 20224, 23936,
      27648, 31360, 35072, 38784, 42496, 46208, 51712, 57216, 62720, 68224
  };
  private static final int[] D2MOO_INTEGER_MIN = {
      8, 15, 22, 29, 36, 43, 50, 57, 71, 85, 99, 113, 127, 141, 155, 169,
      190, 211, 232, 253
  };
  private static final int[] D2MOO_INTEGER_MAX = {
      12, 19, 27, 34, 42, 49, 57, 64, 79, 93, 108, 122, 137, 151, 166, 180,
      202, 223, 245, 266
  };

  @Test
  void levelOneToTwentyMatchesD2mooFixedPointFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.ICE_BLAST);
    Missiles.Entry missile = Riiablo.files.Missiles.get("iceblast");
    assertNotNull(skill);
    assertNotNull(missile);
    assertEquals("Ice Blast", skill.skill);
    assertEquals(0, skill.SrcDam);
    assertEquals(7, skill.HitShift);
    assertEquals("cold", skill.EType);
    assertEquals(16, skill.EMin);
    assertEquals(24, skill.EMax);
    assertArrayEquals(new int[] {14, 28, 42, 56, 70}, skill.EMinLev);
    assertArrayEquals(new int[] {15, 29, 43, 57, 71}, skill.EMaxLev);
    assertEquals(4, missile.pSrvDmgFunc,
        "D2MOO MISSMODE_SrvDmg04 owns Ice Blast's freeze conversion");

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
