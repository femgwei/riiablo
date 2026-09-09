package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Executable contract for the native Poison Dagger Skills.txt row. */
class NativeNecromancerPoisonDaggerDataTest extends RiiabloTest {
  @Test
  void poisonDaggerUsesNativeCombatFunctionsAndFixedPoisonFields() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.POISON_DAGGER);
    assertNotNull(skill);
    assertEquals("Poison Dagger", skill.skill);
    assertEquals(16, skill.srvstfunc);
    assertEquals(32, skill.srvdofunc);
    assertEquals(128, skill.SrcDam);
    assertEquals(30, skill.ToHit);
    assertEquals(20, skill.LevToHit);
    assertEquals("pois", skill.EType);
    assertEquals(1, skill.HitShift);
    assertEquals(18, skill.EMin);
    assertEquals(40, skill.EMax);
    assertEquals(50, skill.ELen);
    assertEquals(20, skill.Param[7]);
    assertTrue(skill.EDmgSymPerCalc.contains("Poison Explosion"));
    assertTrue(skill.EDmgSymPerCalc.contains("Poison Nova"));
  }
}
