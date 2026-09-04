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

/** Locks Shock Wave's executable behavior to the native Skills/Missiles rows. */
class NativeDruidShockWaveDataTest extends RiiabloTest {
  @Test
  void shockWaveUsesNativeSrvDoMissileDamageAndStunColumns() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.SHOCK_WAVE);
    assertNotNull(skill);
    assertEquals("Shock Wave", skill.skill);
    assertEquals(0, skill.srvstfunc);
    assertEquals(8, skill.srvdofunc);
    assertEquals(2, skill.restrict);
    assertEquals("bear", skill.state1);
    assertEquals("5", skill.calc1);
    assertEquals("shockwave", skill.srvmissilea);
    assertEquals(0, skill.SrcDam);
    assertTrue(DruidSkills.isShockWave(skill));
    assertEquals(5, DruidSkills.getShockWaveMissileCount(skill, 20));
    assertEquals(40, DruidSkills.getShockWaveStunDuration(skill, 1));
    assertEquals(55, DruidSkills.getShockWaveStunDuration(skill, 2));
    assertEquals(10, DruidSkills.getShockWaveDamageRange(skill, 1)[0]);
    assertEquals(20, DruidSkills.getShockWaveDamageRange(skill, 1)[1]);
    assertEquals(13, DruidSkills.getShockWaveDamageRange(skill, 2)[0]);
    assertEquals(23, DruidSkills.getShockWaveDamageRange(skill, 2)[1]);

    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.srvmissilea);
    assertNotNull(missile);
    assertEquals("shockwave", missile.Missile);
    assertEquals(1, missile.pSrvDoFunc);
    assertEquals(0, missile.pSrvHitFunc);
    assertEquals(7, missile.pSrvDmgFunc);
    assertEquals(0, missile.dParam[0]);
    assertEquals(14, missile.Range);
    assertEquals(20, missile.Vel);
  }
}
