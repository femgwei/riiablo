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

/** Locks Fury's SrvSt37/SrvDo013 formulas to the native Skills.txt row. */
class NativeDruidFuryDataTest extends RiiabloTest {
  @Test
  void furyUsesNativeShapeCountDamageAndAttackRateColumns() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.FURY);
    assertNotNull(skill);
    assertEquals("Fury", skill.skill);
    assertEquals(37, skill.srvstfunc);
    assertEquals(13, skill.srvdofunc);
    assertEquals(2, skill.restrict);
    assertEquals("wolf", skill.state1);
    assertEquals("\"min((par5 + lvl -1), par6)\"", skill.calc1);
    assertEquals("ln34", skill.calc2);
    assertEquals(50, skill.ToHit);
    assertEquals(7, skill.LevToHit);
    assertEquals(128, skill.SrcDam);
    assertTrue(DruidSkills.isFury(skill));
    assertEquals(2, DruidSkills.getFuryHitCount(skill, 1));
    assertEquals(3, DruidSkills.getFuryHitCount(skill, 2));
    assertEquals(5, DruidSkills.getFuryHitCount(skill, 4));
    assertEquals(5, DruidSkills.getFuryHitCount(skill, 20));
    assertEquals(100, DruidSkills.getFuryDamagePercent(skill, 1));
    assertEquals(117, DruidSkills.getFuryDamagePercent(skill, 2));
    assertEquals(100, DruidSkills.getFuryRepeatAttackRate(skill));
  }
}
