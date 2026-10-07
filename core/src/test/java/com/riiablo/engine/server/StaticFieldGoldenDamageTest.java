package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SorceressSkills;
import org.junit.jupiter.api.Test;

/**
 * Twelfth DMG-04 level-by-level golden audit.
 *
 * <p>Static Field does not own a conventional minimum/maximum damage curve.
 * Diablo II 1.10f evaluates {@code calc1} as a percentage of the target's
 * integer current life and {@code calc2} as a minimum signed 8.8 damage value.
 * D2MOO references: {@code SkillSor.cpp:406 SKILLS_SrvDo020_StaticField} and
 * {@code SkillSor.cpp:438 SKILLS_AuraCallback_StaticField}.</p>
 *
 * <p>The level matrix fixes one target at 100/100 life in Normal Expansion,
 * with zero lightning resistance. Skill level changes the aura radius but not
 * the 25-point single-target packet. Target count, repeated casts, resistance,
 * absorb, difficulty floors, and PvP settlement remain separate scenarios.</p>
 */
class StaticFieldGoldenDamageTest extends RiiabloTest {
  private static final int CURRENT_LIFE_FIXED = 100 << 8;
  private static final int MAXIMUM_LIFE_FIXED = 100 << 8;
  private static final int DAMAGE_PERCENT = 25;
  private static final int EXPECTED_DAMAGE_FIXED = 25 << 8;

  @Test
  void levelOneToTwentyMatchesD2mooCurrentLifeFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.STATIC_FIELD);
    assertNotNull(skill);
    assertEquals(42, skill.Id);
    assertEquals("Static Field", skill.skill);
    assertEquals(0, skill.srvstfunc);
    assertEquals(20, skill.srvdofunc);
    assertEquals("ltng", skill.EType);
    assertEquals("par4", skill.calc1);
    assertEquals("par3", skill.calc2);
    assertEquals("ln12", skill.aurarangecalc);
    assertEquals(0, skill.Param[2], "calc2 minimum is already signed 8.8 damage");
    assertEquals(DAMAGE_PERCENT, skill.Param[3]);

    for (int level = 1; level <= 20; level++) {
      int d2mooDamagePercent = SkillFormula.evaluate(skill.calc1, skill, level);
      int d2mooMinimumDamageFixed = SkillFormula.evaluate(skill.calc2, skill, level);
      int expectedFixed = d2mooStaticFieldDamageFixed(
          CURRENT_LIFE_FIXED, MAXIMUM_LIFE_FIXED, d2mooDamagePercent,
          d2mooMinimumDamageFixed, 0);

      assertEquals(DAMAGE_PERCENT, d2mooDamagePercent, "damage percent level " + level);
      assertEquals(0, d2mooMinimumDamageFixed, "minimum fixed damage level " + level);
      assertEquals(EXPECTED_DAMAGE_FIXED, expectedFixed, "D2MOO fixed damage level " + level);
      assertEquals(25, expectedFixed >> 8, "D2MOO integer damage level " + level);
      assertEquals(d2mooDamagePercent,
          SorceressSkills.getStaticFieldDamagePercent(skill, level),
          "riiablo damage percent level " + level);
      assertEquals(d2mooMinimumDamageFixed,
          SorceressSkills.getStaticFieldMinimumDamageFixed(skill, level),
          "riiablo minimum fixed damage level " + level);
      assertEquals(expectedFixed, SorceressSkills.calculateStaticFieldRawDamageFixed(
          CURRENT_LIFE_FIXED, MAXIMUM_LIFE_FIXED, d2mooDamagePercent,
          d2mooMinimumDamageFixed, 0), "riiablo raw fixed damage level " + level);
    }
  }

  @Test
  void nativeFloorAndLastLifeBoundariesMatchD2mooOrder() {
    assertBoundary(1, 100, 25, 0, 0, 0);
    assertBoundary(34, 100, 25, 0, 33, 8 << 8);
    assertBoundary(33, 100, 25, 0, 33, 0);
    assertBoundary(51, 100, 25, 0, 50, 12 << 8);
    assertBoundary(50, 100, 25, 0, 50, 0);
  }

  private static void assertBoundary(
      int currentLife, int maximumLife, int damagePercent,
      int minimumDamageFixed, int lifeFloorPercent, int expectedFixed) {
    int d2mooFixed = d2mooStaticFieldDamageFixed(
        currentLife << 8, maximumLife << 8, damagePercent,
        minimumDamageFixed, lifeFloorPercent);
    assertEquals(expectedFixed, d2mooFixed, "D2MOO boundary current life " + currentLife);
    assertEquals(d2mooFixed, SorceressSkills.calculateStaticFieldRawDamageFixed(
        currentLife << 8, maximumLife << 8, damagePercent,
        minimumDamageFixed, lifeFloorPercent),
        "riiablo boundary current life " + currentLife);
  }

  /** Independent transcription of D2MOO SKILLS_AuraCallback_StaticField. */
  private static int d2mooStaticFieldDamageFixed(
      int currentLifeFixed, int maximumLifeFixed, int damagePercent,
      int minimumDamageFixed, int lifeFloorPercent) {
    int currentLife = currentLifeFixed >> 8;
    if (currentLife < 1) return 0;
    if (lifeFloorPercent != 0) {
      int minimumLife = (maximumLifeFixed >> 8) * lifeFloorPercent / 100;
      if (currentLife <= minimumLife) return 0;
    }
    int shiftedDamage = currentLife * damagePercent / 100;
    if (shiftedDamage > currentLife - 1) shiftedDamage = currentLife - 1;
    int damageFixed = shiftedDamage << 8;
    return Math.max(damageFixed, minimumDamageFixed);
  }
}
