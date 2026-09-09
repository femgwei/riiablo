package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Stable executable contract for the 1.10f Blessed Hammer table rows. */
class NativePaladinBlessedHammerDataTest extends RiiabloTest {
  @Test
  void nativeRowsMatchBlessedHammerServerContracts() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.BLESSED_HAMMER);
    assertNotNull(skill);
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.srvmissilea);
    assertNotNull(missile);

    assertEquals("Blessed Hammer", skill.skill);
    assertEquals(73, skill.srvdofunc);
    assertEquals("blessedhammer", skill.srvmissilea);
    assertArrayEquals(new int[] {4, 0, 0, 0, 0, 0, 0, 14}, skill.Param);
    assertEquals(8, skill.HitShift);
    assertEquals("mag", skill.EType);
    assertEquals(12, skill.EMin);
    assertArrayEquals(new int[] {8, 10, 12, 13, 14}, skill.EMinLev);
    assertEquals(16, skill.EMax);
    assertArrayEquals(new int[] {8, 10, 12, 13, 14}, skill.EMaxLev);
    assertEquals("(skill('Vigor'.blvl)+skill('Blessed Aim'.blvl))*par8",
        skill.EDmgSymPerCalc);

    assertEquals(92, missile.Id);
    assertEquals("blessedhammer", missile.Missile);
    assertEquals(18, missile.Vel);
    assertEquals(30, missile.MaxVel);
    assertEquals(250, missile.Accel);
    assertEquals(120, missile.Range);
    assertEquals(3, missile.CollideType);
    assertFalse(missile.CollideKill);
    assertFalse(missile.Collision);
    assertTrue(missile.LastCollide);
    assertFalse(missile.NextHit);
    assertEquals(1, missile.Size);
    assertFalse(missile.ToHit);
    assertEquals(1, missile.pSrvDoFunc);
    assertEquals(0, missile.pSrvHitFunc);
    assertEquals(5, missile.pSrvDmgFunc);
    assertEquals("Blessed Hammer", missile.Skill);
    assertArrayEquals(new int[] {50, 50}, missile.dParam);
  }
}
