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

  @org.junit.jupiter.api.Test
  void damagePenaltyMultipliesTheCompletedPhysicalDamageRatherThanCancellingStrength() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.HUNGER);
    com.riiablo.attributes.Attributes attrs = com.riiablo.attributes.Attributes.obtainStandard();
    attrs.base().put(com.riiablo.attributes.Stat.mindamage, 40);
    attrs.base().put(com.riiablo.attributes.Stat.maxdamage, 40);
    attrs.base().put(com.riiablo.attributes.Stat.strength, 100);
    attrs.reset();
    org.junit.jupiter.api.Assertions.assertArrayEquals(new int[] {20,20},
        DruidSkills.calculateShapeWeaponDamage(skill, 1, attrs, null, null),
        "40 * (1 + 100% strength) * 25% = 20, rather than 50 from additive -75% ED");
    com.riiablo.engine.server.state.StateList states = new com.riiablo.engine.server.state.StateList(1);
    states.addState(com.riiablo.engine.server.state.StateId.BEAR, 2000, 1, 1).damageModifier = 100;
    org.junit.jupiter.api.Assertions.assertArrayEquals(new int[] {30,30},
        DruidSkills.calculateShapeWeaponDamage(skill, 1, attrs, null, states));
  }
}
