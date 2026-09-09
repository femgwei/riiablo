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

/** Native 1.10f data contracts for Bone Spear and Bone Spirit projectiles. */
class NativeNecromancerBoneProjectileDataTest extends RiiabloTest {
  @Test
  void fieldsAndMagicDamagePacketsMatchNativeRows() {
    Skills.Entry spear = Riiablo.files.skills.get(SkillId.BONE_SPEAR);
    Skills.Entry spirit = Riiablo.files.skills.get(SkillId.BONE_SPIRIT);
    assertNotNull(spear);
    assertNotNull(spirit);
    assertEquals(0, spear.srvdofunc);
    assertEquals("bonespear", spear.srvmissile);
    assertEquals(16, spear.EMin);
    assertEquals(24, spear.EMax);
    assertEquals(8, spear.HitShift);
    assertEquals(7, spear.Param[7]);
    assertEquals(10, spirit.srvdofunc);
    assertEquals("bonespirit", spirit.srvmissilea);
    assertEquals(20, spirit.EMin);
    assertEquals(30, spirit.EMax);
    assertEquals(8, spirit.HitShift);
    assertEquals(6, spirit.Param[7]);
    assertTrue(NecromancerSkills.isBoneSpear(spear));
    assertTrue(NecromancerSkills.isBoneSpirit(spirit));
    assertEquals(16, NecromancerSkills.getBoneProjectileMagicDamage(spear, 1, name -> 0)[0]);
    assertEquals(24, NecromancerSkills.getBoneProjectileMagicDamage(spear, 1, name -> 0)[1]);
    assertEquals(20, NecromancerSkills.getBoneProjectileMagicDamage(spirit, 1, name -> 0)[0]);
    assertEquals(30, NecromancerSkills.getBoneProjectileMagicDamage(spirit, 1, name -> 0)[1]);
    assertEquals(17, NecromancerSkills.getBoneProjectileMagicDamage(spear, 1,
        name -> "Bone Wall".equals(name) ? 1 : 0)[0]);

    Missiles.Entry spearMissile = Riiablo.files.Missiles.get(spear.srvmissile);
    Missiles.Entry spiritMissile = Riiablo.files.Missiles.get(spirit.srvmissilea);
    assertNotNull(spearMissile);
    assertNotNull(spiritMissile);
    assertEquals(24, spearMissile.Vel);
    assertEquals(40, spearMissile.Range);
    assertTrue(spearMissile.LastCollide);
    assertTrue(!spearMissile.CollideKill);
    assertEquals(12, spiritMissile.Vel);
    assertEquals(128, spiritMissile.Range);
    assertEquals(10, spiritMissile.pSrvHitFunc);
  }
}
