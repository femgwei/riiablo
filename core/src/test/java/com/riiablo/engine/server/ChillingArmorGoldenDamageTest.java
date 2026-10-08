package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.MathUtils;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Eighteenth DMG-04 audit: one Chilling Armor return missile. */
class ChillingArmorGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_INTEGER_MIN = {
      4, 5, 6, 7, 8, 9, 10, 11, 13, 15,
      17, 19, 21, 23, 25, 27, 30, 33, 36, 39
  };
  private static final int[] D2MOO_INTEGER_MAX = {
      6, 7, 9, 10, 12, 13, 15, 16, 19, 21,
      24, 26, 29, 31, 34, 36, 40, 43, 47, 50
  };
  private static final int[] D2MOO_COLD_LENGTH = {
      100, 100, 100, 100, 100, 100, 100, 100, 125, 150,
      175, 200, 225, 250, 275, 300, 325, 350, 375, 400
  };

  @Test
  void levelOneToTwentyMatchesD2mooReturnMissileFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.CHILLING_ARMOR);
    Missiles.Entry row = Riiablo.files.Missiles.get("chillingarmorbolt");
    assertNotNull(skill);
    assertNotNull(row);
    assertEquals("Chilling Armor", skill.skill);
    assertEquals(18, skill.srvdofunc);
    assertEquals("hitbymissile", skill.auraevent[0]);
    assertEquals(1, skill.auraeventfunc[0]);
    assertEquals("chillingarmorbolt", skill.srvmissilea);
    assertEquals("Chilling Armor", row.Skill);
    assertFalse(row.ReturnFire, "the return bolt must not recursively trigger Chilling Armor");
    assertEquals(0, skill.SrcDam);
    assertEquals(7, skill.HitShift);
    assertEquals("cold", skill.EType);
    assertEquals(8, skill.EMin);
    assertEquals(12, skill.EMax);
    assertArrayEquals(new int[] {2, 4, 6, 8, 10}, skill.EMinLev);
    assertArrayEquals(new int[] {3, 5, 7, 9, 11}, skill.EMaxLev);
    assertEquals(100, skill.ELen);
    assertArrayEquals(new int[] {0, 25, 25}, skill.ELevLen);

    Attributes owner = Attributes.obtainStandard();
    for (int level = 1; level <= 20; level++) {
      int fixedMin = d2mooElementalDamageFixed(
          skill.EMin, skill.EMinLev, skill.HitShift, level);
      int fixedMax = d2mooElementalDamageFixed(
          skill.EMax, skill.EMaxLev, skill.HitShift, level);
      assertEquals(D2MOO_INTEGER_MIN[level - 1], fixedMin >> 8,
          "D2MOO min level " + level);
      assertEquals(D2MOO_INTEGER_MAX[level - 1], fixedMax >> 8,
          "D2MOO max level " + level);

      Missile projectile = new Missile();
      projectile.missile = row;
      assertTrue(MissileDamageResolver.initializeSkill(
          projectile, skill, owner, level, name -> 0));
      assertEquals(SkillId.CHILLING_ARMOR, projectile.skillId,
          "skill id level " + level);
      assertEquals(level, projectile.damageLevel, "skill level " + level);
      assertEquals(D2MOO_INTEGER_MIN[level - 1],
          projectile.damage.get(Stat.coldmindam).asInt(), "riiablo min level " + level);
      assertEquals(D2MOO_INTEGER_MAX[level - 1],
          projectile.damage.get(Stat.coldmaxdam).asInt(), "riiablo max level " + level);
      assertEquals(D2MOO_COLD_LENGTH[level - 1],
          projectile.damage.get(Stat.coldlength).asInt(), "cold length level " + level);
    }
  }

  @Test
  void nativeMissileRollTreatsGetterMaximumAsExclusive() {
    MathUtils.random.setSeed(0xC4111A6L);
    boolean sawMinimum = false;
    boolean sawMaximumMinusOne = false;
    for (int i = 0; i < 256; i++) {
      int rolled = CombatSystem.rollNativeMissileDamage(4, 6);
      assertTrue(rolled >= 4 && rolled < 6);
      sawMinimum |= rolled == 4;
      sawMaximumMinusOne |= rolled == 5;
    }
    assertTrue(sawMinimum);
    assertTrue(sawMaximumMinusOne);
    assertEquals(4, CombatSystem.rollNativeMissileDamage(4, 4));
    assertEquals(4, CombatSystem.rollNativeMissileDamage(4, 5));
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
