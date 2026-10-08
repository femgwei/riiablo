package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SorceressSkills;
import org.junit.jupiter.api.Test;

/** Seventeenth DMG-04 audit: one Shiver Armor melee-attack retaliation. */
class ShiverArmorGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_INTEGER_MIN = {
      6, 8, 10, 12, 14, 16, 18, 20, 23, 26,
      29, 32, 35, 38, 41, 44, 48, 52, 56, 60
  };
  private static final int[] D2MOO_INTEGER_MAX = {
      8, 10, 13, 15, 18, 20, 23, 25, 29, 32,
      36, 39, 43, 46, 50, 53, 58, 62, 67, 71
  };
  private static final int[] D2MOO_COLD_LENGTH = {
      100, 100, 100, 100, 100, 100, 100, 100, 125, 150,
      175, 200, 225, 250, 275, 300, 350, 400, 450, 500
  };

  @Test
  void levelOneToTwentyMatchesD2mooDirectEventFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.SHIVER_ARMOR);
    assertNotNull(skill);
    assertEquals("Shiver Armor", skill.skill);
    assertEquals(18, skill.srvdofunc);
    assertEquals("attackedinmelee", skill.auraevent[0]);
    assertEquals(3, skill.auraeventfunc[0]);
    assertEquals(7, skill.HitShift);
    assertEquals("cold", skill.EType);
    assertEquals(12, skill.EMin);
    assertEquals(16, skill.EMax);
    assertArrayEquals(new int[] {4, 6, 8, 10, 12}, skill.EMinLev);
    assertArrayEquals(new int[] {5, 7, 9, 11, 13}, skill.EMaxLev);
    assertEquals(100, skill.ELen);
    assertArrayEquals(new int[] {0, 25, 50}, skill.ELevLen);

    for (int level = 1; level <= 20; level++) {
      int fixedMin = d2mooElementalDamageFixed(
          skill.EMin, skill.EMinLev, skill.HitShift, level);
      int fixedMax = d2mooElementalDamageFixed(
          skill.EMax, skill.EMaxLev, skill.HitShift, level);
      assertEquals(D2MOO_INTEGER_MIN[level - 1], fixedMin >> 8,
          "D2MOO min level " + level);
      assertEquals(D2MOO_INTEGER_MAX[level - 1], fixedMax >> 8,
          "D2MOO max level " + level);
      assertArrayEquals(
          new int[] {D2MOO_INTEGER_MIN[level - 1], D2MOO_INTEGER_MAX[level - 1]},
          SorceressSkills.getArmorColdDamage(skill, level, name -> 0),
          "riiablo damage level " + level);
      assertEquals(D2MOO_COLD_LENGTH[level - 1],
          SorceressSkills.getArmorColdLength(skill, level, name -> 0),
          "cold length level " + level);
    }
  }

  @Test
  void nativeLimitedRandomRollTreatsGetterMaximumAsExclusive() {
    boolean sawMinimum = false;
    boolean sawMaximumMinusOne = false;
    for (int seed = 1; seed <= 256; seed++) {
      int rolled = StateUpdater.rollNativeDamage(new NativeRng(seed), 6, 8);
      assertTrue(rolled >= 6 && rolled < 8);
      sawMinimum |= rolled == 6;
      sawMaximumMinusOne |= rolled == 7;
    }
    assertTrue(sawMinimum);
    assertTrue(sawMaximumMinusOne);
    assertEquals(6, StateUpdater.rollNativeDamage(new NativeRng(1), 6, 6));
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
