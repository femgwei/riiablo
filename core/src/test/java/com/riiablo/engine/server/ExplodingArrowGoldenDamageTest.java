package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/**
 * Twenty-ninth DMG-04 level-by-level audit: one Exploding Arrow explosion
 * child applying one skill-owned fire packet to one target.
 *
 * <p>D2MOO keeps the travelling arrow's weapon packet on the parent. Its
 * {@code SrvHit04} callback creates {@code explodingarrowexp2}; that child
 * uses the common {@code SrvHit01} explosion callback to fan the skill packet
 * out to hostile targets. D2MOO references
 * {@code MissMode.cpp:2673 MISSMODE_SrvHit04_ExplodingArrow_FreezingArrow_RoyalStrikeMeteorCenter},
 * {@code MissMode.cpp:2087 MISSMODE_SrvHit01_Fireball_ExplodingArrow_FreezingArrowExplosion},
 * {@code D2Skills.cpp SKILLS_GetMinElemDamage/SKILLS_GetMaxElemDamage}, and
 * {@code D2Common/Units/Missile.cpp MISSILE_CalculateDamageData}.</p>
 */
class ExplodingArrowGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIRE_MIN = {
      2, 7, 12, 17, 22, 27, 32, 37, 44, 51,
      58, 65, 72, 79, 86, 93, 102, 111, 120, 129
  };
  private static final int[] D2MOO_FIRE_MAX = {
      6, 11, 16, 21, 26, 31, 36, 41, 49, 57,
      65, 73, 81, 89, 97, 105, 116, 127, 138, 149
  };

  @Test
  void levelOneToTwentyMatchesD2mooExplosionFireCurveWithoutWeaponOrSynergy() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.EXPLODING_ARROW);
    Missiles.Entry root = Riiablo.files.Missiles.get("explodingarrow");
    Missiles.Entry explosion = Riiablo.files.Missiles.get("explodingarrowexp2");
    assertNotNull(skill);
    assertNotNull(root);
    assertNotNull(explosion);
    assertEquals("Exploding Arrow", skill.skill);
    assertEquals(16, skill.Id);
    assertEquals(4, skill.srvstfunc);
    assertEquals(0, skill.srvdofunc);
    assertEquals("explodingarrow", skill.srvmissile);
    assertEquals(128, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals("fire", skill.EType);
    assertEquals(2, skill.EMin);
    assertEquals(6, skill.EMax);
    assertArrayEquals(new int[] {5, 7, 9, 12, 20}, skill.EMinLev);
    assertArrayEquals(new int[] {5, 8, 11, 14, 23}, skill.EMaxLev);
    assertEquals("(skill('Fire Arrow'.blvl)) * par8", skill.EDmgSymPerCalc);
    assertEquals(4, root.pSrvHitFunc);
    assertEquals("explodingarrowexp2", root.HitSubMissile[0]);
    assertEquals(1, explosion.pSrvHitFunc);
    assertEquals(0, explosion.Explosion);

    Attributes owner = Attributes.obtainStandard();
    Missile parent = new Missile();
    parent.missile = root;
    Attributes weaponOwner = Attributes.obtainStandard();
    weaponOwner.base().put(Stat.mindamage, 10);
    weaponOwner.base().put(Stat.maxdamage, 10);
    weaponOwner.reset();
    assertTrue(MissileDamageResolver.initializeSkill(parent, skill, weaponOwner, 1, name -> 0));
    assertNull(parent.damage.get(Stat.firemaxdam),
        "the travelling arrow owns the weapon hit; the fire packet belongs to its child");

    for (int level = 1; level <= 20; level++) {
      Missile child = new Missile();
      child.missile = explosion;
      assertTrue(MissileDamageResolver.initializeSkillArea(child, skill, owner, level));
      assertEquals(D2MOO_FIRE_MIN[level - 1], statInt(child, Stat.firemindam),
          "fire min level " + level);
      assertEquals(D2MOO_FIRE_MAX[level - 1], statInt(child, Stat.firemaxdam),
          "fire max level " + level);
      assertEquals(D2MOO_FIRE_MIN[level - 1],
          MissileDamageResolver.skillElementalDamage(skill, level, true, name -> 0),
          "displayed min level " + level);
      assertEquals(D2MOO_FIRE_MAX[level - 1],
          MissileDamageResolver.skillElementalDamage(skill, level, false, name -> 0),
          "displayed max level " + level);
      assertEquals(0, statInt(child, Stat.maxdamage),
          "explosion child has no independent physical packet level " + level);
    }
  }

  private static int statInt(Missile missile, short stat) {
    StatRef ref = missile.damage.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }
}
