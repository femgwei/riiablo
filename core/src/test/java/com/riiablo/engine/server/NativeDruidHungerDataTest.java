package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

class NativeDruidHungerDataTest extends RiiabloTest {
  @Test
  void hungerUsesNativeSrvDoAndWolfBearFormulaColumns() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.HUNGER);
    assertNotNull(skill);
    assertEquals("Hunger", skill.skill);
    assertEquals(0, skill.srvstfunc);
    assertEquals(122, skill.srvdofunc);
    assertEquals(2, skill.restrict);
    assertEquals("wolf", skill.state1);
    assertEquals("bear", skill.state2);
    assertEquals("par5", skill.calc1);
    assertEquals("dm12", skill.calc2);
    assertEquals("dm34", skill.calc3);
    assertEquals(128, skill.SrcDam);
    assertTrue(DruidSkills.isHunger(skill));
    assertTrue(DruidSkills.getHungerLifeLeech(skill, 1, name -> 0) > 0);
    assertTrue(DruidSkills.getHungerManaLeech(skill, 1, name -> 0) > 0);
  }
}
