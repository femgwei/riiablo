package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillFormula;
import org.junit.jupiter.api.Test;

/** Twenty-fifth DMG-04 audit: Multiple Shot owns per-lane weapon records. */
class MultipleShotGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_LANE_COUNT = {
      2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
      12, 13, 14, 15, 16, 17, 18, 19, 20, 21
  };

  @Test
  void levelOneToTwentyUseNativeLaneCountsWithoutOwningFixedDamage() {
    Skills.Entry skill = Riiablo.files.skills.get("Multiple Shot");
    assertNotNull(skill);
    assertEquals(12, skill.Id);
    assertEquals(4, skill.srvstfunc);
    assertEquals(8, skill.srvdofunc);
    assertEquals(96, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals(0, skill.ToHit);
    assertEquals(0, skill.LevToHit);
    assertEquals("\"min(24,ln12)\"", skill.calc1);
    assertEquals("par3", skill.calc2);
    assertEquals("2", skill.calc3);
    assertTrue(blank(skill.calc4));
    assertArrayEquals(new int[] {2, 1, 1, 0, 0, 0, 0, 0}, skill.Param);
    assertTrue(blank(skill.srvmissile));
    assertEquals("multipleshotarrow", skill.srvmissilea);
    assertEquals("multipleshotbolt", skill.srvmissileb);
    assertTrue(blank(skill.srvmissilec));
    assertTrue(blank(skill.srvmissiled));

    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MaxLevDam);
    assertTrue(blank(skill.EType));
    assertEquals(0, skill.EMin);
    assertEquals(0, skill.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMinLev);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMaxLev);

    for (int level = 1; level <= 20; level++) {
      assertEquals(D2MOO_LANE_COUNT[level - 1],
          SkillFormula.evaluate(skill.calc1, skill, level),
          "native total lane count level " + level);
      assertEquals(1, SkillFormula.evaluate(skill.calc2, skill, level),
          "native missile activation frame level " + level);
      assertEquals(2, SkillFormula.evaluate(skill.calc3, skill, level),
          "native centre lane group level " + level);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
