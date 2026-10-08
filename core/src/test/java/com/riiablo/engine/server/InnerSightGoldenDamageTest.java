package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.AmazonSkills;
import com.riiablo.engine.server.skill.SkillFormula;
import org.junit.jupiter.api.Test;

/** Twenty-first DMG-04 audit: Inner Sight is a non-damaging defense state. */
class InnerSightGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_DEFENSE_REDUCTION = {
      40, 65, 90, 115, 140, 165, 190, 215, 260, 305,
      350, 395, 440, 485, 530, 575, 635, 695, 755, 815
  };

  @Test
  void levelOneToTwentyAreNondamagingNativeDefenseDebuffs() {
    Skills.Entry skill = Riiablo.files.skills.get("Inner Sight");
    assertNotNull(skill);
    assertEquals(8, skill.Id);
    assertEquals(6, skill.srvdofunc);
    assertEquals(0, skill.SrcDam);
    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertTrue(blank(skill.EType));
    assertTrue(blank(skill.srvmissile));
    assertTrue(blank(skill.srvmissilea));
    assertTrue(blank(skill.srvmissileb));
    assertTrue(blank(skill.srvmissilec));
    assertTrue(blank(skill.srvmissiled));

    assertEquals("-edmn", skill.aurastatcalc[0]);
    assertEquals(40, skill.EMin);
    assertEquals(0, skill.EMax);
    assertArrayEquals(new int[] {25, 45, 60, 80, 100}, skill.EMinLev);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMaxLev);

    for (int level = 1; level <= 20; level++) {
      int expected = D2MOO_DEFENSE_REDUCTION[level - 1];
      final int nativeLevel = level;
      assertEquals(expected, SkillFormula.evaluate("edmn", skill, level),
          "native unshifted elemental-minimum token level " + level);
      assertEquals(-expected,
          SkillFormula.evaluate(skill.aurastatcalc[0], skill, level),
          "native AuraStatCalc level " + level);
      assertEquals(expected,
          SkillFormula.evaluate("skill('Inner Sight'.edmn)", skill, 1,
              name -> nativeLevel, name -> skill),
          "referenced native elemental-minimum token level " + level);
      assertEquals(expected,
          AmazonSkills.calculateInnerSightDefenseReduce(skill, level),
          "riiablo flat defense reduction level " + level);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
