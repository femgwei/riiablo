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
 * Eleventh DMG-04 level-by-level golden audit.
 *
 * <p>The constants below are the direct result of the Diablo II 1.10f
 * D2Common formula, not values produced by riiablo. D2MOO references:
 * {@code SkillSor.cpp:222 SKILLS_SrvSt13_ThunderStorm},
 * {@code SkillSor.cpp:918 SKILLS_SrvDo029_ThunderStorm},
 * {@code D2Skills.cpp:2459 SKILLS_CalculateDamageBonusByLevel},
 * {@code D2Skills.cpp:2623 SKILLS_GetMinElemDamage},
 * {@code D2Skills.cpp:2685 SKILLS_GetMaxElemDamage}, and
 * {@code Units/Missile.cpp:467 MISSILE_CalculateDamageData}.</p>
 *
 * <p>The values describe one periodic strike against one target. Aura
 * duration, strike interval, target selection, number of strikes, resistances,
 * Lightning Mastery, and full-aura totals remain later audit work.</p>
 */
class ThunderStormGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIXED_MIN = {
      256, 2816, 5376, 7936, 10496, 13056, 15616, 18176, 20736, 23296,
      25856, 28416, 30976, 33536, 36096, 38656, 41472, 44288, 47104, 49920
  };
  private static final int[] D2MOO_FIXED_MAX = {
      25600, 28160, 30720, 33280, 35840, 38400, 40960, 43520, 46080, 48640,
      51200, 53760, 56320, 58880, 61440, 64000, 66816, 69632, 72448, 75264
  };
  private static final int[] D2MOO_INTEGER_MIN = {
      1, 11, 21, 31, 41, 51, 61, 71, 81, 91,
      101, 111, 121, 131, 141, 151, 162, 173, 184, 195
  };
  private static final int[] D2MOO_INTEGER_MAX = {
      100, 110, 120, 130, 140, 150, 160, 170, 180, 190,
      200, 210, 220, 230, 240, 250, 261, 272, 283, 294
  };

  @Test
  void levelOneToTwentyMatchesD2mooFixedPointFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.THUNDER_STORM);
    Missiles.Entry missile = Riiablo.files.Missiles.get("thunderstorm1");
    assertNotNull(skill);
    assertNotNull(missile);
    assertEquals("Thunder Storm", skill.skill);
    assertEquals(13, skill.srvstfunc);
    assertEquals(29, skill.srvdofunc);
    assertEquals("thunderstorm1", skill.srvmissilea);
    assertEquals("thunderstorm1", missile.Missile);
    assertEquals("Thunder Storm", missile.Skill);
    assertEquals(3, missile.pSrvDoFunc);
    assertEquals(0, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals("ltng", skill.EType);
    assertEquals(1, skill.EMin);
    assertEquals(100, skill.EMax);
    assertArrayEquals(new int[] {10, 10, 11, 11, 11}, skill.EMinLev);
    assertArrayEquals(new int[] {10, 10, 11, 11, 11}, skill.EMaxLev);
    assertTrue(skill.EDmgSymPerCalc == null || skill.EDmgSymPerCalc.isEmpty());

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

      Missile strike = new Missile();
      strike.missile = missile;
      assertTrue(MissileDamageResolver.initializeSkill(
          strike, skill, owner, level, name -> 0));
      assertEquals(D2MOO_INTEGER_MIN[level - 1], statInt(strike, Stat.lightmindam),
          "riiablo lightning min level " + level);
      assertEquals(D2MOO_INTEGER_MAX[level - 1], statInt(strike, Stat.lightmaxdam),
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
