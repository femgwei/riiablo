package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillFormula;
import org.junit.jupiter.api.Test;

/** DMG-04 level-by-level audit: one Strafe arrow owns one enhanced weapon record. */
class StrafeGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_DAMAGE_PERCENT = {
      5, 10, 15, 20, 25, 30, 35, 40, 45, 50,
      55, 60, 65, 70, 75, 80, 85, 90, 95, 100
  };

  @Test
  void levelOneToTwentyUseNativePerArrowWeaponPercent() {
    Skills.Entry skill = Riiablo.files.skills.get("Strafe");
    Missiles.Entry arrow = Riiablo.files.Missiles.get("strafearrow");
    Missiles.Entry bolt = Riiablo.files.Missiles.get("strafebolt");
    assertNotNull(skill);
    assertNotNull(arrow);
    assertNotNull(bolt);
    assertEquals(26, skill.Id);
    assertEquals(8, skill.srvstfunc);
    assertEquals(12, skill.srvdofunc);
    assertEquals(96, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals("\"min(par3 + lvl - 1, par4)\"", skill.calc1);
    assertEquals("ln12", skill.calc2);
    assertEquals("2+lvl/4", skill.calc3);
    assertArrayEquals(new int[] {5, 5, 4, 10, 35, 50, 0, 0}, skill.Param);
    assertTrue(blank(skill.srvmissile));
    assertEquals("strafearrow", skill.srvmissilea);
    assertEquals("strafebolt", skill.srvmissileb);
    assertEquals(1, arrow.pSrvDoFunc);
    assertEquals(96, arrow.SrcDamage);
    assertEquals(8, arrow.HitShift);
    assertTrue(arrow.Pierce);
    assertTrue(arrow.LastCollide);
    assertEquals(1, bolt.pSrvDoFunc);
    assertEquals(96, bolt.SrcDamage);

    Attributes owner = Attributes.obtainStandard();
    owner.base().clear();
    owner.base().put(Stat.mindamage, 10);
    owner.base().put(Stat.maxdamage, 20);
    owner.base().put(Stat.tohit, 100);
    owner.base().put(Stat.level, 1);
    owner.reset();
    for (int level = 1; level <= 20; level++) {
      int expected = D2MOO_DAMAGE_PERCENT[level - 1];
      assertEquals(expected, SkillFormula.evaluate(skill.calc2, skill, level),
          "native Calc2 level " + level);

      Missile projectile = new Missile().set(arrow, new Vector2(), 40).setOwner(1);
      assertTrue(MissileDamageResolver.initializeSkill(projectile, skill, owner, level),
          "one Strafe arrow must own a weapon snapshot at level " + level);
      assertEquals(7, statInt(projectile, Stat.mindamage), "SrcDam min level " + level);
      assertEquals(15, statInt(projectile, Stat.maxdamage), "SrcDam max level " + level);
      assertEquals(expected, statInt(projectile, Stat.damagepercent),
          "per-arrow Calc2 damage percent level " + level);
      assertTrue(projectile.usesAttackRating);
    }
  }

  private static int statInt(Missile projectile, short stat) {
    StatRef ref = projectile.damage.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
