package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Thirteenth DMG-04 conventional min/max audit: D2MOO SrvDo021 Telekinesis. */
class TelekinesisGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_INTEGER_MIN = {
      1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
      11, 12, 13, 14, 15, 16, 17, 18, 19, 20
  };
  private static final int[] D2MOO_INTEGER_MAX = {
      2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
      12, 13, 14, 15, 16, 17, 18, 19, 20, 21
  };

  @Test
  void levelOneToTwentyMatchesD2mooElementalFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.TELEKINESIS);
    assertNotNull(skill);
    assertEquals("Telekinesis", skill.skill);
    assertEquals(12, skill.srvstfunc);
    assertEquals(21, skill.srvdofunc);
    assertEquals(8, skill.HitShift);
    assertEquals("ltng", skill.EType);
    assertEquals(1, skill.EMin);
    assertEquals(2, skill.EMax);
    assertArrayEquals(new int[] {1, 1, 1, 1, 1}, skill.EMinLev);
    assertArrayEquals(new int[] {1, 1, 1, 1, 1}, skill.EMaxLev);
    assertTrue(skill.EDmgSymPerCalc == null || skill.EDmgSymPerCalc.isEmpty());

    for (int level = 1; level <= 20; level++) {
      int fixedMin = d2mooElementalDamageFixed(
          skill.EMin, skill.EMinLev, skill.HitShift, level);
      int fixedMax = d2mooElementalDamageFixed(
          skill.EMax, skill.EMaxLev, skill.HitShift, level);
      assertEquals(D2MOO_INTEGER_MIN[level - 1] << 8, fixedMin,
          "fixed min level " + level);
      assertEquals(D2MOO_INTEGER_MAX[level - 1] << 8, fixedMax,
          "fixed max level " + level);
      assertEquals(D2MOO_INTEGER_MIN[level - 1],
          MissileDamageResolver.skillElementalDamage(skill, level, true, name -> 0),
          "riiablo min level " + level);
      assertEquals(D2MOO_INTEGER_MAX[level - 1],
          MissileDamageResolver.skillElementalDamage(skill, level, false, name -> 0),
          "riiablo max level " + level);
      assertEquals(0,
          MissileDamageResolver.skillPhysicalDamage(skill, level, true, name -> 0));
      assertEquals(0,
          MissileDamageResolver.skillPhysicalDamage(skill, level, false, name -> 0));
    }
  }

  @Test
  void nativeLimitedRandomRollTreatsMaximumAsExclusive() {
    for (int i = 0; i < 100; i++) {
      assertEquals(20, ServerSkillSystem.rollNativeDamage(20, 21));
    }
    assertEquals(20, ServerSkillSystem.rollNativeDamage(20, 20));
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
