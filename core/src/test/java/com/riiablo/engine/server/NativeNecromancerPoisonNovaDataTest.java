package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Executable contract for the native 1.10f Poison Nova rows. */
class NativeNecromancerPoisonNovaDataTest extends RiiabloTest {
  @Test
  void rowAndFixedPoisonFormulaMatchNativeFields() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.POISON_NOVA);
    assertNotNull(skill);
    assertEquals("Poison Nova", skill.skill);
    assertEquals(22, skill.srvdofunc);
    assertEquals("poisonnova", skill.srvmissilea);
    assertEquals(16, skill.EMin);
    assertEquals(29, skill.EMax);
    assertEquals(4, skill.HitShift);
    assertEquals(50, skill.ELen);
    assertTrue(skill.EDmgSymPerCalc.contains("Poison Dagger"));
    assertTrue(skill.EDmgSymPerCalc.contains("Poison Explosion"));
    assertEquals(10, skill.Param[7]);

    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.srvmissilea);
    assertNotNull(missile);
    assertEquals(12, missile.Vel);
    assertEquals(30, missile.Range);
    assertEquals(1, missile.pSrvDoFunc);

    assertTrue(NecromancerSkills.isPoisonNova(skill));
    assertEquals(256, NecromancerSkills.getPoisonNovaDamage(skill, 1, name -> 0)[0]);
    assertEquals(464, NecromancerSkills.getPoisonNovaDamage(skill, 1, name -> 0)[1]);
    assertEquals(50, NecromancerSkills.getPoisonNovaDurationFrames(skill, 1));
    assertEquals(384, NecromancerSkills.getPoisonNovaDamage(skill, 1,
        name -> "Poison Dagger".equals(name) ? 2 : "Poison Explosion".equals(name) ? 3 : 0)[0]);
  }
}
