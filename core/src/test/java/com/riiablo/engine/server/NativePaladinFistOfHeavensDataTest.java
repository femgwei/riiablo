package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.NativeSkills;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Stable 1.10f contracts used by SrvDo080 and SrvHit07/22. */
class NativePaladinFistOfHeavensDataTest extends RiiabloTest {
  @Test
  void nativeRowsMatchFistOfHeavensServerContracts() {
    Skills.Entry fist = Riiablo.files.skills.get(SkillId.FIST_OF_THE_HEAVENS);
    NativeSkills.Entry nativeFist = Riiablo.files.NativeSkills.get("Fist of the Heavens");
    assertNotNull(fist);
    assertNotNull(nativeFist);
    assertEquals(80, fist.srvdofunc);
    assertEquals("fistoftheheavensdelay", fist.srvmissilea);
    assertEquals("20", fist.aurarangecalc);
    assertEquals(0xA587, fist.aurafilter);
    assertEquals("ln12", fist.calc4);
    assertEquals("ltng", fist.EType);
    assertEquals(150, fist.EMin);
    assertEquals(200, fist.EMax);
    assertEquals("skill('Holy Shock'.blvl)*par8", fist.EDmgSymPerCalc);
    assertEquals("handofgod", nativeFist.string("srvoverlay"));
    assertFalse(nativeFist.bool("TargetPet"));
    assertFalse(nativeFist.bool("TargetAlly"));

    Missiles.Entry delay = Riiablo.files.Missiles.get(fist.srvmissilea);
    assertNotNull(delay);
    assertEquals(10, delay.Range);
    assertEquals(0, delay.Vel);
    assertEquals(1, delay.pSrvDoFunc);
    assertEquals(22, delay.pSrvHitFunc);
    assertFalse(delay.Collision);
    assertFalse(delay.CollideKill);
    assertEquals("Fist of the Heavens", delay.Skill);
    assertEquals("fistoftheheavensbolt", delay.HitSubMissile[0]);
    assertArrayEquals(new int[] {0, 0, 0}, delay.sHitPar);

    Missiles.Entry bolt = Riiablo.files.Missiles.get(delay.HitSubMissile[0]);
    assertNotNull(bolt);
    assertEquals(12, bolt.Vel);
    assertEquals(50, bolt.Range);
    assertEquals(3, bolt.CollideType);
    assertEquals(7, bolt.pSrvHitFunc);
    assertTrue(bolt.CollideFriend);
    assertTrue(bolt.CollideKill);
    assertTrue(bolt.LastCollide);
    assertArrayEquals(new int[] {1, 1, 0}, bolt.sHitPar);
    assertEquals("mag", bolt.EType);
    assertEquals(40, bolt.EMin);
    assertEquals(50, bolt.Emax);
    assertArrayEquals(new int[] {6, 10, 16, 32, 48}, bolt.MinELev);
    assertArrayEquals(new int[] {6, 10, 16, 32, 48}, bolt.MaxELev);
    assertEquals("skill('Holy Bolt'.blvl) * 15", bolt.EDmgSymPerCalc);
  }

  @Test
  void nativeHolyBoltUsesTheSharedSrvHit07HealingContract() {
    Skills.Entry holyBolt = Riiablo.files.skills.get(SkillId.HOLY_BOLT);
    NativeSkills.Entry nativeHolyBolt = Riiablo.files.NativeSkills.get("Holy Bolt");
    assertNotNull(holyBolt);
    assertNotNull(nativeHolyBolt);
    assertEquals("holybolt", holyBolt.srvmissile);
    assertEquals("ln12 * (100 + skill('Prayer'.blvl) * par7) / 100", holyBolt.calc1);
    assertEquals("ln34 * (100 + skill('Prayer'.blvl) * par7) / 100", holyBolt.calc2);
    assertTrue(nativeHolyBolt.bool("TargetPet"));
    assertTrue(nativeHolyBolt.bool("TargetAlly"));

    Missiles.Entry missile = Riiablo.files.Missiles.get(holyBolt.srvmissile);
    assertNotNull(missile);
    assertEquals(7, missile.pSrvHitFunc);
    assertTrue(missile.CollideFriend);
    assertTrue(missile.CollideKill);
    assertArrayEquals(new int[] {1, 1, 0}, missile.sHitPar);
    assertEquals("Holy Bolt", missile.Skill);
    assertEquals("healing", missile.ProgOverlay);
  }
}
