package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Native 1.10f contracts for the SrvDo065 Paladin support auras. */
class NativePaladinSupportAuraDataTest extends RiiabloTest {
  private static final int PARTY_AURA_FILTER = 0x12003;

  @Test
  void supportAuraRowsRetainTheirNativeStatesFiltersAndCadence() {
    assertRow(SkillId.DEFIANCE, "Defiance", "defiance", "", "skill_armor_percent", "ln34");
    assertRow(SkillId.BLESSED_AIM, "Blessed Aim", "blessedaim", "penetrate",
        "item_tohit_percent", "ln34");
    assertRow(SkillId.VIGOR, "Vigor", "stamina", "", "staminarecoverybonus", "ln34");
    assertRow(SkillId.FANATICISM, "Fanaticism", "fanaticism", "",
        "attackrate", "dm34");
    assertRow(SkillId.THORNS, "Thorns", "thorns", "", "thorns_percent", "ln34");
  }

  @Test
  void nativeLevelOneFormulasMatchThe110fTables() {
    Skills.Entry defiance = skill(SkillId.DEFIANCE);
    assertEquals(70, SkillFormula.evaluate(defiance.aurastatcalc[0], defiance, 1));

    Skills.Entry blessedAim = skill(SkillId.BLESSED_AIM);
    assertEquals(75, SkillFormula.evaluate(blessedAim.aurastatcalc[0], blessedAim, 1));
    assertEquals("item_tohit_percent", blessedAim.passivestat[0]);
    assertEquals("skill('Blessed Aim'.blvl) * par8", blessedAim.passivecalc[0]);
    assertEquals(20, SkillFormula.evaluate(blessedAim.passivecalc[0], blessedAim, 4,
        name -> "Blessed Aim".equalsIgnoreCase(name) ? 4 : 0));

    Skills.Entry vigor = skill(SkillId.VIGOR);
    assertEquals("skill_staminapercent", vigor.aurastat[1]);
    assertEquals("velocitypercent", vigor.aurastat[2]);
    assertEquals(50, SkillFormula.evaluate(vigor.aurastatcalc[0], vigor, 1));
    assertEquals(50, SkillFormula.evaluate(vigor.aurastatcalc[1], vigor, 1));
    assertEquals(13, SkillFormula.evaluate(vigor.aurastatcalc[2], vigor, 1));

    Skills.Entry fanaticism = skill(SkillId.FANATICISM);
    assertEquals("item_tohit_percent", fanaticism.aurastat[1]);
    assertEquals("damagepercent", fanaticism.aurastat[2]);
    assertEquals(14, SkillFormula.evaluate(fanaticism.aurastatcalc[0], fanaticism, 1));
    assertEquals(40, SkillFormula.evaluate(fanaticism.aurastatcalc[1], fanaticism, 1));
    assertEquals(25, SkillFormula.evaluate(fanaticism.aurastatcalc[2], fanaticism, 1));
    assertEquals("damagepercent", fanaticism.passivestat[0]);
    assertEquals(50, SkillFormula.evaluate(fanaticism.passivecalc[0], fanaticism, 1));

    Skills.Entry thorns = skill(SkillId.THORNS);
    assertEquals(250, SkillFormula.evaluate(thorns.aurastatcalc[0], thorns, 1));
  }

  private static void assertRow(int id, String name, String state, String passiveState,
      String firstAuraStat, String firstAuraCalc) {
    Skills.Entry skill = skill(id);
    assertEquals(name, skill.skill);
    assertEquals(65, skill.srvdofunc);
    assertEquals(state, skill.aurastate);
    assertEquals(state, skill.auratargetstate);
    assertEquals(passiveState, skill.passivestate);
    assertEquals(PARTY_AURA_FILTER, skill.aurafilter);
    assertEquals("50", skill.perdelay);
    assertEquals("ln12", skill.aurarangecalc);
    assertEquals(firstAuraStat, skill.aurastat[0]);
    assertEquals(firstAuraCalc, skill.aurastatcalc[0]);
  }

  private static Skills.Entry skill(int id) {
    Skills.Entry skill = Riiablo.files.skills.get(id);
    assertNotNull(skill, "missing 1.10f Skills.txt row id=" + id);
    return skill;
  }
}
