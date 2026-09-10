package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SorceressSkills;
import org.junit.jupiter.api.Test;

/** Executable 1.10f data contract for Blaze and Fire Wall. */
class NativeSorceressFireAreaDataTest extends RiiabloTest {
  @Test
  void blazeUsesTimedStateAndFractionalGroundMissile() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.BLAZE);
    assertNotNull(skill);
    assertEquals("Blaze", skill.skill);
    assertEquals(23, skill.srvdofunc);
    assertEquals("blaze", skill.aurastate);
    assertEquals("dm12", skill.auralencalc);
    assertEquals(120, SorceressSkills.getBlazeDuration(skill, 1, name -> 0));
    assertEquals(430, SorceressSkills.getBlazeDuration(skill, 20, name -> 0));
    assertEquals(4, skill.HitShift);
    assertEquals(4, skill.EMin);
    assertEquals(8, skill.EMax);

    Missiles.Entry row = Riiablo.files.Missiles.get(skill.srvmissilea);
    assertNotNull(row);
    assertEquals("blaze", row.Missile);
    assertEquals(5, row.pSrvDoFunc);
    assertEquals(8, row.pSrvHitFunc);
    assertEquals(3, row.pSrvDmgFunc);
    assertEquals(90, row.Range);
    assertEquals(25, row.LevRange);
    assertEquals(0, row.Vel);
    assertEquals(19, row.dParam[0]);
  }

  @Test
  void fireWallUsesTwoMakersAndOnePersistentCentreSegment() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.FIRE_WALL);
    assertNotNull(skill);
    assertEquals("Fire Wall", skill.skill);
    assertEquals(24, skill.srvdofunc);
    assertEquals("firewallmaker", skill.srvmissilea);
    assertEquals("firewall", skill.srvmissileb);
    assertEquals(4, skill.HitShift);
    assertEquals(15, skill.EMin);
    assertEquals(20, skill.EMax);

    Missiles.Entry maker = Riiablo.files.Missiles.get(skill.srvmissilea);
    assertNotNull(maker);
    assertEquals(6, maker.pSrvDoFunc);
    assertEquals("firewall", maker.SubMissile[0]);
    assertEquals(7, maker.Range);
    assertEquals(2, maker.LevRange);
    assertEquals(12, maker.Vel);
    assertFalse(maker.Collision);
    assertEquals(8, maker.CollideType);

    Missiles.Entry fire = Riiablo.files.Missiles.get(skill.srvmissileb);
    assertNotNull(fire);
    assertEquals(5, fire.pSrvDoFunc);
    assertEquals(3, fire.pSrvDmgFunc);
    assertEquals(90, fire.Range);
    assertEquals(41, fire.DamageRate);
    assertTrue(fire.Collision);
    assertFalse(fire.CollideKill);
  }

  @Test
  void fireAreaSnapshotRetainsEightEightDamageMasteryAndPierce() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.FIRE_WALL);
    Missiles.Entry row = Riiablo.files.Missiles.get(skill.srvmissileb);
    Missile missile = new Missile();
    missile.missile = row;
    Attributes owner = Attributes.obtainStandard();
    owner.base().put(Stat.passive_fire_mastery, 20);
    owner.base().put(Stat.item_pierce_fire, 7);
    owner.base().put(Stat.passive_fire_pierce, 3);
    owner.reset();

    assertTrue(MissileDamageResolver.initializeSorceressFireArea(
        missile, skill, owner, true, 1, name -> 0));
    // (15..20) << HitShift(4), then 20% Fire Mastery.
    assertEquals(288, missile.elementalMinRateFixed);
    assertEquals(384, missile.elementalMaxRateFixed);
    assertEquals(10, missile.elementalPiercePercent);
    assertEquals(41, missile.elementalDamageRate);
    assertTrue(missile.fixedElementalRate);
  }
}
