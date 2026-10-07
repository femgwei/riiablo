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
 * Third DMG-04 level-by-level golden audit.
 *
 * <p>The constants below are the direct result of the Diablo II 1.10f
 * D2Common formula, not values produced by riiablo. D2MOO references:
 * {@code D2Skills.cpp:2459 SKILLS_CalculateDamageBonusByLevel},
 * {@code D2Skills.cpp:2623 SKILLS_GetMinElemDamage},
 * {@code D2Skills.cpp:2685 SKILLS_GetMaxElemDamage}, and
 * {@code Units/Missile.cpp:467 MISSILE_CalculateDamageData}.</p>
 *
 * <p>The values describe one damage packet applied once to one target.
 * SrvHit01 area fan-out and cast-wide multi-target totals remain DMG-07 work.</p>
 */
class FireBallGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIXED_MIN = {
      1536, 3200, 4864, 6528, 8192, 9856, 11520, 13184, 16128, 19072,
      22016, 24960, 27904, 30848, 33792, 36736, 40320, 43904, 47488, 51072
  };
  private static final int[] D2MOO_FIXED_MAX = {
      3584, 5504, 7424, 9344, 11264, 13184, 15104, 17024, 20224, 23424,
      26624, 29824, 33024, 36224, 39424, 42624, 46464, 50304, 54144, 57984
  };
  private static final int[] D2MOO_INTEGER_MIN = {
      6, 12, 19, 25, 32, 38, 45, 51, 63, 74, 86, 97, 109, 120, 132, 143,
      157, 171, 185, 199
  };
  private static final int[] D2MOO_INTEGER_MAX = {
      14, 21, 29, 36, 44, 51, 59, 66, 79, 91, 104, 116, 129, 141, 154, 166,
      181, 196, 211, 226
  };

  @Test
  void levelOneToTwentyMatchesD2mooFixedPointFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.FIRE_BALL);
    Missiles.Entry missile = Riiablo.files.Missiles.get("fireball");
    assertNotNull(skill);
    assertNotNull(missile);
    assertEquals("Fire Ball", skill.skill);
    assertEquals(0, skill.SrcDam);
    assertEquals(7, skill.HitShift);
    assertEquals("fire", skill.EType);
    assertEquals(12, skill.EMin);
    assertEquals(28, skill.EMax);
    assertArrayEquals(new int[] {13, 23, 28, 33, 38}, skill.EMinLev);
    assertArrayEquals(new int[] {15, 25, 30, 35, 40}, skill.EMaxLev);
    assertEquals(1, missile.pSrvHitFunc);
    assertEquals("explodingarrowexp", missile.ExplosionMissile);

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
      assertEquals(D2MOO_INTEGER_MIN[level - 1], statInt(projectile, Stat.firemindam),
          "riiablo fire min level " + level);
      assertEquals(D2MOO_INTEGER_MAX[level - 1], statInt(projectile, Stat.firemaxdam),
          "riiablo fire max level " + level);
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
