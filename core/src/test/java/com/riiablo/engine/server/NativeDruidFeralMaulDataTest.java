package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import org.junit.jupiter.api.Test;

/** Executable contract for D2's Skills.txt Feral Rage and Maul rows. */
class NativeDruidFeralMaulDataTest extends RiiabloTest {
  @Test
  void feralRageUsesNativeSrvFunctionsShapeGateAndStackFormulas() {
    Skills.Entry skill = skill(SkillId.FERAL_RAGE);
    assertEquals("Feral Rage", skill.skill);
    assertEquals(56, skill.srvstfunc);
    assertEquals(120, skill.srvdofunc);
    assertEquals(2, skill.restrict);
    assertEquals("wolf", skill.state1);
    assertEquals("feralrage", skill.aurastate);
    assertEquals("par1", skill.auralencalc);
    assertEquals("velocitypercent", skill.aurastat[0]);
    assertEquals("dm34", skill.aurastatcalc[0]);
    assertEquals("lifedrainmindam", skill.aurastat[1]);
    assertEquals("par2 * lvl", skill.aurastatcalc[1]);
    assertEquals("ln56", skill.calc1);
    assertEquals("lvl/par7 + par8", skill.calc2);
    assertEquals(20, skill.ToHit);
    assertEquals(10, skill.LevToHit);
    assertEquals(128, skill.SrcDam);
    assertEquals(StateId.FERALRAGE, DruidSkills.getFeralMaulStateId(skill));
    assertEquals(500, DruidSkills.getFeralMaulDuration(skill, 1));
    assertEquals(3, DruidSkills.getFeralMaulMaxStacks(skill, 1));
    assertEquals(5, DruidSkills.getFeralMaulMaxStacks(skill, 5));
  }

  @Test
  void maulUsesNativeBearGateDamageAndStunFormulas() {
    Skills.Entry skill = skill(SkillId.MAUL);
    assertEquals("Maul", skill.skill);
    assertEquals(56, skill.srvstfunc);
    assertEquals(120, skill.srvdofunc);
    assertEquals(2, skill.restrict);
    assertEquals("bear", skill.state1);
    assertEquals("maul", skill.aurastate);
    assertEquals("par4", skill.auralencalc);
    assertEquals("damagepercent", skill.aurastat[0]);
    assertEquals("lvl*par3", skill.aurastatcalc[0]);
    assertEquals("stunlength", skill.aurastat[1]);
    assertEquals("dm56", skill.aurastatcalc[1]);
    assertEquals("0", skill.calc1);
    assertEquals("lvl/par7 + par8", skill.calc2);
    assertEquals(StateId.MAUL, DruidSkills.getFeralMaulStateId(skill));
    assertEquals(500, DruidSkills.getFeralMaulDuration(skill, 20));
  }

  private static Skills.Entry skill(int id) {
    Skills.Entry skill = Riiablo.files.skills.get(id);
    assertNotNull(skill, Integer.toString(id));
    return skill;
  }
}
