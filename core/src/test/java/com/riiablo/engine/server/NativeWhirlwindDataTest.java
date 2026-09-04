package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.BarbarianSkills;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class NativeWhirlwindDataTest extends RiiabloTest {
  @Test
  void nativeRowDrivesWhirlwindPathAndAttackWindows() {
    Skills.Entry skill = Riiablo.files.skills.get("Whirlwind");
    assertNotNull(skill);
    assertEquals(151, skill.Id);
    assertEquals(38, skill.srvstfunc);
    assertEquals(76, skill.srvdofunc);
    assertEquals(45, skill.cltdofunc);
    assertEquals("SQ", skill.anim);
    assertEquals(10, skill.seqnum);
    assertEquals("whirlwind", skill.aurastate);
    assertEquals("ln12", skill.calc1);
    assertEquals(-50, BarbarianSkills.calculateWhirlwindDamageBonus(skill, 1, name -> 0));
    assertEquals(102, BarbarianSkills.calculateWhirlwindDamageBonus(skill, 20, name -> 0));
    assertEquals(128, skill.SrcDam);
    assertEquals(5, skill.LevToHit);
  }
}
