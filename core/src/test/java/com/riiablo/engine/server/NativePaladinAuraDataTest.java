package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.AuraManager;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Stable executable contract for the five migrated 1.10f Paladin aura rows. */
class NativePaladinAuraDataTest extends RiiabloTest {
  @Test
  void basicAuraRowsMatchTheNative110fTable() {
    Skills.Entry might = skill(SkillId.MIGHT);
    assertAura(might, 65, "might", "might", "ln12", 73731);
    assertEquals("damagepercent", might.aurastat[0]);
    assertEquals("ln34", might.aurastatcalc[0]);
    assertEquals(16, SkillFormula.evaluate(might.aurarangecalc, might, 1));
    assertEquals(54, SkillFormula.evaluate(might.aurarangecalc, might, 20));
    assertEquals(40, SkillFormula.evaluate(might.aurastatcalc[0], might, 1));
    assertEquals(230, SkillFormula.evaluate(might.aurastatcalc[0], might, 20));

    Skills.Entry prayer = skill(SkillId.PRAYER);
    assertAura(prayer, 65, "prayer", "prayer", "ln12", 73731);
    assertEquals("hitpoints", prayer.aurastat[0]);
    assertEquals("edns", prayer.aurastatcalc[0]);
    assertEquals(512, SkillFormula.evaluate("edns", prayer, 1));
    assertEquals(6400, SkillFormula.evaluate("edns", prayer, 20));

    Skills.Entry concentration = skill(SkillId.CONCENTRATION);
    assertAura(concentration, 65, "concentration", "concentration", "ln12", 73731);
    assertEquals("damagepercent", concentration.aurastat[0]);
    assertEquals("skill_concentration", concentration.aurastat[1]);
    assertEquals(60, SkillFormula.evaluate(concentration.aurastatcalc[0], concentration, 1));
    assertEquals(345, SkillFormula.evaluate(concentration.aurastatcalc[0], concentration, 20));
    assertEquals(20, SkillFormula.evaluate(concentration.aurastatcalc[1], concentration, 1));
  }

  @Test
  void periodicAndDebuffRowsMatchTheNative110fTable() {
    Skills.Entry holyFire = skill(SkillId.HOLY_FIRE);
    assertAura(holyFire, 66, "holyfire", "", "ln12", 42883);
    assertEquals("fire", holyFire.EType);
    assertEquals(7, holyFire.HitShift);
    assertEquals(2, holyFire.EMin);
    assertEquals(6, holyFire.EMax);
    assertEquals("firemindam", holyFire.passivestat[0]);
    assertEquals("firemaxdam", holyFire.passivestat[1]);
    assertEquals("enms*par5/256", holyFire.passivecalc[0]);
    assertEquals("exms*par5/256", holyFire.passivecalc[1]);
    assertEquals(6, SkillFormula.evaluate(holyFire.passivecalc[0], holyFire, 1));
    assertEquals(18, SkillFormula.evaluate(holyFire.passivecalc[1], holyFire, 1));
    assertEquals(6, SkillFormula.evaluate(holyFire.aurarangecalc, holyFire, 1));
    assertEquals(25, SkillFormula.evaluate(holyFire.aurarangecalc, holyFire, 20));
    assertArrayEquals(new int[] {1, 3}, AuraManager.nativeElementalDamageRange(holyFire, 1));
    assertArrayEquals(new int[] {18, 20}, AuraManager.nativeElementalDamageRange(holyFire, 20));

    Skills.Entry conviction = skill(SkillId.CONVICTION);
    assertAura(conviction, 66, "conviction", "conviction", "ln12", 42371);
    assertEquals("skill_armor_percent", conviction.aurastat[0]);
    assertEquals("fireresist", conviction.aurastat[1]);
    assertEquals("coldresist", conviction.aurastat[2]);
    assertEquals("lightresist", conviction.aurastat[3]);
    assertEquals(20, SkillFormula.evaluate(conviction.aurarangecalc, conviction, 1));
    assertEquals(20, SkillFormula.evaluate(conviction.aurarangecalc, conviction, 20));
    assertEquals(-49, SkillFormula.evaluate(conviction.aurastatcalc[0], conviction, 1));
    assertEquals(-90, SkillFormula.evaluate(conviction.aurastatcalc[0], conviction, 20));
    assertEquals(-30, SkillFormula.evaluate(conviction.aurastatcalc[1], conviction, 1));
    assertEquals(-125, SkillFormula.evaluate(conviction.aurastatcalc[1], conviction, 20));
  }

  private static Skills.Entry skill(int skillId) {
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    assertNotNull(skill, "missing Paladin aura id=" + skillId);
    return skill;
  }

  private static void assertAura(Skills.Entry skill, int serverFunction,
      String selfState, String targetState, String rangeFormula, int filter) {
    assertEquals(serverFunction, skill.srvdofunc);
    assertEquals(selfState, skill.aurastate);
    assertEquals(targetState, skill.auratargetstate);
    assertEquals(rangeFormula, skill.aurarangecalc);
    assertEquals("50", skill.perdelay);
    assertEquals(filter, skill.aurafilter);
  }
}
