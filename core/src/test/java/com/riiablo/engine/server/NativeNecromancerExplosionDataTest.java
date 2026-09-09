package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Executable contract for the 1.10f Corpse/Poison Explosion rows. */
class NativeNecromancerExplosionDataTest extends RiiabloTest {
  @Test
  void corpseExplosionUsesNativeFunctionsDamageSplitAndHalfSquareRadii() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.CORPSE_EXPLOSION);
    assertNotNull(skill);
    assertEquals(17, skill.srvstfunc);
    assertEquals(55, skill.srvdofunc);
    assertEquals(32, skill.cltdofunc);
    assertEquals("ln34", skill.aurarangecalc);
    assertEquals("par1", skill.calc1);
    assertEquals("par2", skill.calc2);
    assertEquals("par5", skill.calc3);
    assertEquals("corpseexplosion", skill.cltmissilea);
    assertEquals("fire", skill.EType);
    assertEquals(70, NecromancerSkills.getCorpseExplosionDamagePercent(skill, 1)[0]);
    assertEquals(120, NecromancerSkills.getCorpseExplosionDamagePercent(skill, 1)[1]);
    assertEquals(50, NecromancerSkills.getCorpseExplosionElementalPercent(skill, 1));
    assertEquals(4, NecromancerSkills.getCorpseExplosionRadii(skill, 2)[0]);
    assertEquals(5, NecromancerSkills.getCorpseExplosionRadii(skill, 2)[1]);
  }

  @Test
  void poisonExplosionUsesNativeFixedRateDurationSynergyAndCloudRow() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.POISON_EXPLOSION);
    assertNotNull(skill);
    assertEquals(17, skill.srvstfunc);
    assertEquals(63, skill.srvdofunc);
    assertEquals(33, skill.cltdofunc);
    assertEquals("poisonexplosioncloud", skill.srvmissilea);
    assertEquals("poisoncorpseexplosion", skill.cltmissilea);
    assertEquals("pois", skill.EType);
    int[] base = NecromancerSkills.getPoisonExplosionDamage(skill, 1, name -> 0);
    assertEquals(128, base[0]);
    assertEquals(384, base[1]);
    assertEquals(50, NecromancerSkills.getPoisonExplosionDurationFrames(skill, 1));
    int[] synergized = NecromancerSkills.getPoisonExplosionDamage(skill, 1,
        name -> "Poison Dagger".equals(name) ? 2
            : "Poison Nova".equals(name) ? 3 : 0);
    assertEquals(224, synergized[0]);
    assertEquals(672, synergized[1]);
    assertEquals(80, NecromancerSkills.getPoisonExplosionDurationFrames(skill, 4));
    assertTrue(skill.EDmgSymPerCalc.contains("Poison Dagger"));
    assertTrue(skill.EDmgSymPerCalc.contains("Poison Nova"));
  }
}
