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
 * Seventh DMG-04 level-by-level golden audit.
 *
 * <p>The constants below are the direct result of the Diablo II 1.10f
 * D2Common formula, not values produced by riiablo. D2MOO references:
 * {@code D2Skills.cpp:2459 SKILLS_CalculateDamageBonusByLevel},
 * {@code D2Skills.cpp:2623 SKILLS_GetMinElemDamage},
 * {@code D2Skills.cpp:2685 SKILLS_GetMaxElemDamage}, and
 * {@code Units/Missile.cpp:467 MISSILE_CalculateDamageData}.</p>
 *
 * <p>The values describe one damage packet applied once to one target.
 * SrvDo022's 64-way delivery, multi-target contact, and cast-wide totals
 * remain DMG-07 work.</p>
 */
class NovaGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIXED_MIN = {
      256, 1792, 3328, 4864, 6400, 7936, 9472, 11008, 12800, 14592,
      16384, 18176, 19968, 21760, 23552, 25344, 27392, 29440, 31488, 33536
  };
  private static final int[] D2MOO_FIXED_MAX = {
      5120, 7168, 9216, 11264, 13312, 15360, 17408, 19456, 21760, 24064,
      26368, 28672, 30976, 33280, 35584, 37888, 40448, 43008, 45568, 48128
  };
  private static final int[] D2MOO_INTEGER_MIN = {
      1, 7, 13, 19, 25, 31, 37, 43, 50, 57,
      64, 71, 78, 85, 92, 99, 107, 115, 123, 131
  };
  private static final int[] D2MOO_INTEGER_MAX = {
      20, 28, 36, 44, 52, 60, 68, 76, 85, 94,
      103, 112, 121, 130, 139, 148, 158, 168, 178, 188
  };

  @Test
  void levelOneToTwentyMatchesD2mooFixedPointFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.NOVA);
    Missiles.Entry missile = Riiablo.files.Missiles.get("nova");
    assertNotNull(skill);
    assertNotNull(missile);
    assertEquals("Nova", skill.skill);
    assertEquals(22, skill.srvdofunc);
    assertEquals("nova", skill.srvmissilea);
    assertEquals("Nova", missile.Skill);
    assertEquals(0, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals("ltng", skill.EType);
    assertEquals(1, skill.EMin);
    assertEquals(20, skill.EMax);
    assertArrayEquals(new int[] {6, 7, 8, 9, 10}, skill.EMinLev);
    assertArrayEquals(new int[] {8, 9, 10, 11, 12}, skill.EMaxLev);
    assertTrue(skill.EDmgSymPerCalc == null || skill.EDmgSymPerCalc.isEmpty());
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
