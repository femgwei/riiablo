package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Skills;
import org.junit.jupiter.api.Test;

class NativeSelfResurrectDataTest extends RiiabloTest {
  @Test
  void resurrectionDataResolvesAndSkillsExposeNativeServerStart61() {
    int configuredRows = 0;
    for (MonStats2.Entry monster : Riiablo.files.monstats2) {
      if (monster.ResurrectSkill == null || monster.ResurrectSkill.isEmpty()) continue;
      configuredRows++;
      Skills.Entry skill = Riiablo.files.skills.get(monster.ResurrectSkill);
      assertNotNull(skill, "missing ResurrectSkill " + monster.ResurrectSkill
          + " for MonStats2 " + monster.Id);
    }
    assertTrue(configuredRows > 0, "MonStats2 must configure native resurrection rows");

    Skills.Entry selfResurrect = null;
    for (Skills.Entry skill : Riiablo.files.skills) {
      if (skill.srvstfunc == 61) {
        selfResurrect = skill;
        break;
      }
    }
    assertNotNull(selfResurrect, "Skills.txt must expose native SrvSt61 SelfResurrect");
  }
}
