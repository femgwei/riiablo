package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Executable contract for D2's Rabies and Fire Claws table rows. */
class NativeDruidRabiesFireClawsDataTest extends RiiabloTest {
  @Test
  void rabiesUsesNativeWolfGatePoisonAndControllerMissile() {
    Skills.Entry skill = skill(SkillId.RABIES);
    assertEquals("Rabies", skill.skill);
    assertEquals(57, skill.srvstfunc);
    assertEquals(121, skill.srvdofunc);
    assertEquals(2, skill.restrict);
    assertEquals("wolf", skill.state1);
    assertEquals("rabies", skill.auratargetstate);
    assertEquals("pois", skill.EType);
    assertEquals(6, skill.EMin);
    assertEquals(14, skill.EMax);
    assertEquals(100, skill.ELen);
    assertEquals("(skill('Plague Poppy'.blvl))*par8", skill.EDmgSymPerCalc);
    assertEquals(18, skill.Param[7]);
    assertEquals(50, skill.ToHit);
    assertEquals(7, skill.LevToHit);
    assertEquals("rabiesplague", skill.srvmissilea);
    Missiles.Entry controller = Riiablo.files.Missiles.get(skill.srvmissilea);
    assertNotNull(controller);
    assertEquals(30, controller.pSrvDoFunc);
    assertEquals(4, controller.Param[0]);
    assertEquals(7, controller.Param[1]);
    assertEquals("rabiescontagion", controller.SubMissile[0]);
    Missiles.Entry contagion = Riiablo.files.Missiles.get(controller.SubMissile[0]);
    assertNotNull(contagion);
    assertEquals(53, contagion.pSrvHitFunc);
    assertEquals(11, contagion.pSrvDmgFunc);
    assertTrue(DruidSkills.isRabies(skill));
    assertEquals(100, DruidSkills.getRabiesPoisonDuration(skill, 1, name -> 0));
    int[] poison = DruidSkills.getRabiesPoisonDamage(skill, 1, name -> 0);
    assertTrue(poison[0] > 0 && poison[1] >= poison[0]);
  }

  @Test
  void fireClawsUsesBothShapesAndNativeElementalSynergies() {
    Skills.Entry skill = skill(SkillId.FIRE_CLAWS);
    assertEquals("Fire Claws", skill.skill);
    assertEquals(58, skill.srvstfunc);
    assertEquals(2, skill.srvdofunc);
    assertEquals(2, skill.restrict);
    assertEquals("wolf", skill.state1);
    assertEquals("bear", skill.state2);
    assertEquals("fire", skill.EType);
    assertEquals(15, skill.EMin);
    assertEquals(20, skill.EMax);
    assertEquals(50, skill.ToHit);
    assertEquals(15, skill.LevToHit);
    assertEquals(128, skill.SrcDam);
    assertEquals(22, skill.Param[7]);
    assertTrue(DruidSkills.isFireClaws(skill));

    int[] base = DruidSkills.getFireClawsFireDamage(skill, 1, name -> 0);
    int[] synergized = DruidSkills.getFireClawsFireDamage(skill, 1,
        name -> "Firestorm".equals(name) ? 3 : "Volcano".equals(name) ? 2 : 0);
    assertEquals(15, base[0]);
    assertEquals(20, base[1]);
    assertEquals(base[0] * 210 / 100, synergized[0]);
    assertEquals(base[1] * 210 / 100, synergized[1]);
  }

  private static Skills.Entry skill(int id) {
    Skills.Entry skill = Riiablo.files.skills.get(id);
    assertNotNull(skill, Integer.toString(id));
    return skill;
  }
}
