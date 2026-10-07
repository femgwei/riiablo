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
 * Sixth DMG-04 level-by-level golden audit.
 *
 * <p>The constants below are the direct result of the Diablo II 1.10f
 * D2Common formula, not values produced by riiablo. D2MOO references:
 * {@code D2Skills.cpp:2459 SKILLS_CalculateDamageBonusByLevel},
 * {@code D2Skills.cpp:2623 SKILLS_GetMinElemDamage},
 * {@code D2Skills.cpp:2685 SKILLS_GetMaxElemDamage}, and
 * {@code Units/Missile.cpp:467 MISSILE_CalculateDamageData}.</p>
 *
 * <p>The values describe one damage packet applied once to one target.
 * Piercing, multi-target contact, and cast-wide totals remain DMG-06/07
 * work.</p>
 */
class LightningGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIXED_MIN = {
      256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
      256, 256, 256, 256, 256, 256, 256, 256, 256, 256
  };
  private static final int[] D2MOO_FIXED_MAX = {
      10240, 12288, 14336, 16384, 18432, 20480, 22528, 24576, 27648, 30720,
      33792, 36864, 39936, 43008, 46080, 49152, 54272, 59392, 64512, 69632
  };
  private static final int[] D2MOO_INTEGER_MIN = {
      1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1
  };
  private static final int[] D2MOO_INTEGER_MAX = {
      40, 48, 56, 64, 72, 80, 88, 96, 108, 120,
      132, 144, 156, 168, 180, 192, 212, 232, 252, 272
  };

  @Test
  void levelOneToTwentyMatchesD2mooFixedPointFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.LIGHTNING);
    Missiles.Entry missile = Riiablo.files.Missiles.get("lightningbolt");
    assertNotNull(skill);
    assertNotNull(missile);
    assertEquals("Lightning", skill.skill);
    assertEquals("lightningbolt", skill.srvmissile);
    assertEquals("Lightning", missile.Skill);
    assertEquals(0, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals("ltng", skill.EType);
    assertEquals(1, skill.EMin);
    assertEquals(40, skill.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMinLev);
    assertArrayEquals(new int[] {8, 12, 20, 28, 36}, skill.EMaxLev);
    assertEquals(
        "(skill('Charged Bolt'.blvl)+skill('Chain Lightning'.blvl)"
            + "+skill('Nova'.blvl))*par8",
        skill.EDmgSymPerCalc);

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
      assertEquals(D2MOO_INTEGER_MIN[level - 1], statInt(projectile, Stat.lightmindam),
          "riiablo lightning min level " + level);
      assertEquals(D2MOO_INTEGER_MAX[level - 1], statInt(projectile, Stat.lightmaxdam),
          "riiablo lightning max level " + level);
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
