package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.NativeSkills;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Native 1.10f contracts for the remaining Paladin elemental/resistance auras. */
class NativePaladinRemainingAuraDataTest extends RiiabloTest {
  @Test
  void resistanceAuraRowsPreserveActiveAndHardPointPassiveContracts() {
    assertResistanceAura(SkillId.RESIST_FIRE, "resistfire", "passive_resistfire",
        "fireresist", "maxfireresist");
    assertResistanceAura(SkillId.RESIST_COLD, "resistcold", "passive_resistcold",
        "coldresist", "maxcoldresist");
    assertResistanceAura(SkillId.RESIST_LIGHTNING, "resistlight", "passive_resistltng",
        "lightresist", "maxlightresist");
  }

  @Test
  void salvationUsesNativeAllResistanceFormula() {
    Skills.Entry skill = skill(SkillId.SALVATION);
    assertEquals(65, skill.srvdofunc);
    assertEquals("resistall", skill.aurastate);
    assertEquals("resistall", skill.auratargetstate);
    assertEquals("ln12", skill.aurarangecalc);
    assertEquals(73731, skill.aurafilter);
    assertEquals("50", skill.perdelay);
    assertEquals("fireresist", skill.aurastat[0]);
    assertEquals("coldresist", skill.aurastat[1]);
    assertEquals("lightresist", skill.aurastat[2]);
    assertEquals("dm34", skill.aurastatcalc[0]);
    assertEquals("dm34", skill.aurastatcalc[1]);
    assertEquals("dm34", skill.aurastatcalc[2]);
    assertEquals(61, com.riiablo.engine.server.skill.SkillFormula.evaluate(
        skill.aurastatcalc[0], skill, 1));
    assertEquals(109, com.riiablo.engine.server.skill.SkillFormula.evaluate(
        skill.aurastatcalc[0], skill, 20));
  }

  private static void assertResistanceAura(int id, String stateName, String passiveState,
      String resistStat, String maximumStat) {
    Skills.Entry skill = skill(id);
    NativeSkills.Entry nativeSkill = Riiablo.files.NativeSkills.get(id);
    assertNotNull(nativeSkill);
    assertEquals(65, skill.srvdofunc);
    assertEquals(stateName, skill.aurastate);
    assertEquals(stateName, skill.auratargetstate);
    assertEquals("ln12", skill.aurarangecalc);
    assertEquals(73731, skill.aurafilter);
    assertEquals("50", skill.perdelay);
    assertEquals(resistStat, skill.aurastat[0]);
    assertEquals(maximumStat, skill.aurastat[1]);
    assertEquals("dm34", skill.aurastatcalc[0]);
    assertEquals("skill('" + skill.skill + "'.blvl)", skill.aurastatcalc[1]);
    assertEquals(passiveState, skill.passivestate);
    assertEquals(passiveState, nativeSkill.string("passivestate"));
    assertEquals(maximumStat, skill.passivestat[0]);
    assertEquals("skill('" + skill.skill + "'.blvl)/2", skill.passivecalc[0]);
    assertEquals(4, com.riiablo.engine.server.skill.SkillFormula.evaluate(
        skill.aurastatcalc[1], skill, 4, name -> 4));
    assertEquals(2, com.riiablo.engine.server.skill.SkillFormula.evaluate(
        skill.passivecalc[0], skill, 4, name -> 4));
  }

  private static Skills.Entry skill(int id) {
    Skills.Entry skill = Riiablo.files.skills.get(id);
    assertNotNull(skill, "missing Paladin aura id=" + id);
    return skill;
  }
}
