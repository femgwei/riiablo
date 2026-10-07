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
 * Second DMG-04 level-by-level golden audit.
 *
 * <p>The constants below are the direct result of the Diablo II 1.10f
 * D2Common formula, not values produced by riiablo. D2MOO references:
 * {@code D2Skills.cpp:2459 SKILLS_CalculateDamageBonusByLevel},
 * {@code D2Skills.cpp:2623 SKILLS_GetMinElemDamage},
 * {@code D2Skills.cpp:2685 SKILLS_GetMaxElemDamage}, and
 * {@code Units/Missile.cpp:467 MISSILE_CalculateDamageData}.</p>
 *
 * <p>Ice Bolt's cold duration is deliberately outside this damage-only
 * golden case and remains part of DMG-07.</p>
 */
class IceBoltGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIXED_MIN = {
      768, 1024, 1280, 1536, 1792, 2048, 2304, 2560, 3072, 3584,
      4096, 4608, 5120, 5632, 6144, 6656, 7424, 8192, 8960, 9728
  };
  private static final int[] D2MOO_FIXED_MAX = {
      1280, 1664, 2048, 2432, 2816, 3200, 3584, 3968, 4608, 5248,
      5888, 6528, 7168, 7808, 8448, 9088, 9984, 10880, 11776, 12672
  };
  private static final int[] D2MOO_INTEGER_MIN = {
      3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16, 18, 20, 22, 24, 26, 29, 32, 35, 38
  };
  private static final int[] D2MOO_INTEGER_MAX = {
      5, 6, 8, 9, 11, 12, 14, 15, 18, 20, 23, 25, 28, 30, 33, 35, 39, 42, 46, 49
  };

  @Test
  void levelOneToTwentyMatchesD2mooFixedPointFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.ICE_BOLT);
    Missiles.Entry missile = Riiablo.files.Missiles.get("icebolt");
    assertNotNull(skill);
    assertNotNull(missile);
    assertEquals("Ice Bolt", skill.skill);
    assertEquals(0, skill.SrcDam);
    assertEquals(7, skill.HitShift);
    assertEquals("cold", skill.EType);
    assertEquals(6, skill.EMin);
    assertEquals(10, skill.EMax);
    assertArrayEquals(new int[] {2, 4, 6, 8, 10}, skill.EMinLev);
    assertArrayEquals(new int[] {3, 5, 7, 9, 11}, skill.EMaxLev);

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
